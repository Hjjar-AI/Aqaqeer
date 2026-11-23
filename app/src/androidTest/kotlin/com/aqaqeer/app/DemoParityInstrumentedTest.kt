package com.aqaqeer.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Authored Android provider/font/Keystore checks; workflow 03 compiles but does not execute them. */
class DemoParityInstrumentedTest {
    private lateinit var profile: SuiteProfile
    private val documents = mutableListOf<Uri>()
    @Before fun setup() {
        Assume.assumeTrue(BuildConfig.IS_DEMO)
        profile = SuiteProfile(); DemoSession.reset(); DemoSession.setAdmin(true)
    }
    @After fun cleanup() {
        if (::profile.isInitialized) {
            documents.forEach { profile.context.contentResolver.delete(it, null, null) }
            profile.close(); DemoSession.reset(); DemoSession.setAdmin(false)
        }
    }
    private fun document(): Uri {
        val authority = InstrumentationRegistry.getInstrumentation().context.packageName + ".suite-documents"
        return Uri.parse("content://$authority/demo-parity-${UUID.randomUUID()}").also { documents += it }
    }
    @Test fun demoPdfExportsAllPresetsThroughRealProviderWithRenderableA5Pages(): Unit = runBlocking {
        val reports = ReportRepository(profile.admin, DocumentOutput(profile.context), PdfReports(profile.context))
        for (preset in ReportPreset.entries) {
            val snapshot = reports.capture(ReportRequest(preset, DemoSession.authorized().catalog.sections.first().id))
            val target = document(); reports.pdf(target, snapshot)
            val file = File(profile.root, "${preset.name}.pdf")
            profile.context.contentResolver.openInputStream(target)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount >= 1)
                for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                    assertEquals(420, page.width); assertEquals(595, page.height)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        assertTrue("Every exported page contains content", pixels.count { it != Color.WHITE } > 100)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }
    @Test fun demoBackupRestoreDoesNotAuthorizeOrMutateInstalledCatalog(): Unit = runBlocking {
        profile.ready()
        val installed = profile.admin.transaction { Triple(it.catalog(), it.deviceIdentity(), it.pending()) }
        DemoSession.edit { c, _, _, _ -> c.copy(medications = c.medications.mapIndexed { i, med -> if (i == 0) med.copy(note = "demo archive") else med }) }
        val saved = DemoSession.authorized(); val target = document(); val files = DemoFiles(profile.context)
        files.exportBackup(target, "demo backup pass".toCharArray(), "demo backup pass".toCharArray())
        DemoSession.publish()
        files.restoreBackup(target, "demo backup pass".toCharArray(), saved.catalog.pharmacy!!.name, true)
        assertEquals(saved.catalog, DemoSession.initialize().catalog)
        assertFalse(DemoSession.initialize().admin); assertTrue(DemoSession.initialize().restorePaused)
        assertEquals(installed, profile.admin.transaction { Triple(it.catalog(), it.deviceIdentity(), it.pending()) })
    }
}
