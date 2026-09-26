package com.rkd.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rkd.launcher.data.CALENDAR_ALL
import com.rkd.launcher.data.CALENDAR_AUTO
import com.rkd.launcher.data.CALENDAR_SELECTED
import com.rkd.launcher.data.CalendarInfo
import com.rkd.launcher.ui.components.FocusDialog
import com.rkd.launcher.ui.components.Hairline
import com.rkd.launcher.ui.components.T
import com.rkd.launcher.ui.components.UnderlinedField
import com.rkd.launcher.ui.theme.LocalFocusColors

/**
 * Picks the one calendar shown on the home screen. Phones with a few Google accounts easily carry
 * a dozen calendars, half of them called "Holidays", so the list can be searched by calendar or
 * account name, and every entry says how many events it has coming up: the quickest way to
 * recognise the one you live in.
 */
@Composable
internal fun CalendarPickerDialog(
    calendars: List<CalendarInfo>,
    upcomingCounts: Map<String, Int>,
    selectedKey: String,
    selectedKeys: Set<String>,
    workProfileBlocked: Boolean,
    onDismiss: () -> Unit,
    onPick: (String, Set<String>) -> Unit,
) {
    val c = LocalFocusColors.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val shown = remember(calendars, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) calendars
        else calendars.filter { it.name.contains(q, ignoreCase = true) || it.account.contains(q, ignoreCase = true) }
    }

    FocusDialog(onDismiss, title = "Calendars to show", subtitle = "Tap calendars to include them. Changes save immediately.", tall = true) {
        UnderlinedField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search calendars",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            imeAction = ImeAction.Search,
        )
        LazyColumn(Modifier.weight(1f)) {
            if (workProfileBlocked) {
                item(key = "work-note") {
                    Column {
                        T(
                            "Looking for your work calendar? This phone has a Work profile, and the organisation that manages " +
                                "it does not allow other apps to read its calendars, so they cannot be listed here. If you share " +
                                "your work calendar with one of your personal Google accounts, it appears in this list.",
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            size = 13.sp, color = c.dim, lineHeight = 19.sp,
                        )
                        Hairline()
                    }
                }
            }
            items(shown, key = { it.key }) { calendar ->
                val count = upcomingCounts[calendar.key] ?: 0
                CalendarRow(
                    name = calendar.name,
                    account = calendar.account.takeIf { it.isNotEmpty() && it != calendar.name },
                    detail = if (count > 0) "$count upcoming" else "empty",
                    selected = if (selectedKey == CALENDAR_SELECTED) calendar.key in selectedKeys else calendar.key == selectedKey,
                ) {
                    val current = if (selectedKey == CALENDAR_SELECTED) selectedKeys else if (selectedKey.startsWith("p:") || selectedKey.startsWith("w:")) setOf(selectedKey) else emptySet()
                    val next = if (calendar.key in current) current - calendar.key else current + calendar.key
                    onPick(if (next.isEmpty()) CALENDAR_AUTO else CALENDAR_SELECTED, next)
                }
            }
            if (shown.isEmpty()) {
                item(key = "none") {
                    T("No calendar matches “${query.text.trim()}”.", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), size = 15.sp, color = c.dim)
                }
            }
            if (query.text.isBlank()) {
                item(key = "auto") {
                    CalendarRow("Automatically choose one", null, null, selectedKey == CALENDAR_AUTO) {
                        onPick(CALENDAR_AUTO, emptySet())
                    }
                }
                item(key = "all") {
                    CalendarRow("All calendars together", account = null, detail = null, selected = selectedKey == CALENDAR_ALL) {
                        onPick(CALENDAR_ALL, emptySet())
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(name: String, account: String?, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalFocusColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(name, size = 17.sp, maxLines = 1)
            if (account != null) T(account, Modifier.padding(top = 2.dp), size = 12.sp, color = c.faint, maxLines = 1)
        }
        if (detail != null) T(detail, size = 12.sp, color = c.dim, maxLines = 1)
        if (selected) Box(Modifier.size(8.dp).background(c.fg, CircleShape))
    }
}
