package com.aqaqeer.app

import android.net.Uri
import android.os.Process
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.maintenance.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Same host-owned death protocol as restore; all profiles/remote effects are test-only. */
class AcceptanceProcessDeathInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val base get()=instrumentation.targetContext
    private val args get()=InstrumentationRegistry.getArguments()
    private fun argument(name:String)=requireNotNull(args.getString("crash"+name))
    private fun id(name:String)=argument(name).also { check(it.matches(Regex("[a-f0-9]{32}"))) }
    private val runId get()=id("Run")
    private val caseId get()=id("Case")
    private val root get()=File(base.noBackupFilesDir,"restore-death/$runId").apply { mkdirs();Os.chmod(path,448) }
    private val meta get()=RestoreCrashProfile(base,File(root,"acceptance-meta").apply { mkdirs() },"crash_acceptance_meta_$runId")
    private fun profile(gate:RestoreCrashGate?=null)=RestoreCrashProfile(base,File(root,caseId).apply { mkdirs() },"crash_${runId}_$caseId",gate)
    private fun password()=" exact interruption 😀 backup ".toCharArray()
    private fun store(name:String,value:JsonElement) {
        val bytes=value.toString().toByteArray();try { meta.real.write("$caseId-$name",bytes) } finally { bytes.fill(0) }
    }
    private fun load(name:String):JsonObject {
        val bytes=requireNotNull(meta.real.read("$caseId-$name"));return try { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject } finally { bytes.fill(0) }
    }
    private fun outcome() {
        val f=File(root,"outcome-$caseId-${id("Event")}.json")
        f.writeText(buildJsonObject { put("phase",argument("Phase"));put("passed",true);put("pid",Process.myPid());put("uid",Process.myUid()) }.toString());Os.chmod(f.path,384)
    }
    private fun coordinator(p:RestoreCrashProfile,r:PersistentRecoveryRemote)=MaintenanceCoordinator(MaintenanceStore(p.secure),MaintenanceStage(p.context),
        p.admin,SnapshotReadClient(r,EnvelopeReader(p.root)),r,r,r,p.requirements,p.backups,p.context)
    private suspend fun snapshot(p:RestoreCrashProfile,r:PersistentRecoveryRemote)=buildJsonObject {
        put("tables",p.tables());put("credentials",p.rawCredentials(listOf(requireNotNull(p.ready().catalog.pharmacy).pharmacyId)))
        put("uploads",r.uploads);put("writes",r.writes)
    }
    @Test fun phase():Unit=runBlocking {
        Assume.assumeTrue("Requires the host death orchestrator",args.containsKey("crashRun"))
        check(!BuildConfig.IS_DEMO && BuildConfig.APPLICATION_ID.contains(".verification."))
        when(argument("Phase")) {
            "prepare" -> prepare()
            "interrupt" -> interrupt()
            "verify" -> verify()
            "cleanup" -> profile().deleteOwned()
            "cleanupRun" -> { meta.deleteOwned();root.deleteRecursively() }
            else -> error("Unknown recovery phase")
        }
        outcome()
    }
    private suspend fun prepare() {
        val p=profile();check(!p.context.getDatabasePath(p.factory.databaseName).exists())
        p.create("صيدلية الصيانة");p.medication("Interruption verification only")
        if(argument("Target")=="export") p.admin.transaction { dao ->
            val medication=dao.medications().single()
            dao.insertHistory(listOf(HistoryEntry(newId(),null,"medication",medication.id,medication.nameEn,"note",null,
                JsonPrimitive("Synthetic export interruption ".repeat(6000)).toString(),null,null,null,p.clock.instant(),null)))
        }
        val remote=PersistentRecoveryRemote(p)
        val sharing=PharmacySharing(p.admin,p.secure,p.requirements,SnapshotReadClient(remote,EnvelopeReader(p.root)),remote,remote,p.context)
        sharing.saveSetup(M3Fixtures.token,M3Fixtures.groupId,remote.title);sharing.publish();assertTrue(sharing.status.value.verified)
        p.admin.transaction { dao -> dao.control(requireNotNull(dao.control()).copy(lastBackupAt=p.clock.millis()-30000,backupDismissedAt=p.clock.millis()-10000)) }
        store("before",snapshot(p,remote))
    }
    private suspend fun interrupt() {
        val point=argument("Checkpoint")
        val gate=RestoreCrashGate(File(root,"marker-$caseId-${id("Event")}.json"),point,id("Event"))
        val p=profile(gate)
        if(argument("Target")=="export") {
            p.backups.export(Uri.fromFile(File(p.root,"export.aqbak")),password(),password())
        } else {
            val remote=PersistentRecoveryRemote(p,gate);val flow=coordinator(p,remote)
            flow.begin(MaintenanceKind.ROTATION);flow.prepareSource(false)
            flow.backup(Uri.fromFile(File(p.root,"maintenance.aqbak")),password(),password(),flow.journal().id)
            flow.revocationGuide();flow.rotationToken("123456:replacement_public_test_token")
            flow.publishMaintenance();flow.complete()
        }
    }
    private suspend fun verify() {
        val p=profile();val ready=p.throughViewModel();assertTrue(ready.admin)
        val remote=PersistentRecoveryRemote(p)
        if(argument("Cold")=="second") {
            assertTrue("Repeated cold startup must not change business/protected state or remote counters",load("verified")==snapshot(p,remote))
            return
        }
        val before=load("before")
        if(argument("Target")=="export") {
            val file=File(p.root,"export.aqbak");assertTrue(file.exists() && file.length()>0)
            val point=argument("ScenarioCheckpoint")
            if(point=="none") {
                assertEquals(p.clock.millis(),p.admin.transaction { it.control()!!.lastBackupAt })
                V1BackupEnvelope().read(file.inputStream(),p.root,password()).close()
            } else {
                assertTrue("Interrupted export must preserve all prior business/control/credential state",before==snapshot(p,remote))
                if(point=="export_after_close") V1BackupEnvelope().read(file.inputStream(),p.root,password()).close()
                else {
                    var rejected=false
                    try { V1BackupEnvelope().read(file.inputStream(),p.root,password()).close() } catch(_:EnvelopeException) { rejected=true }
                    assertTrue("Partially copied output cannot authenticate as a successful backup",rejected)
                }
            }
        } else {
            val flow=coordinator(p,remote);val store=MaintenanceStore(p.secure);val journal=store.read()
            val early=setOf(MaintenancePhase.PREPARING,MaintenancePhase.SOURCE_READY,MaintenancePhase.BACKED_UP,MaintenancePhase.TOKEN_CHANGE)
            if(journal!=null && journal.phase in early) {
                assertTrue(ready.maintenancePaused)
                assertEquals(1,ready.catalog.pharmacy!!.keyVersion)
                assertEquals(1,remote.uploads);assertEquals(1,remote.writes)
                if(journal.phase==MaintenancePhase.BACKED_UP) {
                    assertNotNull(journal.receipt)
                    V1BackupEnvelope().read(File(p.root,"maintenance.aqbak").inputStream(),p.root,password()).close()
                }
                flow.cancel()
                assertTrue("Cancellation before possible remote mutation keeps the active connection",before.getValue("credentials")==p.rawCredentials(listOf(requireNotNull(ready.catalog.pharmacy).pharmacyId)))
            } else {
                if(journal!=null) {
                    if(journal.phase!=MaintenancePhase.COMPLETE) flow.publishMaintenance()
                    assertEquals(MaintenancePhase.COMPLETE,flow.journal().phase)
                    flow.complete()
                }
                assertEquals(2,p.ready().catalog.pharmacy!!.keyVersion)
                assertEquals("Retained upload must not be duplicated",2,remote.uploads)
                assertEquals("Verified remote write must not be replayed",2,remote.writes)
                val active=PendingConnectionStore(p.secure,"active-connection")
                active.withCandidate(active.info()!!.id) { candidate ->
                    val read=SnapshotReadClient(remote,EnvelopeReader(p.root)).read(candidate,BuildConfig.VERSION_CODE)
                    assertEquals(2,(read.snapshot as SnapshotCandidate.Supported).catalog.pharmacy!!.keyVersion)
                }
            }
            assertNull(store.read());assertFalse(p.ready().maintenancePaused)
            assertNull(store.connection("old").info());assertNull(store.connection("candidate").info())
            p.assertMaintenanceStageEmpty()
        }
        store("verified",snapshot(p,remote))
    }
}
