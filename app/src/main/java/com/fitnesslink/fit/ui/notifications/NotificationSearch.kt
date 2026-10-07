package com.fitnesslink.fit.ui.notifications

/**
 * Inbox search (port of iOS `NotificationSearch`, product request
 * 2026-09-26). Case-insensitive match against title + body. Plain text
 * matches anywhere ("plan" hits "Meal plan ready"); `*` is a wildcard for
 * any run of characters, so "work*plan" matches "Your workout plan is
 * ready" and "*week" matches anything ending in "week". Whitespace-separated
 * terms must all match (AND).
 */
object NotificationSearch {
    fun matches(query: String, title: String, body: String): Boolean {
        val terms = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val haystack = (title + "\n" + body).lowercase()
        return terms.all { term ->
            val t = term.lowercase()
            if (!t.contains('*')) return@all haystack.contains(t)
            val parts = t.split('*').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return@all true
            var cursor = 0
            for (part in parts) {
                val idx = haystack.indexOf(part, startIndex = cursor)
                if (idx < 0) return@all false
                cursor = idx + part.length
            }
            true
        }
    }
}
