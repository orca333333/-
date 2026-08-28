package com.chatbridge.kakao.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.finance.FinanceSnapshot
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSaladScreen() {
    val context = LocalContext.current
    val repo = (context.applicationContext as ChatBridgeApp).financeRepository
    val snapshots by repo.observeSnapshots().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("가계부 (뱅크샐러드)") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { scope.launch { repo.requestRefresh() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "지금 갱신")
            }
        }
    ) { padding ->
        if (snapshots.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "아직 기록이 없습니다. 우측 하단 버튼을 눌러 뱅크샐러드 가계부를 불러오세요.\n" +
                        "(뱅크샐러드 앱이 열리며 접근성 서비스가 화면을 읽습니다)",
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(snapshots, key = { it.id }) { snapshot ->
                    SnapshotCard(snapshot, dateFormat)
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(snapshot: FinanceSnapshot, dateFormat: SimpleDateFormat) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(dateFormat.format(Date(snapshot.capturedAt)), style = MaterialTheme.typography.labelMedium)
            if (snapshot.summary.isNotBlank()) {
                Text(snapshot.summary, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Claude API 키가 없어 자동 요약을 생략했습니다. 원문 스크랩:",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    snapshot.rawText.take(500),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
