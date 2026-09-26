package com.rkd.launcher

import android.app.Application
import com.rkd.launcher.data.AppRepository
import com.rkd.launcher.data.AppState
import com.rkd.launcher.data.LimitManager
import com.rkd.launcher.data.SettingsStore
import com.rkd.launcher.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled service locator. The launcher activities and the accessibility service share one
 * process, so these singletons are the shared state between them.
 */
object Graph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore by lazy { SettingsStore(app) }
    val apps: AppRepository by lazy { AppRepository(app, settings, scope) }
    val usage: UsageRepository by lazy { UsageRepository(app) { apps } }
    val limits: LimitManager by lazy { LimitManager(app, settings, apps) }
    val state: AppState by lazy { AppState(app) }

    fun init(application: Application) {
        app = application
    }
}
