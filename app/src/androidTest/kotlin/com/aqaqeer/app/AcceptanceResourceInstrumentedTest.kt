package com.aqaqeer.app

import android.app.ActivityManager
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.*
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Controlled pressure is confined to this isolated process, never system-wide pressure. */
class AcceptanceResourceInstrumentedTest {
    private val profiles = mutableListOf<SuiteProfile>()
    private val password get() = " exact resource 😀 backup ".toCharArray()
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun isolatedStandardOnly() {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        check(BuildConfig.APPLICATION_ID.contains(".verification."))
    }
    @After fun cleanup() { profiles.asReversed().forEach { it.close() } }
    private suspend fun profile() = SuiteProfile().also { profiles += it; it.create() }
    private suspend fun seed(p: SuiteProfile, rows: Int) {
        val med = p.medication("Resource measurement only")
        p.admin.transaction { dao ->
            dao.insertHistory((0 until rows).map { index ->
                val value = "x".repeat(65_500) + " | سجل 😀 $index"
                HistoryEntry(newId(),null,"medication",med.id,med.nameEn,"note",null,
                    JsonPrimitive(value).toString(),null,null,null,p.clock.instant(),null)
            })
        }
    }
    private suspend fun digest(p: SuiteProfile): String = withContext(Dispatchers.IO) {
        val hash=MessageDigest.getInstance("SHA-256");val db=p.factory.open()
        try {
            db.openHelper.readableDatabase.query("SELECT id,newValue FROM history WHERE entityType='medication' AND field='note' ORDER BY id").use { c ->
                while(c.moveToNext()) { hash.update(c.getString(0).toByteArray());hash.update(c.getString(1).toByteArray()) }
            }
        } finally { db.close() }
        // Only synthetic medication-note rows are compared; no credentials/verifiers are hashed.
        hash.digest().joinToString("") { "%02x".format(it) }
    }
    private fun report(name: String, result: JsonObject) {
        val id=InstrumentationRegistry.getArguments().getString("acceptanceRun") ?: error("Host run ID required")
        check(id.matches(Regex("[a-f0-9]{32}")))
        val root=File(base.noBackupFilesDir,"acceptance-$id").apply { mkdirs() }
        File(root,"$name.json").writeText(result.toString())
    }
    @Test fun measuredHistoryRoundTripsBelowBackupCap(): Unit = runBlocking {
        val measurements=mutableListOf<JsonElement>()
        for(rows in listOf(180,480)) {
            val donor=profile();seed(donor,rows)
            val count=donor.admin.transaction { it.historyCount() };val expected=digest(donor)
            val file=File(donor.root,"measured-$rows.aqbak")
            System.gc();delay(200)
            val sampler=MemorySampler(base)
            try {
                donor.backups.export(Uri.fromFile(file),password,password)
                assertTrue("Fixture must exceed snapshot cap",file.length()>10L*1024*1024)
                assertTrue("Complete exported envelope must fit the backup reader",file.length()<=V1BackupEnvelope.LIMIT)
                val restored=SuiteProfile().also { profiles+=it;it.ready() }
                restored.backups.restore(Uri.fromFile(file),password,"",false)
                assertEquals(count,restored.admin.transaction { it.historyCount() })
                assertEquals(expected,digest(restored))
                assertFalse(restored.ready().admin);assertTrue(restored.ready().restorePaused)
                measurements+=buildJsonObject { put("historyRows",rows);put("envelopeBytes",file.length());put("memory",sampler.finish()) }
            } finally { sampler.close() }
        }
        report("roundtrips",buildJsonObject { put("measurements",JsonArray(measurements));put("historyPreserved",true) })
    }
    @Test fun controlledHeapPressureRejectsBeforeReplacementAndRecoversAfterRelease(): Unit = runBlocking {
        val donor=profile();seed(donor,180)
        val file=File(donor.root,"pressure.aqbak");donor.backups.export(Uri.fromFile(file),password,password)
        val target=profile();target.medication("Existing data must survive")
        val before=target.admin.transaction { dao ->
            listOf(dao.catalog(),dao.pending(),dao.attempts(),dao.historyCount(),dao.deviceIdentity(),dao.control(),dao.snapshotState())
        }
        Runtime.getRuntime().gc();delay(250)
        val sampler=MemorySampler(base)
        val observation=AtomicReference<Pair<Long,Boolean>>()
        val failure=AtomicReference<Throwable?>()
        val releasedAllocations=mutableListOf<WeakReference<ByteArray>>()
        // Exit the allocation stack entirely before measuring released headroom. A
        // suspended coroutine can keep compiler-generated allocation locals reachable.
        val worker=Thread({
            val pressure=mutableListOf<ByteArray>()
            try {
                val runtime=Runtime.getRuntime();val chunk=8192
                fun headroom()=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
                while(headroom()>48L*1024*1024) {
                    val block=ByteArray(chunk)
                    for(i in block.indices step 4096) block[i]=1
                    pressure+=block
                    releasedAllocations+=WeakReference(block)
                }
                val headroom=headroom();assertTrue(headroom<56L*1024*1024)
                var rejected=false
                try {
                    runBlocking { target.backups.restore(Uri.fromFile(file),password,"صيدلية الاختبار",true) }
                    fail("Actual low-headroom restore must fail before replacing existing data")
                } catch(error:EnvelopeException) {
                    assertEquals(EnvelopeError.RESOURCE_LIMIT,error.error);rejected=true
                }
                observation.set(headroom to rejected)
            } catch(error:Throwable) { failure.set(error) }
            finally { pressure.clear() }
        },"acceptance-controlled-pressure")
        worker.start();worker.join(120000)
        assertFalse("Controlled-pressure worker must finish",worker.isAlive)
        failure.get()?.let { throw it }
        val (freeAtAttempt,rejected)=requireNotNull(observation.get())
        Runtime.getRuntime().gc();delay(250);sampler.close()
        assertTrue(rejected)
        val after=target.admin.transaction { dao ->
            listOf(dao.catalog(),dao.pending(),dao.attempts(),dao.historyCount(),dao.deviceIdentity(),dao.control(),dao.snapshotState())
        }
        assertTrue("Low-headroom rejection must preserve catalog/work/identity/authorization/reminders",before==after)
        // ART may process an explicit GC request asynchronously. Establish that the
        // controlled pressure has actually gone before asserting successful recovery.
        val runtime=Runtime.getRuntime()
        fun releasedHeadroom()=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())
        report("pressure-before-retry",buildJsonObject {
            put("resourceLimitObserved",rejected);put("existingStatePreserved",before==after)
            put("headroomAfterClearBytes",releasedHeadroom());put("allocationsStillReachable",releasedAllocations.count { it.get()!=null });put("memory",sampler.finish())
        })
        withTimeout(15000) {
            while(releasedHeadroom()<runtime.maxMemory()*3/4) { runtime.gc();delay(250) }
        }
        assertTrue("All controlled allocations must be collectible",releasedAllocations.all { it.get()==null })
        val retryHeadroom=releasedHeadroom()
        target.backups.restore(Uri.fromFile(file),password,"صيدلية الاختبار",true)
        assertEquals(digest(donor),digest(target));assertTrue(target.ready().restorePaused)
        report("pressure",buildJsonObject {
            put("envelopeBytes",file.length());put("headroomAtAttemptBytes",freeAtAttempt)
            put("resourceLimitObserved",rejected);put("existingStatePreserved",before==after)
            put("headroomBeforeRetryBytes",retryHeadroom);put("retryAfterReleaseSucceeded",true);put("memory",sampler.finish())
            put("mechanism","controlled in-process retained heap allocations; not OS low-memory termination")
        })
    }
}

private class MemorySampler(context: android.content.Context): AutoCloseable {
    private val running=AtomicBoolean(true)
    private val started=SystemClock.elapsedRealtime()
    private var ended=0L
    private val manager=context.getSystemService(ActivityManager::class.java)
    private var peakJava=0L;private var peakNative=0L;private var peakPss=0;private var samples=0
    private var minimumSystemAvailable=Long.MAX_VALUE;private var systemLowMemory=false
    private val thread=Thread({
        while(running.get()) {
            val r=Runtime.getRuntime();val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
            val system=ActivityManager.MemoryInfo();manager.getMemoryInfo(system)
            synchronized(this) {
                peakJava=maxOf(peakJava,r.totalMemory()-r.freeMemory());peakNative=maxOf(peakNative,Debug.getNativeHeapAllocatedSize())
                peakPss=maxOf(peakPss,info.totalPss);minimumSystemAvailable=minOf(minimumSystemAvailable,system.availMem)
                systemLowMemory=systemLowMemory||system.lowMemory;samples++
            }
            try { Thread.sleep(100) } catch(_:InterruptedException) { break }
        }
    },"acceptance-memory-sampler").apply { isDaemon=true;start() }
    override fun close() { if(running.getAndSet(false)) { thread.interrupt();thread.join(2000);ended=SystemClock.elapsedRealtime() } }
    fun finish(): JsonObject { close();return synchronized(this) { buildJsonObject {
        put("elapsedMs",ended-started);put("samples",samples)
        put("peakJavaUsedBytes",peakJava);put("peakNativeAllocatedBytes",peakNative);put("peakPssKiB",peakPss)
        put("maxHeapBytes",Runtime.getRuntime().maxMemory());put("minimumSystemAvailableBytes",minimumSystemAvailable)
        put("systemLowMemoryFlagObserved",systemLowMemory)
    } } }
}
