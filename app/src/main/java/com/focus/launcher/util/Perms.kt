package com.focus.launcher.util

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import com.focus.launcher.Graph

/** Checks for, and deep links to, the handful of system switches the launcher depends on. */
object Perms {
    fun isDefaultLauncher(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun hasUsageAccess(): Boolean = Graph.usage.hasAccess()

    /**
     * True when the user let Focus display over other apps. That switch is what allows the "time's
     * up" screen to come up in front of an app that is open; without it Focus sends a notification.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun canPostNotifications(context: Context): Boolean {
        val enabled = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return enabled
        return enabled && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    // ---- deep links --------------------------------------------------------------------------

    fun openUsageAccess(context: Context) = start(
        context,
        // Some phones jump straight to this app's switch when given the package, others reject it.
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    fun openOverlaySettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    )

    fun openHomeSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openNotificationSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context),
    )

    fun openAppDetails(context: Context) = start(context, appDetails(context))

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /** Tries each intent in order until one can be started. [options] carries the launch animation. */
    fun start(context: Context, vararg candidates: Intent, options: Bundle? = null): Boolean {
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
