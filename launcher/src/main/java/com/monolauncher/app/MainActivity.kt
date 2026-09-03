package com.monolauncher.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.monolauncher.app.data.InstalledAppsRepository
import com.monolauncher.app.data.ThemePrefs
import com.monolauncher.app.ui.AppListScreen
import com.monolauncher.app.ui.HomeScreen
import com.monolauncher.app.ui.theme.MonoLauncherTheme

private enum class Screen { HOME, APPS }

/**
 * 홈(런처) 액티비티. 안드로이드가 홈 버튼을 누를 때마다 새 인스턴스를 만들지 않고
 * 이 액티비티의 onNewIntent를 호출하므로, 그때마다 화면을 HOME으로 되돌린다.
 */
class MainActivity : ComponentActivity() {

    private val screenState = mutableStateOf(Screen.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var screen by screenState
            var themeMode by remember { mutableStateOf(ThemePrefs.get(this)) }

            MonoLauncherTheme(mode = themeMode) {
                BackHandler(enabled = screen == Screen.APPS) {
                    screen = Screen.HOME
                }

                when (screen) {
                    Screen.HOME -> HomeScreen(onOpenMenu = { screen = Screen.APPS })
                    Screen.APPS -> {
                        val apps = remember(screen) {
                            InstalledAppsRepository.getLaunchableApps(this@MainActivity)
                        }
                        AppListScreen(
                            apps = apps,
                            themeMode = themeMode,
                            onAppClick = { app ->
                                InstalledAppsRepository.launch(this@MainActivity, app.packageName)
                            },
                            onToggleTheme = {
                                val next = ThemePrefs.next(themeMode)
                                ThemePrefs.set(this@MainActivity, next)
                                themeMode = next
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenState.value = Screen.HOME
    }
}
