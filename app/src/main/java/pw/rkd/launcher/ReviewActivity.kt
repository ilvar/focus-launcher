package pw.rkd.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pw.rkd.launcher.service.WeeklyReview
import pw.rkd.launcher.ui.review.ReviewScreen
import pw.rkd.launcher.ui.theme.FocusTheme
import pw.rkd.launcher.ui.theme.applyFocusWindow
import java.time.LocalDate

/** Today's screen time and the weekly review. Opened from the home screen or the Sunday notification. */
class ReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val week = try {
            intent.getStringExtra(EXTRA_WEEK)?.let(LocalDate::parse)
        } catch (_: Exception) {
            null
        }
        if (week != null) {
            // Opening the review is what settles the reminder.
            Graph.state.setPendingReview(null)
            WeeklyReview.dismissNotification(this)
        }
        applyFocusWindow(Graph.settings.value.dark)
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark) { applyFocusWindow(settings.dark) }
            FocusTheme(settings) {
                ReviewScreen(settings = settings, initialWeek = week, onBack = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_WEEK = "week"

        /** [week] = start date of the week to review; null opens on today's numbers. */
        fun intent(context: Context, week: LocalDate?): Intent =
            Intent(context, ReviewActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_WEEK, week?.toString())
    }
}
