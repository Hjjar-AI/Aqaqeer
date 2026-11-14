package com.aqaqeer.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AqaqeerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStrings.resources = resources
    }
}
