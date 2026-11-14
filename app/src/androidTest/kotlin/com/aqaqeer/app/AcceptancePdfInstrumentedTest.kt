package com.aqaqeer.app

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.*
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

/** Deliberately mixed-script fixtures; generated through the real report capture/writer. */
class AcceptancePdfInstrumentedTest {
    private lateinit var profile: SuiteProfile
    @Before fun setup(): Unit = runBlocking {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        check(BuildConfig.APPLICATION_ID.contains(".verification."))
        profile=SuiteProfile();profile.create()
    }
    @After fun cleanup() { if(::profile.isInitialized) profile.close() }
    @Test fun exportArabicPresetsForHumanPageReview(): Unit = runBlocking {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val run=InstrumentationRegistry.getArguments().getString("acceptanceRun") ?: error("Host run required")
        check(run.matches(Regex("[a-f0-9]{32}")))
        val output=File(base.noBackupFilesDir,"acceptance-$run").apply { mkdirs() }
        val seed=M3Fixtures.catalog().medications.single();val section=profile.ready().catalog.sections.single()
        val today=LocalDate.now(java.time.ZoneId.of("Asia/Damascus"))
        for(i in 0 until 36) {
            val medication=seed.copy(id=newId(),nameAr="دواء عربي للمراجعة رقم $i — طويل الاسم للاختبار",
                nameEn="Arabic review $i / Amoxicillin & Clavulanate 500 mg + 125 mg",
                genericAr="أموكسيسيلين مع حمض الكلافولانيك",genericEn="Amoxicillin / Clavulanic acid",
                strength="500 mg + 125 mg",note=("ملاحظة عربية ممتدة لاختبار اتصال الحروف واتجاه النص مع English reference 12345. ").repeat(if(i==3) 80 else 2),
                priceMinor=if(i%2==0) 12500 else 1299,currency=if(i%2==0) Currency.SYP else Currency.USD,
                expiryDate=today.plusDays(20))
            profile.admin.saveMedication(medication)
            profile.admin.availability(medication.id,section.id,if(i%2==0) AvailabilityStatus.OUT else AvailabilityStatus.UNKNOWN)
        }
        val reports=ReportRepository(profile.admin,DocumentOutput(profile.context),PdfReports(profile.context))
        val results=buildJsonArray {
            for(preset in ReportPreset.entries) {
                val request=ReportRequest(preset,if(preset==ReportPreset.OUT) section.id else null,true)
                val snapshot=reports.capture(request)
                val file=File(output,"arabic-${preset.name.lowercase()}.pdf")
                val authority=InstrumentationRegistry.getInstrumentation().context.packageName+".suite-documents"
                val uri=Uri.parse("content://$authority/suite-${newId()}")
                try {
                    reports.pdf(uri,snapshot)
                    requireNotNull(base.contentResolver.openInputStream(uri)).use { input -> file.outputStream().use { input.copyTo(it) } }
                } finally { base.contentResolver.delete(uri,null,null) }
                assertTrue(file.length()>1000)
                add(buildJsonObject { put("preset",preset.name);put("medications",snapshot.medications.size);put("bytes",file.length());put("file",file.name) })
            }
        }
        File(output,"pdfs.json").writeText(buildJsonObject { put("exports",results);put("syntheticDataOnly",true) }.toString())
    }
}
