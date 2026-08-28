package com.chatbridge.kakao.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.MonitoredChat
import com.chatbridge.kakao.util.PermissionUtils
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onManageRooms: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as ChatBridgeApp).repository
    val chats by repo.observeMonitoredChats().collectAsStateWithLifecycle(initialValue = emptyList())

    var notificationOk by remember { mutableStateOf(PermissionUtils.isNotificationListenerEnabled(context)) }
    var accessibilityOk by remember { mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context)) }

    LaunchedEffect(Unit) {
        notificationOk = PermissionUtils.isNotificationListenerEnabled(context)
        accessibilityOk = PermissionUtils.isAccessibilityServiceEnabled(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카카오톡") },
                actions = {
                    IconButton(onClick = onManageRooms) {
                        Icon(Icons.Filled.Settings, contentDescription = "채팅방 선택")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!notificationOk || !accessibilityOk) {
                PermissionBanner(notificationOk, accessibilityOk)
            }
            if (chats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("요청한 채팅방이 없습니다. 우측 상단 설정에서 채팅방을 선택하세요.")
                }
            } else {
                LazyColumn {
                    items(chats, key = { it.chatName }) { chat ->
                        ChatRow(chat, onClick = { onOpenChat(URLEncoder.encode(chat.chatName, "UTF-8")) })
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(notificationOk: Boolean, accessibilityOk: Boolean) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("설정이 필요합니다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!notificationOk) {
                Text("• 카카오톡 메시지를 읽으려면 '알림 접근 권한'을 켜주세요.")
                TextButton(onClick = {
                    context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("알림 접근 권한 설정으로 이동") }
            }
            if (!accessibilityOk) {
                Text("• 답장을 보내려면 '손쉬운 사용(접근성)' 권한을 켜주세요.")
                TextButton(onClick = {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("접근성 설정으로 이동") }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: MonitoredChat, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(chat.chatName) },
        supportingContent = { Text(chat.lastMessagePreview.ifBlank { "메시지 없음" }) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
