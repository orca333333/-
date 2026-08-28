package com.chatbridge.kakao.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chatbridge.kakao.util.SecurePrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    var youtubeKey by remember { mutableStateOf(securePrefs.youtubeApiKey.orEmpty()) }
    var claudeKey by remember { mutableStateOf(securePrefs.claudeApiKey.orEmpty()) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("API 키", style = MaterialTheme.typography.titleMedium)
            Text(
                "이 앱은 서버 없이 동작하며, 입력한 키는 기기에 암호화되어서만 저장됩니다.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = youtubeKey,
                onValueChange = { youtubeKey = it; saved = false },
                label = { Text("YouTube Data API 키") },
                supportingText = { Text("메뉴 2(유튜브)에 필요. Google Cloud Console에서 무료 발급") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = claudeKey,
                onValueChange = { claudeKey = it; saved = false },
                label = { Text("Claude API 키") },
                supportingText = { Text("메뉴 3(뉴스 제목 요약), 메뉴 5(가계부 요약)에 필요. 비워두면 요약 없이 원문만 표시") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(onClick = {
                securePrefs.youtubeApiKey = youtubeKey.trim().ifBlank { null }
                securePrefs.claudeApiKey = claudeKey.trim().ifBlank { null }
                saved = true
            }) {
                Text("저장")
            }
            if (saved) {
                Spacer(Modifier.height(8.dp))
                Text("저장되었습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
