package com.chatbridge.kakao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chatbridge.kakao.ui.screens.ChatDetailScreen
import com.chatbridge.kakao.ui.screens.ChatListScreen
import com.chatbridge.kakao.ui.screens.MonitoredRoomsScreen
import com.chatbridge.kakao.ui.theme.ChatBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatBridgeTheme {
                ChatBridgeRoot()
            }
        }
    }
}

private data class BottomTab(val route: String, val label: String, val enabled: Boolean)

// Menu 1 (KakaoTalk) is the only implemented feature for now; menus 2-4 are reserved slots for
// future integrations the user will request one at a time.
private val bottomTabs = listOf(
    BottomTab("kakao_chats", "카카오톡", enabled = true),
    BottomTab("menu2", "메뉴 2", enabled = false),
    BottomTab("menu3", "메뉴 3", enabled = false),
    BottomTab("menu4", "메뉴 4", enabled = false),
)

@Composable
private fun ChatBridgeRoot() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route
            NavigationBar {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        enabled = tab.enabled,
                        onClick = { navController.navigate(tab.route) { launchSingleTop = true } },
                        icon = {
                            Icon(
                                if (tab.enabled) Icons.Filled.Chat else Icons.Filled.HourglassEmpty,
                                contentDescription = tab.label
                            )
                        },
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
            composable("menu2") { PlaceholderScreen("메뉴 2") }
            composable("menu3") { PlaceholderScreen("메뉴 3") }
            composable("menu4") { PlaceholderScreen("메뉴 4") }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name - 준비중")
    }
}
