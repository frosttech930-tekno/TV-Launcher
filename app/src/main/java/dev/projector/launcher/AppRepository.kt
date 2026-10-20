package dev.projector.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(
    val component: ComponentName,
    val category: String,
    val label: String,
    val icon: Drawable,
    val banner: Drawable?,
) {
    val packageName: String get() = component.packageName

    /** Apps are keyed by package so favorites survive an app renaming its main activity. */
    val key: String get() = packageName
}

object AppRepository {

    /**
     * Loads every launchable app. TV (leanback) entries win over phone-style entries
     * for the same package because they come with a proper 16:9 banner.
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val byPackage = LinkedHashMap<String, AppEntry>()

        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo
                if (activity.packageName == context.packageName) continue
                if (byPackage.containsKey(activity.packageName)) continue

                val banner = if (category == Intent.CATEGORY_LEANBACK_LAUNCHER) {
                    activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
                } else {
                    null
                }
                byPackage[activity.packageName] = AppEntry(
                    component = ComponentName(activity.packageName, activity.name),
                    category = category,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm),
                    banner = banner,
                )
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }
}
