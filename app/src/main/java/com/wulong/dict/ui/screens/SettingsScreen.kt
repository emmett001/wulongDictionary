package com.wulong.dict.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.wulong.dict.AppContainer
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
            // ── Dictionary status ──────────────────────────────────
            Text(
                text = "词典数据",
                style = MaterialTheme.typography.titleSmall,
                color = WulongColors.BodyText
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "牛津 / 柯林斯 / 韦氏大学",
                style = MaterialTheme.typography.bodyMedium,
                color = WulongColors.Placeholder
            )

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
            appContainer.dictEngine.close()
            appContainer.dictEngine.open()
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
