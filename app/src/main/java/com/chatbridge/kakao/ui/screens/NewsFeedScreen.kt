package com.chatbridge.kakao.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatbridge.kakao.ChatBridgeApp
import com.chatbridge.kakao.data.news.NewsCategory
import com.chatbridge.kakao.ui.common.HeadlineCard
import com.chatbridge.kakao.ui.common.HeadlineFeedList
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as ChatBridgeApp).newsRepository
    val scope = rememberCoroutineScope()
    var selected by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    val categories = NewsCategory.entries
    val category = categories[selected]
    val articles by repo.observeByCategory(category).collectAsStateWithLifecycle(initialValue = emptyList())
    val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("뉴스 (당일 08:00 기준)") },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(onClick = {
                            refreshing = true
                            scope.launch {
                                repo.refreshDailyDigest()
                                refreshing = false
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정(API 키)")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selected) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = selected == index,
                        onClick = { selected = index },
                        text = { Text(cat.displayName) }
                    )
                }
            }
            HeadlineFeedList(
                items = articles.map { article ->
                    HeadlineCard(
                        id = article.link,
                        title = article.summaryTitle,
                        subtitle = "${article.source.ifBlank { "출처 미상" }} · ${timeFormat.format(Date(article.publishedAt))}",
                        link = article.link
                    )
                },
                emptyMessage = "표시할 뉴스가 없습니다. 새로고침을 눌러보세요."
            )
        }
    }
}
