package com.wulong.dict.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextField
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
    onRestartRequested: () -> Unit = {},
) {
    val currentLang = remember {
        runBlocking { appContainer.languageSettings.languageCode.first() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }
    var importDetail by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var dictRefreshKey by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<SqliteDictEngine.DictConfig?>(null) }
    var renameTarget by remember { mutableStateOf<SqliteDictEngine.DictConfig?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Force recompose when engine configs change
    val dictConfigs = remember(dictRefreshKey) {
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
                Text("将从所选文件夹中复制所有词典文件（数据库、样式、图片等）到「${appContainer.language.displayName}」词典目录。\n\n这可能需要几秒钟。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    val uri = pendingUri ?: return@TextButton
                    pendingUri = null
                    val langCode = appContainer.language.code
                    scope.launch {
                        statusText = ""
                        isImporting = true
                        importProgress = 0f
                        importDetail = "正在扫描文件…"
                        val result = withContext(Dispatchers.IO) {
                            importFromFolder(context, uri, langCode, appContainer) { done, total ->
                                launch(Dispatchers.Main) {
                                    importProgress = done.toFloat() / total
                                    importDetail = "正在复制 $done / $total"
                                }
                            }
                        }
                        isImporting = false
                        statusText = result
                        dictRefreshKey++
                        if (result.startsWith("导入完成")) {
                            showRestartDialog = true
                        }
                    }
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

    // ── Restart prompt dialog ──────────────────────────────────────
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("导入完成") },
            text = { Text("词典已导入成功。需要重启应用以使新词典生效。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    onRestartRequested()
                }) {
                    Text("立即重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("稍后")
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

    // ── Rename dialog ──────────────────────────────────────────────────
    val configToRename = renameTarget
    if (configToRename != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("修改词典名称") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotBlank()) {
                        appContainer.dictEngine.updateDictName(configToRename, newName)
                        dictRefreshKey++
                    }
                    renameTarget = null
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
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
                .verticalScroll(rememberScrollState())
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

                            // Rename button
                            IconButton(
                                onClick = {
                                    renameText = config.shortName
                                    renameTarget = config
                                }
                            ) {
                                Text(
                                    "✏",
                                    fontSize = 16.sp,
                                    color = WulongColors.Placeholder
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
            if (isImporting) {
                Spacer(modifier = Modifier.height(24.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "正在导入，请勿离开此页面…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WulongColors.Placeholder
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { importProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = WulongColors.SearchFill,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = importDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = WulongColors.Placeholder
                    )
                }
            } else if (statusText.isNotEmpty()) {
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
    appContainer: AppContainer,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): String {
    val srcRoot = DocumentFile.fromTreeUri(context, folderUri)
        ?: return "无法读取所选文件夹"

    val targetRoot = File(context.getExternalFilesDir(null), "dicts/$langCode")

    context.contentResolver.takePersistableUriPermission(
        folderUri,
        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    )

    // Count total files first so we can report progress
    var totalFiles = 0
    srcRoot.listFiles().forEach { child ->
        if (child.isDirectory) totalFiles += countFiles(child)
    }
    if (totalFiles == 0) return "未找到任何文件，请确认已选择正确的解压目录。"

    var copied = 0
    val errors = mutableListOf<String>()

    srcRoot.listFiles().forEach { child ->
        if (child.isDirectory) {
            // If the selected folder contains a wrapper dir named after a
            // language code (en/ja/de/ko), flatten it — import its
            // contents instead of the wrapper directory itself.
            val langCodes = setOf("en", "ja", "de", "ko", "ru")
            val children = if (child.name in langCodes && child.isDirectory) {
                child.listFiles().filter { it.isDirectory }.toList()
            } else {
                listOf(child)
            }
            for (c in children) {
                try {
                    copied += copyDir(context, c, targetRoot) { n ->
                        onProgress(copied + n, totalFiles)
                    }
                } catch (e: Exception) {
                    errors.add("${c.name}: ${e.message}")
                }
            }
        }
    }

    // Prevent media scanner from indexing dict resources into gallery
    File(context.getExternalFilesDir(null), "dicts/.nomedia").createNewFile()
    File(targetRoot, ".nomedia").createNewFile()

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

/** Recursively copy ALL files from a SAF directory into [targetRoot]. */
private fun copyDir(
    context: android.content.Context,
    src: DocumentFile,
    targetRoot: File,
    onFileCopied: (Int) -> Unit = {}
): Int {
    var count = 0
    val subDir = File(targetRoot, src.name ?: return 0)
    subDir.mkdirs()

    src.listFiles().forEach { child ->
        if (child.isDirectory) {
            count += copyDir(context, child, subDir) { n -> onFileCopied(count + n) }
        } else {
            val destFile = File(subDir, child.name ?: return@forEach)
            context.contentResolver.openInputStream(child.uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            count++
            onFileCopied(count)
        }
    }
    return count
}

/** Recursively count all files in a SAF directory (for progress tracking). */
private fun countFiles(dir: DocumentFile): Int {
    var count = 0
    dir.listFiles().forEach { child ->
        if (child.isDirectory) count += countFiles(child)
        else count++
    }
    return count
}
