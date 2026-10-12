package com.chatbridge.kakao.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.scrape.ScrapeTarget
import com.chatbridge.kakao.ui.common.HeadlineCard
import com.chatbridge.kakao.ui.common.HeadlineFeedList
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun platformLabel(platform: ScrapeTarget) = when (platform) {
    ScrapeTarget.SNS_X -> "X"
    ScrapeTarget.SNS_INSTAGRAM -> "Instagram"
    ScrapeTarget.SNS_TIKTOK -> "TikTok"
    ScrapeTarget.BANKSALAD -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnsScreen() {
    val context = LocalContext.current
    val repo = (context.applicationContext as ChatBridgeApp).snsRepository
    val accounts by repo.observeAccounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val posts by repo.observeFeed().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    var handle by remember { mutableStateOf("") }
    var platformMenuOpen by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf(ScrapeTarget.SNS_X) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SNS") },
                actions = {
                    IconButton(onClick = {
                        scope.launch { accounts.forEach { repo.requestRefresh(it) } }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "전체 새로고침")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(
                        onClick = { platformMenuOpen = true },
                        label = { Text(platformLabel(selectedPlatform)) }
                    )
                    DropdownMenu(expanded = platformMenuOpen, onDismissRequest = { platformMenuOpen = false }) {
                        listOf(ScrapeTarget.SNS_X, ScrapeTarget.SNS_INSTAGRAM, ScrapeTarget.SNS_TIKTOK).forEach { p ->
                            DropdownMenuItem(text = { Text(platformLabel(p)) }, onClick = {
                                selectedPlatform = p
                                platformMenuOpen = false
                            })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("계정 핸들 (예: @handle)") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    val h = handle.trim()
                    if (h.isNotEmpty()) {
                        scope.launch { repo.addAccount(selectedPlatform, h) }
                        handle = ""
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "계정 추가")
                }
            }

            if (accounts.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.take(6).forEach { account ->
                        AssistChip(
                            onClick = { scope.launch { repo.removeAccount(account.platform, account.handle) } },
                            label = { Text("${platformLabel(account.platform)} @${account.handle}") },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "삭제",
                                    modifier = Modifier.width(16.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Divider()

            HeadlineFeedList(
                items = posts.map { post ->
                    HeadlineCard(
                        id = post.id.toString(),
                        title = post.contentPreview,
                        subtitle = "${platformLabel(post.platform)} · @${post.handle} · ${timeFormat.format(Date(post.scrapedAt))}",
                        link = post.link
                    )
                },
                emptyMessage = "계정을 추가하고 새로고침하면 게시글이 여기에 표시됩니다.\n" +
                    "(개별 게시물 링크가 아닌 계정 프로필로 연결됩니다)"
            )
        }
    }
}
