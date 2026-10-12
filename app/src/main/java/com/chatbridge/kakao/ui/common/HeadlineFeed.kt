package com.chatbridge.kakao.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow

/**
 * A title-only headline shared by menus 3 (news) and 4 (SNS): a scrollable list of compact cards,
 * tap to open the linked page. [subtitle] carries source/date (news) or handle/platform (SNS).
 */
data class HeadlineCard(val id: String, val title: String, val subtitle: String, val link: String)

@Composable
fun HeadlineFeedList(
    items: List<HeadlineCard>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "표시할 항목이 없습니다"
) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.clickable { openLink(context, item.link) }
            )
            Divider()
        }
    }
}

fun openLink(context: Context, link: String) {
    if (link.isBlank()) return
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(link))
    } catch (e: Exception) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
    }
}
