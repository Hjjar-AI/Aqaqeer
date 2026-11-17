package com.aqaqeer.app.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Presentation-only task session. Never written to saved state, preferences or backups. */
internal object UiSession {
    private var active = false
    private val dismissed = mutableStateMapOf<List<String>, List<String>>()

    fun begin() {
        if (active) return
        dismissed.clear()
        active = true
    }
    fun finish() { active = false }
    fun isDismissed(identity: List<String>, content: List<String>): Boolean = dismissed[identity] == content
    fun reconcile(scope: List<String>, activeContent: Map<List<String>, List<String>>) {
        dismissed.keys.filter { it.take(scope.size) == scope && activeContent[it] != dismissed[it] }
            .forEach { dismissed.remove(it) }
    }
    fun dismiss(identity: List<String>, content: List<String>) { dismissed[identity] = content.toList() }
}

internal val LocalNoticeScope = staticCompositionLocalOf { "preview" }
internal data class HomeNotice(val kind: String, val text: String, val content: List<String> = listOf(text))
