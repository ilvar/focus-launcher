package com.rkd.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoSettingsTest {
    @Test fun `todo items survive settings backup including completion time`() {
        val items = listOf(TodoItem("a", "Buy milk"), TodoItem("b", "Pay bill", System.currentTimeMillis()))
        val restored = Settings.fromJson(Settings(showTodo = true, todos = items).toJson())
        assertEquals(items, restored.todos)
        assertEquals(true, restored.showTodo)
    }

    @Test fun `checked items expire after seven days and active items remain`() {
        val now = 1_000_000_000_000L
        val items = listOf(
            TodoItem("active", "Keep"),
            TodoItem("old", "Remove", now - TodoItem.RETENTION_MS - 1),
            TodoItem("recent", "Can uncheck", now - TodoItem.RETENTION_MS + 1),
        )
        assertEquals(listOf("active", "recent"), items.withoutExpiredTodos(now).map { it.id })
    }
}
