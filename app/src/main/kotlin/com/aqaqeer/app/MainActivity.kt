package com.aqaqeer.app

import android.os.Bundle
import android.animation.Animator
import android.os.LocaleList
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.aqaqeer.app.ui.SplashArtwork
import com.aqaqeer.app.ui.awaitSplashLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aqaqeer.app.data.CatalogState
import com.aqaqeer.app.data.DisplayPreferences
import com.aqaqeer.app.data.ThemeChoice
import com.aqaqeer.app.data.ThemeAccent
import com.aqaqeer.app.ui.appColorScheme
import com.aqaqeer.app.ui.themeIsDark
import androidx.compose.ui.graphics.toArgb
import com.aqaqeer.app.ui.AqaqeerApp
import com.aqaqeer.app.ui.CatalogViewModel
import com.aqaqeer.app.ui.animateSplashExit
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: CatalogViewModel by viewModels()
    private var splashExit: Animator? = null
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("ar"))
            setLayoutDirection(java.util.Locale.forLanguageTag("ar"))
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        com.aqaqeer.app.data.FetchedChanges.beginSession(this)
        com.aqaqeer.app.ui.UiSession.begin()
        val (launchTheme, launchAccent) = DisplayPreferences.startupPresentation(this)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = themeIsDark(launchTheme, systemDark)
        setTheme(when {
            launchTheme == ThemeChoice.AMOLED -> R.style.Theme_Aqaqeer_Starting_Black
            launchAccent == ThemeAccent.CRIMSON && dark -> R.style.Theme_Aqaqeer_Starting_CrimsonDark
            !dark && launchAccent == ThemeAccent.CRIMSON -> R.style.Theme_Aqaqeer_Starting_CrimsonLight
            !dark && launchAccent == ThemeAccent.BLUE -> R.style.Theme_Aqaqeer_Starting_BlueLight
            !dark && launchAccent == ThemeAccent.GREEN -> R.style.Theme_Aqaqeer_Starting_GreenLight
            !dark && launchAccent == ThemeAccent.PURPLE -> R.style.Theme_Aqaqeer_Starting_PinkLight
            dark -> R.style.Theme_Aqaqeer_Starting_Dark
            else -> R.style.Theme_Aqaqeer_Starting_Light
        })
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(appColorScheme(launchTheme, launchAccent, systemDark, resources).background.toArgb()))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        // Start the shared decode while the database initializes; never decode in the exit callback.
        val splashBitmap = lifecycleScope.async(Dispatchers.IO) { SplashArtwork.bitmap(resources) }
        // Local database readiness only; never a timer, network call, or PIN lock.
        splash.setKeepOnScreenCondition { viewModel.state.value.catalog is CatalogState.Loading }
        splash.setOnExitAnimationListener { provider ->
            lifecycleScope.launch {
                var handedOff = false
                try {
                    val bitmap = splashBitmap.await()
                    val decor = window.decorView as ViewGroup
                    awaitSplashLayout(decor)
                    val state = viewModel.state.value
                    val background = appColorScheme(state.theme, state.accent, systemDark, resources).background.toArgb()
                    splashExit = animateSplashExit(provider, decor, background, bitmap)
                    handedOff = true
                } finally {
                    // Also release the native splash if destruction cancels the pending hand-off.
                    if (!handedOff) provider.remove()
                }
            }
        }
        setContent { AqaqeerApp(viewModel) }
        if (savedInstanceState == null) receiveConnectionIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) {
            com.aqaqeer.app.data.FetchedChanges.finishTask()
            com.aqaqeer.app.ui.UiSession.finish()
        }
        splashExit?.cancel()
        splashExit = null
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
