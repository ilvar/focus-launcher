package com.focus.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.Settings
import com.focus.launcher.service.WeeklyReview
import com.focus.launcher.ui.drawer.AppMenu
import com.focus.launcher.ui.drawer.DrawerScreen
import com.focus.launcher.ui.home.HomeScreen
import com.focus.launcher.ui.launchApp
import com.focus.launcher.ui.theme.FocusTheme
import com.focus.launcher.ui.theme.applyFocusWindow
import com.focus.launcher.util.Perms
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** The home screen (page 0) and, one swipe to the left, the app drawer (page 1). */
class MainActivity : ComponentActivity() {
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.settings.value.let { applyFocusWindow(it.dark, it.hideStatusBar) }
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark, settings.hideStatusBar) { applyFocusWindow(settings.dark, settings.hideStatusBar) }
            FocusTheme(settings) { Launcher(settings, homePresses) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home button while the launcher is already showing: go back to page 0.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.tryEmit(Unit)
    }
}

private var lastBackfill = 0L

@Composable
private fun Launcher(settings: Settings, homePresses: Flow<Unit>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apps by Graph.apps.apps.collectAsStateWithLifecycle()
    val loaded by Graph.apps.loaded.collectAsStateWithLifecycle()
    val today by Graph.usage.today.collectAsStateWithLifecycle()
    val pendingReview by Graph.state.pendingReview.collectAsStateWithLifecycle()

    val pager = rememberPagerState { 2 }
    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var wantsSearchFocus by remember { mutableStateOf(false) }
    var resumeCount by remember { mutableIntStateOf(0) }
    var usageAccess by remember { mutableStateOf(Perms.hasUsageAccess()) }
    var setupIncomplete by remember { mutableStateOf(false) }

    // Every return to the launcher: re-read permissions, refresh today's numbers (then keep them
    // ticking once a minute), and see whether a weekly review has come due.
    LifecycleResumeEffect(settings.timersEnabled) {
        resumeCount++
        usageAccess = Perms.hasUsageAccess()
        setupIncomplete = !Perms.isDefaultLauncher(context) || !usageAccess ||
            (settings.timersEnabled && !Perms.isTimerServiceEnabled(context))
        val job = scope.launch {
            WeeklyReview.checkDue()
            val now = System.currentTimeMillis()
            if (usageAccess && now - lastBackfill > 6 * 3_600_000L) {
                lastBackfill = now
                launch { Graph.usage.backfill() }
            }
            while (true) {
                Graph.usage.refreshToday()
                delay(60_000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    // Leaving the launcher (an app was opened, the screen went off) always resets it to page 0.
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            query = ""
            menuApp = null
            wantsSearchFocus = false
            scope.launch { pager.scrollToPage(0) }
        }
    }

    LaunchedEffect(Unit) {
        homePresses.collect {
            menuApp = null
            query = ""
            if (pager.currentPage != 0) pager.animateScrollToPage(0)
        }
    }

    // A launcher is never "backed out of": back only returns from the drawer to home.
    BackHandler {
        if (pager.currentPage != 0) scope.launch { pager.animateScrollToPage(0) }
    }

    val launch: (AppEntry) -> Unit = { entry -> launchApp(context, scope, entry) }

    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it },
    ) { page ->
        // The page being left fades and sinks back a touch while the other one arrives. Done in
        // graphicsLayer, which runs in the draw phase: the swipe never triggers a recomposition.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                // Text on a flat background never overlaps itself, so each draw call can simply be
                // made more transparent. The default would render the whole page into a full-screen
                // off-screen buffer on every frame of the swipe: more memory and GPU for no gain.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = (1f - distance * 1.2f).coerceIn(0f, 1f)
                val scale = 1f - 0.05f * distance
                scaleX = scale
                scaleY = scale
            },
        ) {
            if (page == 0) {
                HomeScreen(
                    settings = settings,
                    apps = apps,
                    today = today,
                    usageAccess = usageAccess,
                    setupIncomplete = setupIncomplete,
                    pendingReview = pendingReview,
                    resumeCount = resumeCount,
                    onLaunch = launch,
                    onAppMenu = { menuApp = it },
                    onOpenDrawer = { focusSearch ->
                        wantsSearchFocus = focusSearch
                        scope.launch { pager.animateScrollToPage(1) }
                    },
                    onOpenSettings = { route -> context.startActivity(SettingsActivity.intent(context, route)) },
                    onOpenReview = { week -> context.startActivity(ReviewActivity.intent(context, week)) },
                )
            } else {
                DrawerScreen(
                    settings = settings,
                    apps = apps,
                    loaded = loaded,
                    today = today,
                    query = query,
                    onQueryChange = { query = it },
                    isActive = pager.currentPage == 1,
                    wantsSearchFocus = wantsSearchFocus,
                    onSearchFocusHandled = { wantsSearchFocus = false },
                    onLaunch = launch,
                    onAppMenu = { menuApp = it },
                )
            }
        }
    }

    menuApp?.let { app ->
        AppMenu(
            app = app,
            settings = settings,
            today = today,
            onDismiss = { menuApp = null },
            onOpenSetup = { context.startActivity(SettingsActivity.intent(context, "setup")) },
        )
    }
}
