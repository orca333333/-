package com.chatbridge.kakao.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.youtube.YoutubeVideo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as ChatBridgeApp).youtubeRepository
    val channels by repo.observeChannels().collectAsStateWithLifecycle(initialValue = emptyList())
    val videos by repo.observeVideos().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("유튜브") },
                actions = {
                    IconButton(onClick = { scope.launch { repo.refreshAll() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정(API 키)")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!repo.hasApiKey()) {
                Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        "YouTube Data API 키가 설정되지 않았습니다. 설정 화면에서 입력해주세요.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("채널명/핸들 검색") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    val q = query.trim()
                    if (q.isNotEmpty()) {
                        scope.launch {
                            status = if (repo.addChannel(q)) null else "채널을 찾지 못했습니다"
                            query = ""
                        }
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "채널 추가")
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }

            if (channels.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.take(6).forEach { channel ->
                        AssistChip(
                            onClick = { scope.launch { repo.removeChannel(channel.channelId) } },
                            label = { Text(channel.title) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "구독 해제") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Divider()

            if (videos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("채널을 추가하면 최신 영상이 여기에 표시됩니다.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(videos, key = { it.videoId }) { video ->
                        VideoRow(video)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: YoutubeVideo) {
    val context = LocalContext.current
    val dateText = remember(video.publishedAt) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(video.publishedAt))
    }
    ListItem(
        leadingContent = {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 96.dp, height = 54.dp)
            )
        },
        headlineContent = { Text(video.title, maxLines = 2) },
        supportingContent = { Text("${video.channelTitle} · $dateText") },
        modifier = Modifier.clickable { openVideo(context, video.videoId) }
    )
}

private fun openVideo(context: android.content.Context, videoId: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
        setPackage("com.google.android.youtube")
    }
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
    try {
        context.startActivity(appIntent)
    } catch (e: Exception) {
        context.startActivity(webIntent)
    }
}
