package com.chatbridge.kakao

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chatbridge.kakao.data.scrape.ScrapeTarget
import com.chatbridge.kakao.data.sns.SnsRepository
import com.chatbridge.kakao.ui.screens.BankSaladScreen
import com.chatbridge.kakao.ui.screens.ChatDetailScreen
import com.chatbridge.kakao.ui.screens.ChatListScreen
import com.chatbridge.kakao.ui.screens.MonitoredRoomsScreen
import com.chatbridge.kakao.ui.screens.NewsFeedScreen
import com.chatbridge.kakao.ui.screens.SettingsScreen
import com.chatbridge.kakao.ui.screens.SnsScreen
import com.chatbridge.kakao.ui.screens.YoutubeScreen
import com.chatbridge.kakao.ui.theme.ChatBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatBridgeTheme {
                ChatBridgeRoot(intent)
            }
        }
    }

    companion object {
        const val EXTRA_REFRESH_BANKSALAD = "refresh_banksalad"
        const val EXTRA_REFRESH_SNS = "refresh_sns"
    }
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab("kakao_chats", "카카오톡", Icons.Filled.Chat),
    BottomTab("youtube", "유튜브", Icons.Filled.SmartDisplay),
    BottomTab("news", "뉴스", Icons.Filled.Newspaper),
    BottomTab("sns", "SNS", Icons.Filled.Groups),
    BottomTab("banksalad", "가계부", Icons.Filled.AccountBalanceWallet),
)

@Composable
private fun ChatBridgeRoot(startIntent: Intent) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: banner/reminder notifications simply won't show if denied */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(startIntent) {
        val app = context.applicationContext as ChatBridgeApp
        if (startIntent.getBooleanExtra(MainActivity.EXTRA_REFRESH_BANKSALAD, false)) {
            app.financeRepository.requestRefresh()
            context.packageManager.getLaunchIntentForPackage(ScrapeTarget.BANKSALAD.packageName)
                ?.let { context.startActivity(it) }
        }
        if (startIntent.getBooleanExtra(MainActivity.EXTRA_REFRESH_SNS, false)) {
            val accounts = app.snsRepository.getAccountsOnce()
            accounts.forEach { app.snsRepository.requestRefresh(it) }
            accounts.firstOrNull()?.let { first ->
                val uri = Uri.parse(SnsRepository.profileUrl(first.platform, first.handle))
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage(first.platform.packageName)
                    }
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route
            NavigationBar {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navController.navigate(tab.route) { launchSingleTop = true } },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "kakao_chats",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("kakao_chats") {
                ChatListScreen(
                    onOpenChat = { chatName -> navController.navigate("chat/$chatName") },
                    onManageRooms = { navController.navigate("rooms") }
                )
            }
            composable("rooms") {
                MonitoredRoomsScreen(onBack = { navController.popBackStack() })
            }
            composable("chat/{chatName}") { backStackEntry ->
                val chatName = backStackEntry.arguments?.getString("chatName") ?: return@composable
                ChatDetailScreen(chatName = chatName, onBack = { navController.popBackStack() })
            }
            composable("youtube") {
                YoutubeScreen(onOpenSettings = { navController.navigate("settings") })
            }
            composable("news") { NewsFeedScreen(onOpenSettings = { navController.navigate("settings") }) }
            composable("sns") { SnsScreen() }
            composable("banksalad") { BankSaladScreen() }
            composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

