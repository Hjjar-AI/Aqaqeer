package com.aqaqeer.app

import android.content.res.Resources

/** Application resources also serve non-composable UI formatters. V1 has one Arabic interface. */
internal object AppStrings {
    lateinit var resources: Resources
}
internal fun uiText(id: Int, vararg arguments: Any?): String = if (arguments.isEmpty())
    AppStrings.resources.getString(id) else AppStrings.resources.getString(id, *arguments)
