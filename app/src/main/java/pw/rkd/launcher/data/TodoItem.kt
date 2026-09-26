package pw.rkd.launcher.data

import java.util.UUID

data class TodoItem(val id: String, val text: String, val checkedAt: Long? = null) {
    companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        fun create(text: String) = TodoItem(UUID.randomUUID().toString(), text.trim())
    }
}

fun List<TodoItem>.withoutExpiredTodos(now: Long = System.currentTimeMillis()): List<TodoItem> =
    filter { it.checkedAt == null || it.checkedAt > now - TodoItem.RETENTION_MS }
