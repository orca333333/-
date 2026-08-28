package com.chatbridge.kakao.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.MonitoredChat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoredRoomsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as ChatBridgeApp).repository
    val chats by repo.observeAllChats().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("채팅방 선택") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        if (chats.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("아직 감지된 카카오톡 채팅방이 없습니다.\n카카오톡 알림이 오면 자동으로 목록에 추가됩니다.")
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(chats, key = { it.chatName }) { chat ->
                    RoomToggleRow(chat) { monitored ->
                        scope.launch { repo.setMonitored(chat.chatName, monitored) }
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun RoomToggleRow(chat: MonitoredChat, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(chat.chatName) },
        supportingContent = { Text(if (chat.isMonitored) "요청됨" else "요청 안 함") },
        trailingContent = {
            Switch(checked = chat.isMonitored, onCheckedChange = onToggle)
        }
    )
}
