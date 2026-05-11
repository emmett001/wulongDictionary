package com.wulong.dict.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.wulong.dict.AppContainer
import com.wulong.dict.data.local.SqliteDictEngine
import com.wulong.dict.domain.model.Language
import com.wulong.dict.ui.theme.WulongColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appContainer: AppContainer,
    onNavigateBack: () -> Unit,
    onLanguageChanged: (String) -> Unit = {},
) {
    val currentLang = remember {
        runBlocking { appContainer.languageSettings.languageCode.first() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var dictRefreshKey by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<SqliteDictEngine.DictConfig?>(null) }

    // Force recompose when engine configs change
    val dictConfigs = remember(dictRefreshKey, appContainer.dictEngine.configs.size) {
        appContainer.dictEngine.configs
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            showConfirmDialog = true
        }
    }

    // ── Confirmation dialog ─────────────────────────────────────────
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                pendingUri = null
            },
            title = { Text("导入词典文件？") },
            text = {
                Text("将从所选文件夹中复制所有词典文件（数据库、样式、图片等）到应用目录。\n\n这可能需要几秒钟。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    showLangDialog = true
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    pendingUri = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // ── Language selection dialog ────────────────────────────────────
    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = {
                showLangDialog = false
                pendingUri = null
            },
            title = { Text("选择词典语言") },
            text = {
                Text("这些词典将导入到哪种语言的数据目录下？")
            },
            confirmButton = {
                Row {
                    Language.entries.forEach { lang ->
                        TextButton(onClick = {
                            showLangDialog = false
                            val uri = pendingUri ?: return@TextButton
                            pendingUri = null
                            scope.launch {
                                statusText = "正在导入..."
                                val result = withContext(Dispatchers.IO) {
                                    importFromFolder(context, uri, lang.code, appContainer)
                                }
                                statusText = result
                            }
                        }) {
                            Text(lang.displayName)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLangDialog = false
                    pendingUri = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // ── Delete confirmation dialog ──────────────────────────────────
    val configToDelete = deleteTarget
    if (configToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除词典") },
            text = {
                Text("确定要删除「${configToDelete.fullName}」吗？\n\n这将永久删除词典数据，无法恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val config = configToDelete
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            appContainer.deleteDictionary(config)
                        }
                        dictRefreshKey++
                        Toast.makeText(context, "已删除「${config.shortName}」", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        containerColor = WulongColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("设置", color = WulongColors.BodyText) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = WulongColors.BodyText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WulongColors.Background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            // ── Dictionary management ───────────────────────────────
            Text(
                text = "词典管理",
                style = MaterialTheme.typography.titleSmall,
                color = WulongColors.BodyText
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (dictConfigs.isEmpty()) {
                Text(
                    text = "暂无词典，请先导入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WulongColors.Placeholder
                )
            } else {
                dictConfigs.forEachIndexed { index, config ->
                    val dirName = appContainer.dictEngine.resolveDbFile(config).parentFile?.name ?: config.shortName
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = WulongColors.SearchFill
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reorder buttons
                            Column {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val reordered = dictConfigs.map {
                                                appContainer.dictEngine.resolveDbFile(it).parentFile?.name ?: ""
                                            }.toMutableList()
                                            val moved = reordered.removeAt(index)
                                            reordered.add(index - 1, moved)
                                            appContainer.reorderDictionaries(reordered)
                                            dictRefreshKey++
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "上移",
                                        tint = if (index > 0) WulongColors.BodyText else WulongColors.Placeholder,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index < dictConfigs.size - 1) {
                                            val reordered = dictConfigs.map {
                                                appContainer.dictEngine.resolveDbFile(it).parentFile?.name ?: ""
                                            }.toMutableList()
                                            val moved = reordered.removeAt(index)
                                            reordered.add(index + 1, moved)
                                            appContainer.reorderDictionaries(reordered)
                                            dictRefreshKey++
                                        }
                                    },
                                    enabled = index < dictConfigs.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "下移",
                                        tint = if (index < dictConfigs.size - 1) WulongColors.BodyText else WulongColors.Placeholder,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Dict info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = config.shortName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = WulongColors.BodyText
                                )
                                Text(
                                    text = config.fullName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WulongColors.Placeholder,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = { deleteTarget = config }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除词典",
                                    tint = WulongColors.Placeholder,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Language selection ──────────────────────────────────
            Text(
                text = "界面语言",
                style = MaterialTheme.typography.titleSmall,
                color = WulongColors.BodyText
            )

            Spacer(modifier = Modifier.height(8.dp))

            Language.entries.forEach { lang ->
                val selected = currentLang == lang.code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!selected) {
                                scope.launch {
                                    appContainer.languageSettings.setLanguage(lang.code)
                                    onLanguageChanged(lang.code)
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                scope.launch {
                                    appContainer.languageSettings.setLanguage(lang.code)
                                    onLanguageChanged(lang.code)
                                }
                            }
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lang.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) WulongColors.BodyText else WulongColors.Placeholder
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Import button ──────────────────────────────────────
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("导入词典文件…", fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "请先下载词典压缩包并解压到手机，\n然后选择包含 oaldpe/ 等子文件夹的目录。",
                style = MaterialTheme.typography.bodySmall,
                color = WulongColors.Placeholder,
                lineHeight = 20.sp
            )

            // ── Status ─────────────────────────────────────────────
            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WulongColors.BodyText
                )
            }
        }
    }
}

private fun importFromFolder(
    context: android.content.Context,
    folderUri: android.net.Uri,
    langCode: String,
    appContainer: AppContainer
): String {
    val srcRoot = DocumentFile.fromTreeUri(context, folderUri)
        ?: return "无法读取所选文件夹"

    val targetRoot = File(context.getExternalFilesDir(null), "dicts/$langCode")

    // Take persistent permission so we can read the folder
    context.contentResolver.takePersistableUriPermission(
        folderUri,
        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    )

    var copied = 0
    val errors = mutableListOf<String>()

    // Walk the selected folder looking for .sqlite3 files
    srcRoot.listFiles().forEach { child ->
        if (child.isDirectory) {
            try {
                copied += copyDir(context, child, targetRoot)
            } catch (e: Exception) {
                errors.add("${child.name}: ${e.message}")
            }
        }
    }

    // Reinitialize engine only if we imported to the current language
    if (langCode == appContainer.language.code) {
        try {
            appContainer.reloadEngine()
            // Append newly imported directories to the saved order
            val existing = kotlinx.coroutines.runBlocking {
                appContainer.dictionaryOrderSettings.dictOrder.first()
            }
            val newDirs = srcRoot.listFiles()
                .filter { it.isDirectory }
                .map { it.name ?: "" }
                .filter { it.isNotEmpty() && it !in existing }
            if (newDirs.isNotEmpty()) {
                kotlinx.coroutines.runBlocking {
                    appContainer.dictionaryOrderSettings.setOrder(existing + newDirs)
                }
            }
        } catch (e: Exception) {
            errors.add("重新加载引擎失败: ${e.message}")
        }
    }

    if (errors.isNotEmpty()) {
        return "导入完成: $copied 个文件\n错误: ${errors.joinToString(", ")}"
    }
    return if (copied > 0) "导入完成: $copied 个文件" else "未找到任何文件，请确认已选择正确的解压目录。"
}

/** Recursively copy ALL files from a SAF directory into [targetRoot].
 * SQLite databases, CSS, JS, images, and fonts are all required for
 * WebView rendering — filtering by extension breaks layout. */
private fun copyDir(
    context: android.content.Context,
    src: DocumentFile,
    targetRoot: File
): Int {
    var count = 0
    val subDir = File(targetRoot, src.name ?: return 0)
    subDir.mkdirs()

    src.listFiles().forEach { child ->
        if (child.isDirectory) {
            count += copyDir(context, child, targetRoot)
        } else {
            val destFile = File(subDir, child.name ?: return@forEach)
            context.contentResolver.openInputStream(child.uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            count++
        }
    }
    return count
}
