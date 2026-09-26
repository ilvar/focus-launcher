package pw.rkd.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pw.rkd.launcher.service.FocusAccessibilityService
import pw.rkd.launcher.ui.block.BlockRequest
import pw.rkd.launcher.ui.block.BlockScreen
import pw.rkd.launcher.ui.launchOptions
import pw.rkd.launcher.ui.start
import pw.rkd.launcher.ui.theme.FocusTheme
import pw.rkd.launcher.ui.theme.applyFocusWindow

/**
 * The wall that goes up when an app's daily time is used. It is raised either by the accessibility
 * service in the middle of a session (then the limited app is right underneath, and leaving the
 * wall by "continue" simply reveals it again) or by the launcher when a spent app is tapped
 * (then "continue" still has to start the app).
 *
 * The same screen, in its "consent" form, is what asks "open anyway?" before every visit to an app
 * whose limit the user has ignored for the day.
 */
class BlockActivity : ComponentActivity() {
    private var request by mutableStateOf<BlockRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = parse(intent)
        if (request == null) {
            finish()
            return
        }
        applyFocusWindow(Graph.settings.value.dark)
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            FocusTheme(settings) {
                request?.let { current ->
                    BlockScreen(
                        request = current,
                        settings = settings,
                        // A preview from settings only shows the wall; its buttons change nothing.
                        onClose = { if (current.preview) finish() else goHome() },
                        onContinue = { minutes ->
                            if (current.preview) finish() else {
                                Graph.limits.grantExtension(current.packageName, minutes)
                                resume(current)
                            }
                        },
                        onBypass = {
                            if (current.preview) finish() else {
                                Graph.limits.bypassToday(current.packageName)
                                // Choosing this is itself the consent for the visit it starts.
                                Graph.limits.sessionConsent = current.packageName
                                resume(current)
                            }
                        },
                        onConsent = {
                            Graph.limits.sessionConsent = current.packageName
                            resume(current)
                        },
                        onRestoreLimit = {
                            Graph.limits.clearPasses(current.packageName)
                            Toast.makeText(this, "The limit for ${current.label} is back on", Toast.LENGTH_SHORT).show()
                            goHome()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let { request = it }
    }

    override fun onStop() {
        super.onStop()
        // Never linger in the background; if the app is still over its limit the wall comes back.
        if (!isChangingConfigurations) finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun resume(current: BlockRequest) {
        if (!current.midSession) {
            val entry = current.appKey?.let { Graph.apps.byKey(it) } ?: Graph.apps.byPackage(current.packageName)
            if (entry != null) start(this, entry)
            else packageManager.getLaunchIntentForPackage(current.packageName)?.let { startActivity(it, launchOptions(this)) }
        }
        FocusAccessibilityService.recheck()
        finish()
    }

    private fun parse(intent: Intent?): BlockRequest? {
        val pkg = intent?.getStringExtra(EXTRA_PACKAGE) ?: return null
        val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        return BlockRequest(
            packageName = pkg,
            label = if (preview) "Example app" else Graph.apps.labelForPackage(pkg),
            usedMs = intent.getLongExtra(EXTRA_USED, 0L),
            limitMinutes = intent.getIntExtra(EXTRA_LIMIT, 0),
            midSession = intent.getBooleanExtra(EXTRA_MID_SESSION, false),
            appKey = intent.getStringExtra(EXTRA_APP_KEY),
            preview = preview,
            consent = intent.getBooleanExtra(EXTRA_CONSENT, false),
        )
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_USED = "used"
        private const val EXTRA_LIMIT = "limit"
        private const val EXTRA_MID_SESSION = "mid_session"
        private const val EXTRA_APP_KEY = "app_key"
        private const val EXTRA_PREVIEW = "preview"
        private const val EXTRA_CONSENT = "consent"

        fun intent(
            context: Context,
            packageName: String,
            usedMs: Long,
            limitMinutes: Int,
            midSession: Boolean,
            appKey: String? = null,
            preview: Boolean = false,
            consent: Boolean = false,
        ): Intent = Intent(context, BlockActivity::class.java)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_USED, usedMs)
            .putExtra(EXTRA_LIMIT, limitMinutes)
            .putExtra(EXTRA_MID_SESSION, midSession)
            .putExtra(EXTRA_APP_KEY, appKey)
            .putExtra(EXTRA_PREVIEW, preview)
            .putExtra(EXTRA_CONSENT, consent)
    }
}
