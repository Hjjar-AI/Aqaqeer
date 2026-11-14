package com.aqaqeer.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/** Opt-in demo evidence only. Run inside the Compose rule, before its activity teardown. */
class CiFailureCaptureRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } catch (failure: Throwable) {
                try {
                    val theme = InstrumentationRegistry.getArguments().getString("ciTheme") ?: "default"
                    captureCiScreen("failure-$theme-${description.methodName}")
                } catch (capture: Throwable) {
                    failure.addSuppressed(capture)
                }
                throw failure
            }
        }
    }
}

internal fun captureCiScreen(label: String) {
    if (InstrumentationRegistry.getArguments().getString("ciEvidence") != "true") return
    check(BuildConfig.IS_DEMO) { "CI visuals require the public demo profile" }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "ci-visuals")
        .apply { check(mkdirs() || isDirectory) }
    val name = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        File(directory, "$name.png").outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    } finally {
        bitmap.recycle()
    }
}
