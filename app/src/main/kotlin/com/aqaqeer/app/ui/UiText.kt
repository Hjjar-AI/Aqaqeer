package com.aqaqeer.app.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** A configuration-scoped reader can also be safely used by non-composable event/label callbacks. */
internal class UiText(private val resources: Resources) {
    operator fun invoke(@StringRes id: Int, vararg arguments: Any?): String = if (arguments.isEmpty())
        resources.getString(id) else String.format(java.util.Locale.ROOT, resources.getString(id), *arguments)
}

@Composable internal fun rememberUiText(): UiText {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { UiText(resources) }
}
