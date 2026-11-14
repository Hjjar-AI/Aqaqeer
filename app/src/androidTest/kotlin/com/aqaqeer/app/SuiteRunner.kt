package com.aqaqeer.app

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File

/** Host supplies only a shell-private path, never a token in runner arguments or APK assets. */
class SuiteRunner : AndroidJUnitRunner() {
    private var secretPath: String? = null
    override fun newApplication(loader: ClassLoader, name: String, context: Context): Application =
        super.newApplication(loader, HiltTestApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        secretPath = arguments.getString("suiteSecretPath")
        super.onCreate(arguments)
    }

    override fun onStart() {
        secretPath?.let { path ->
            require(path.matches(Regex("/data/local/tmp/aqaqeer-suite-[a-zA-Z0-9_-]+\\.json")))
            check(targetContext.packageName.contains(".verification"))
            if (!BuildConfig.IS_DEMO) {
                val destination = File(targetContext.noBackupFilesDir, "telegram-test-config.local.json")
                ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("cat $path")).use { input ->
                    val bytes = input.readBytes()
                    try {
                        check(bytes.isNotEmpty() && bytes.size < 65536)
                        destination.outputStream().use { it.write(bytes) }
                        android.system.Os.chmod(destination.path, 384)
                    } finally { bytes.fill(0) }
                }
            }
        }
        super.onStart()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        secretPath?.let { path ->
            val owned = File(targetContext.noBackupFilesDir, "telegram-test-owned-message-ids.json")
            if (owned.isFile) {
                val ids = owned.readText()
                check(ids.matches(Regex("\\[[0-9, ]*\\]")))
                ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("printf '%s' '$ids' > $path.owned")).use { it.readBytes() }
                owned.delete()
            }
        }
        File(targetContext.noBackupFilesDir, "telegram-test-config.local.json").delete()
        super.finish(resultCode, results)
    }
}
