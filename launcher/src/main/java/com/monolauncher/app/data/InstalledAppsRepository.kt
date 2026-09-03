package com.monolauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.Collator
import java.util.Locale

/** 설치된 앱(런처 아이콘이 있는 앱) 목록 조회 및 실행. */
object InstalledAppsRepository {

    fun getLaunchableApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val collator = Collator.getInstance(Locale.KOREA)
        return packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager),
                )
            }
            .sortedWith(compareBy(collator) { it.label })
            .toList()
    }

    fun launch(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}
