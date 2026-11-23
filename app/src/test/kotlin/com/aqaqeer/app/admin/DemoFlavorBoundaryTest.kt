package com.aqaqeer.app.admin

import android.content.Context
import android.net.Uri
import com.aqaqeer.app.BuildConfig
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Assert.*
import org.junit.Test

class DemoFlavorBoundaryTest {
    @Test fun standardBuildRejectsDemoWorkspaceEntry() {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        assertThrows(IllegalStateException::class.java) { DemoSession.initialize() }
    }
    @Test fun standardBuildRejectsDemoFileOperationsBeforeProviderAccess(): Unit = runBlocking {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        val context = mockk<Context>(); val uri = mockk<Uri>(); val files = DemoFiles(context)
        assertThrows(IllegalStateException::class.java) { runBlocking { files.exportConnection(uri, "123456".toCharArray()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { files.importConnection(uri, "123456".toCharArray()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { files.exportBackup(uri, "demo passphrase".toCharArray(), "demo passphrase".toCharArray()) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { files.restoreBackup(uri, "demo passphrase".toCharArray(), "sample", true) } }
        verify { context wasNot Called }; verify { uri wasNot Called }
    }
}
