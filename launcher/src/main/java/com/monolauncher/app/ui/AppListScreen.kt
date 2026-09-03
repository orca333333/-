package com.monolauncher.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.monolauncher.app.data.AppInfo
import com.monolauncher.app.data.ThemeMode

/**
 * 메뉴 화면: 설치된 앱들을 작은 아이콘 + 이름으로 세로로 쭉 나열한다.
 * 우측 상단의 작은 텍스트를 탭하면 시스템/라이트/다크 순으로 테마가 전환된다.
 */
@Composable
fun AppListScreen(
    apps: List<AppInfo>,
    themeMode: ThemeMode,
    onAppClick: (AppInfo) -> Unit,
    onToggleTheme: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = themeLabel(themeMode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable(onClick = onToggleTheme)
                    .padding(6.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppRow(app = app, onClick = { onAppClick(app) })
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = remember(app.packageName) {
            app.icon.toBitmap(width = 96, height = 96).asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "시스템"
    ThemeMode.LIGHT -> "라이트"
    ThemeMode.DARK -> "다크"
}
