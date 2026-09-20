package com.focus.launcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.focus.launcher.BlockActivity
import com.focus.launcher.Graph
import com.focus.launcher.R
import com.focus.launcher.data.AppLimit
import com.focus.launcher.util.Perms
import com.focus.launcher.util.formatDuration
import com.focus.launcher.util.formatMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What has to happen for the app that is in front. Worked out by [TimerWatch.decide], a pure function. */
sealed interface WatchAction {
    /** Nothing to do: not a limited app, or a visit the user already agreed to. */
    data object Idle : WatchAction

    /** The limit was set aside for today, but every visit still needs a yes. */
    data object AskConsent : WatchAction

    /** Inside a "continue for N minutes" window: look again when it closes. */
    data class LookAgainAt(val time: Long) : WatchAction

    /** Time is left: come back when it runs out. */
    data class LockIn(val millis: Long) : WatchAction

    /** Today's allowance is gone. */
    data class Lock(val usedMs: Long) : WatchAction
}

object TimerWatch {
    fun decide(
        pkg: String,
        ownPackage: String,
        limit: AppLimit?,
        bypassedToday: Boolean,
        needsConsent: Boolean,
        consentedPkg: String?,
        now: Long,
        passUntil: Long,
        usedMs: Long,
    ): WatchAction = when {
        pkg == ownPackage || limit == null -> WatchAction.Idle
        bypassedToday -> if (needsConsent && consentedPkg != pkg) WatchAction.AskConsent else WatchAction.Idle
        now < passUntil -> WatchAction.LookAgainAt(passUntil)
        usedMs < limit.millis -> WatchAction.LockIn(limit.millis - usedMs)
        else -> WatchAction.Lock(usedMs)
    }
}

/**
 * Enforces app timers while the user is inside an app, with nothing but the usage access Focus
 * already has.
 *
 * Focus knows when the home screen is left and when it is back. In between, this foreground
 * service reads the usage log every few seconds to learn which app is in front (the same log the
 * screen-time numbers come from), and for a limited app it sets a timer for the moment today's
 * allowance runs out. With the screen off it does nothing at all.
 *
 * It replaced an accessibility service. That one needed no polling, but an APK that declares an
 * accessibility service (or a notification listener) is blocked by Google Play Protect when it is
 * installed from a download, which is how Focus is installed.
 *
 * Putting the wall in front of another app is a start from the background. Android allows that to
 * an app the user let "display over other apps"; without that switch the user gets a notification
 * instead, and the app is locked the next time it is opened from Focus.
 */
class TimerWatchService : Service() {

    private val main = Handler(Looper.getMainLooper())

    private var currentPkg: String? = null
    private var sessionStart = 0L
    private var lastPoll = 0L
    private var notifiedFor: String? = null

    /** Bumped on every foreground change so results of slow lookups for an old app are dropped. */
    private var generation = 0

    private val poll = Runnable { pollForeground() }
    private val check = Runnable { evaluate() }
    private var warn: Runnable? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    cancelTimers()
                    main.removeCallbacks(poll)
                    Graph.limits.sessionConsent = null // the next unlock is a new visit
                }
                // After unlocking, the same app is back in front without anything new in the log.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    pollForeground()
                    evaluate()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannels(this)
        val notification = statusNotification(null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(STATUS_ID, notification)
            }
        } catch (_: Exception) {
            // Android decides whether an app may go to the foreground right now. A launcher must not
            // fall over because the answer was no: without the watcher the launch gate still works.
            stopSelf()
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // Not sticky: if the system ends it, the next time the home screen is left starts it again.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pollForeground()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        cancelTimers()
        main.removeCallbacks(poll)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    /** Asks the usage log which app came to the front since the last look. */
    private fun pollForeground() {
        main.removeCallbacks(poll)
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return
        val now = System.currentTimeMillis()
        val since = (if (lastPoll > 0) lastPoll else now - FIRST_LOOK_BACK_MS) - OVERLAP_MS
        lastPoll = now
        Graph.scope.launch(Dispatchers.IO) {
            val pkg = Graph.usage.lastResumedPackage(since)
            main.post {
                if (instance !== this@TimerWatchService) return@post
                if (pkg != null && pkg != currentPkg) foregroundChanged(pkg)
                main.postDelayed(poll, POLL_MS)
            }
        }
    }

    private fun foregroundChanged(pkg: String) {
        currentPkg = pkg
        sessionStart = System.currentTimeMillis()
        notifiedFor = null
        generation++
        if (pkg != packageName && Graph.apps.isHomeApp(pkg)) {
            // A home screen: the visit the user agreed to is over.
            Graph.limits.sessionConsent = null
            // With gesture navigation the phone's own launcher is also the recent-apps screen, so
            // it comes to the front all day long and means nothing. But if Focus is not the home
            // app at all, its home screen will never be resumed to end this service: end it here.
            if (!Perms.isDefaultLauncher(this)) {
                stopSelf()
                return
            }
        }
        evaluate()
    }

    private fun cancelTimers() {
        main.removeCallbacks(check)
        warn?.let(main::removeCallbacks)
        warn = null
    }

    /** Works out what, if anything, has to happen for the app that is in front right now. */
    private fun evaluate() {
        cancelTimers()
        val pkg = currentPkg ?: return
        val limits = Graph.limits
        val limit = limits.limitFor(pkg)
        if (pkg == packageName || limit == null) {
            showStatus(null)
            return
        }
        val gen = generation
        val since = sessionStart - 2_000
        // Shared pool: the service keeps no thread of its own alive between the rare look-ups.
        Graph.scope.launch(Dispatchers.IO) {
            val used = Graph.usage.usageTodayMs(pkg)
            val action = TimerWatch.decide(
                pkg = pkg, ownPackage = packageName, limit = limit,
                bypassedToday = limits.isBypassedToday(pkg), needsConsent = limits.needsConsent(pkg),
                consentedPkg = limits.sessionConsent, now = System.currentTimeMillis(),
                passUntil = limits.extensionUntil(pkg), usedMs = used,
            )
            // About to act on an app: confirm with the usage log that it really is still in front.
            val actuallyInFront = if (action is WatchAction.Lock || action is WatchAction.AskConsent) Graph.usage.lastResumedPackage(since) else null
            main.post {
                if (gen != generation || currentPkg != pkg || instance !== this@TimerWatchService) return@post
                if (actuallyInFront != null && actuallyInFront != pkg && actuallyInFront != packageName) {
                    foregroundChanged(actuallyInFront) // a switch was missed: follow the log, do not lock the wrong app
                    return@post
                }
                when (action) {
                    WatchAction.Idle -> showStatus(null)
                    WatchAction.AskConsent -> bringUp(pkg, used, limit, consent = true)
                    is WatchAction.LookAgainAt -> {
                        main.postDelayed(check, action.time - System.currentTimeMillis() + 250)
                        showStatus("${Graph.apps.labelForPackage(pkg)}: continued until ${clock(action.time)}")
                    }
                    is WatchAction.LockIn -> scheduleFor(pkg, action.millis)
                    is WatchAction.Lock -> {
                        limits.recordBlocked(pkg)
                        bringUp(pkg, action.usedMs, limit, consent = false)
                    }
                }
            }
        }
    }

    /** Arms the timer for the moment today's allowance runs out, plus an optional heads-up before it. */
    private fun scheduleFor(pkg: String, remaining: Long) {
        main.postDelayed(check, remaining.coerceAtLeast(1_500) + 300)
        val label = Graph.apps.labelForPackage(pkg)
        showStatus("$label locks at ${clock(System.currentTimeMillis() + remaining)}")
        val warnMs = Graph.settings.value.warnMinutes * 60_000L
        if (warnMs > 0 && remaining > warnMs + 5_000) {
            val r = Runnable {
                if (currentPkg == pkg) {
                    val left = formatMinutes(Graph.settings.value.warnMinutes)
                    Toast.makeText(this, "$left left on $label today", Toast.LENGTH_LONG).show()
                }
            }
            warn = r
            main.postDelayed(r, remaining - warnMs)
        }
    }

    /**
     * The wall (or the "open anyway?" question), in front of the app that is open. Allowed from
     * here only if the user let Focus display over other apps; otherwise a notification says that
     * time is up, once per visit, and a tap on it brings the wall.
     */
    private fun bringUp(pkg: String, usedMs: Long, limit: AppLimit, consent: Boolean) {
        val intent = BlockActivity.intent(this, pkg, usedMs, limit.minutes, midSession = true, consent = consent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Perms.canDrawOverlays(this)) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        // The question before a visit is only worth asking in front of the app, not from the shade.
        if (consent || notifiedFor == pkg || !Perms.canPostNotifications(this)) return
        notifiedFor = pkg
        val label = Graph.apps.labelForPackage(pkg)
        val open = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = "${formatDuration(usedMs)} today, and your limit is ${formatMinutes(limit.minutes)}. Tap to close it."
        val notification = Notification.Builder(this, CHANNEL_TIME_UP)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Time's up for $label")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(TIME_UP_ID, notification)
    }

    private fun showStatus(text: String?) {
        getSystemService(NotificationManager::class.java)?.notify(STATUS_ID, statusNotification(text))
    }

    private fun statusNotification(text: String?): Notification =
        Notification.Builder(this, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(text ?: "App timers are running")
            .setContentText(if (text == null) "Focus checks which app is open until you are back on the home screen." else null)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    private fun clock(time: Long): String =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    companion object {
        private const val CHANNEL_WATCH = "timer_watch"
        private const val CHANNEL_TIME_UP = "time_up"
        private const val STATUS_ID = 21
        private const val TIME_UP_ID = 22

        /** How often the usage log is asked which app is in front, while the screen is on. */
        private const val POLL_MS = 4_000L
        private const val OVERLAP_MS = 1_500L
        private const val FIRST_LOOK_BACK_MS = 15_000L

        @Volatile
        private var instance: TimerWatchService? = null

        val isRunning: Boolean get() = instance != null

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_WATCH, context.getString(R.string.channel_watch), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_watch_desc); setShowBadge(false) },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_TIME_UP, context.getString(R.string.channel_time_up), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_time_up_desc) },
            )
        }

        /**
         * Called when the home screen is being left. Does nothing unless timers are on, usage can
         * be read and at least one kind of app has a limit: without those there is nothing to watch.
         */
        fun start(context: Context) {
            if (instance != null) return
            val s = Graph.settings.value
            val anyLimit = s.socialDefaultMin > 0 || s.gameDefaultMin > 0 || s.videoDefaultMin > 0 || s.appLimits.values.any { it > 0 }
            if (!s.timersEnabled || !anyLimit || !Graph.usage.hasAccess()) return
            if (context.getSystemService(PowerManager::class.java)?.isInteractive == false) return
            try {
                // startService, not startForegroundService: the home screen is still in front at this
                // moment, so a plain start is allowed, and it carries no "must go foreground within
                // five seconds or the app is killed" promise that a refusal could turn into a crash.
                context.startService(Intent(context, TimerWatchService::class.java))
            } catch (_: Exception) {
                // Android no longer counts the app as in front: no watcher this time.
            }
        }

        /** Called when the home screen is back: from here on the launch gate does the work. */
        fun stop(context: Context) {
            context.stopService(Intent(context, TimerWatchService::class.java))
            context.getSystemService(NotificationManager::class.java)?.cancel(TIME_UP_ID)
        }

        /** Re-checks the app in front; call after limits or passes change. */
        fun recheck() {
            val service = instance ?: return
            service.main.post { service.evaluate() }
        }
    }
}
