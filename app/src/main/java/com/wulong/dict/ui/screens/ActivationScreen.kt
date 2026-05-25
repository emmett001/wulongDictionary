package com.wulong.dict.ui.screens

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wulong.dict.domain.model.Language
import com.wulong.dict.ui.theme.WulongColors
import com.wulong.dict.ui.theme.WulongFonts
import kotlinx.coroutines.launch

@Composable
fun ActivationScreen(
    language: Language,
    serverUrl: String,
    onActivated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var inviteNo by remember { mutableStateOf("") }
    var code      by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    val slogan = when (language) {
        Language.EN -> "Words build worlds."
        Language.JA -> "辞書は、もっと軽くていい。"
        Language.DE -> "Sprache ist der Schlüssel zur Welt."
        Language.KO -> "한 단어의 깊이"
    }

    val sloganFont = when (language) {
        Language.EN, Language.DE -> WulongFonts.PlayfairDisplay
        Language.JA -> WulongFonts.NotoSerifJP
        Language.KO -> androidx.compose.ui.text.font.FontFamily.Default
    }

    fun doActivate() {
        val no   = inviteNo.trim()
        val c    = code.trim()
        if (no.isEmpty() || c.isEmpty()) {
            errorMsg = "请填写所有字段"
            return
        }
        isChecking = true
        errorMsg = null
        scope.launch {
            val repo = com.wulong.dict.data.repository.ActivationRepository(serverUrl)
            val result = repo.activate(no, c, deviceId)
            isChecking = false
            if (result.ok) {
                onActivated(no)
            } else {
                errorMsg = result.msg
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WulongColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.25f))

            Text(
                text = "乌龙词典",
                fontFamily = WulongFonts.PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = WulongColors.BodyText,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = slogan,
                fontFamily = sloganFont,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = WulongColors.Placeholder,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.15f))

            // ── Activation form ────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WulongColors.SearchFill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "首次使用需要激活",
                        style = MaterialTheme.typography.titleSmall,
                        color = WulongColors.BodyText,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "请输入邀请函上的编号和激活码",
                        style = MaterialTheme.typography.bodySmall,
                        color = WulongColors.Placeholder
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inviteNo,
                        onValueChange = { inviteNo = it },
                        label = { Text("邀请编号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("激活码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doActivate() })
                    )

                    if (errorMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { doActivate() },
                        enabled = !isChecking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isChecking) "验证中…" else "激活")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.45f))
        }
    }
}
