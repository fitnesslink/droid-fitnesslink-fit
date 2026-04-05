package com.fitnesslink.fit.model

import androidx.compose.ui.graphics.Color
import com.fitnesslink.fit.ui.theme.BlueTheme
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

enum class NotificationType(val label: String, val icon: String, val color: Color) {
    SYSTEM("System", "megaphone", MediumGray),
    GOALS("Goals", "target", FLPrimary),
    CONTENT("Content", "book", BlueTheme),
    CALENDAR("Calendar", "calendar", OrangeTheme);

    companion object {
        fun fromString(value: String): NotificationType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

enum class NotificationTab(val label: String) {
    ALL("All"), SYSTEM("System"), GOALS("Goals"),
    CONTENT("Content"), CALENDAR("Calendar");

    val notificationType: NotificationType?
        get() = when (this) {
            ALL -> null
            SYSTEM -> NotificationType.SYSTEM
            GOALS -> NotificationType.GOALS
            CONTENT -> NotificationType.CONTENT
            CALENDAR -> NotificationType.CALENDAR
        }
}

data class NotificationItem(
    val id: String = "",
    val type: NotificationType = NotificationType.SYSTEM,
    val title: String = "",
    val body: String = "",
    val isRead: Boolean = false,
    val createdAt: Date = Date(),
    val deepLink: String? = null
) {
    val timeAgo: String
        get() {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(Date().time - createdAt.time)
            if (seconds < 60) return "Just now"
            val minutes = seconds / 60
            if (minutes < 60) return "${minutes}m ago"
            val hours = minutes / 60
            if (hours < 24) return "${hours}h ago"
            val days = hours / 24
            if (days == 1L) return "Yesterday"
            if (days < 7) return "${days}d ago"
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            return sdf.format(createdAt)
        }

    companion object {
        fun isToday(date: Date): Boolean {
            val cal = Calendar.getInstance()
            val todayCal = Calendar.getInstance()
            cal.time = date
            return cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        }

        fun isYesterday(date: Date): Boolean {
            val cal = Calendar.getInstance()
            val yesterdayCal = Calendar.getInstance()
            yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
            cal.time = date
            return cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
        }
    }
}
