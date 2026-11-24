package com.aqaqeer.app

import android.os.Bundle
import android.os.LocaleList
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aqaqeer.app.data.DisplayPreferences
import com.aqaqeer.app.data.CatalogState
import com.aqaqeer.app.data.FetchedChanges
import com.aqaqeer.app.data.ThemeChoice
import com.aqaqeer.app.data.ThemeAccent
import com.aqaqeer.app.ui.appColorScheme
import com.aqaqeer.app.ui.themeIsDark
import androidx.compose.ui.graphics.toArgb
import com.aqaqeer.app.ui.AqaqeerApp
import com.aqaqeer.app.ui.CatalogViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: CatalogViewModel by viewModels()
    private var appFrameReady = false
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("ar"))
            setLayoutDirection(java.util.Locale.forLanguageTag("ar"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        FetchedChanges.scheduleSessionStart(this)
        com.aqaqeer.app.ui.UiSession.begin()
        val (launchTheme, launchAccent) = DisplayPreferences.startupPresentation(this)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = themeIsDark(launchTheme, systemDark)
        setTheme(when {
            launchTheme == ThemeChoice.AMOLED -> R.style.Theme_Aqaqeer_Starting_Black
            dark -> when (launchAccent) {
                ThemeAccent.PURPLE -> R.style.Theme_Aqaqeer_Starting_Dark
                ThemeAccent.BLUE -> R.style.Theme_Aqaqeer_Starting_BlueDark
                ThemeAccent.GREEN -> R.style.Theme_Aqaqeer_Starting_GreenDark
                ThemeAccent.ROSE -> R.style.Theme_Aqaqeer_Starting_RoseDark
            }
            else -> when (launchAccent) {
                ThemeAccent.PURPLE -> R.style.Theme_Aqaqeer_Starting_Light
                ThemeAccent.BLUE -> R.style.Theme_Aqaqeer_Starting_BlueLight
                ThemeAccent.GREEN -> R.style.Theme_Aqaqeer_Starting_GreenLight
                ThemeAccent.ROSE -> R.style.Theme_Aqaqeer_Starting_RoseLight
            }
        })
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val startupState = viewModel.shellState
        // The native pre-draw gate must be releasable without a Compose frame:
        // activity launch/idling can otherwise wait on the callback it prevents.
        splash.setKeepOnScreenCondition { !appFrameReady && startupState.value.catalog == CatalogState.Loading }
        splash.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()
        val launchBackground = appColorScheme(launchTheme, launchAccent, systemDark, resources).background.toArgb()
        window.setBackgroundDrawable(ColorDrawable(launchBackground))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent { AqaqeerApp(viewModel) { appFrameReady = it } }
        if (savedInstanceState == null) receiveConnectionIntent(intent)
    }

    override fun onDestroy() {
        appFrameReady = true
        if (isFinishing) {
            FetchedChanges.scheduleTaskFinish()
            com.aqaqeer.app.ui.UiSession.finish()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveConnectionIntent(intent)
    }

    private fun receiveConnectionIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme == "content") viewModel.openConnectionImport(uri)
        // Do not retain incoming URI/credentials in the activity's saved intent.
        intent.data = null
        intent.clipData = null
    }
}
