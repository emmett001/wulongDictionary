package com.wulong.dict.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wulong.dict.R
import com.wulong.dict.domain.model.Language
import com.wulong.dict.ui.theme.WulongColors
import com.wulong.dict.ui.theme.WulongFonts

@Composable
fun MainScreen(
    language: Language,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val (slogan, placeholder) = when (language) {
        Language.EN -> "Words build worlds." to "今天搜点什么好呢？"
        Language.JA -> "辞書は、もっと軽くていい。" to "何を調べますか？"
    }

    val sloganFont = when (language) {
        Language.EN -> WulongFonts.PlayfairDisplay
        Language.JA -> WulongFonts.NotoSerifJP
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WulongColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Settings gear (top-right) ──────────────────────────────
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "设置",
                tint = WulongColors.Placeholder,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Upper breathing space → pushes logo above midline ───
            Spacer(modifier = Modifier.weight(0.3f))
            Spacer(modifier = Modifier.height(120.dp))

            // ── Logo ─────────────────────────────────────────────────
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "乌龙词典",
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Slogan — literary, restrained ────────────────────────
            Text(
                text = slogan,
                fontFamily = sloganFont,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                color = WulongColors.Placeholder,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            // ── Thin gap → search bar top edge at screen midline ─────
            Spacer(modifier = Modifier.weight(0.02f))

            // ── Extra offset ─────────────────────────────────────────
            Spacer(modifier = Modifier.height(180.dp))

            // ── Cream pill search bar ────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(onClick = onNavigateToSearch),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 0.dp,
                color = WulongColors.SearchFill,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = WulongColors.Placeholder,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WulongColors.Placeholder,
                        fontSize = 15.sp
                    )
                }
            }

            // ── Bottom breathing space ───────────────────────────────
            Spacer(modifier = Modifier.weight(0.65f))
        }
    }
}
