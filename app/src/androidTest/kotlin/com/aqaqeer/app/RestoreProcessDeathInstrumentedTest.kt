package com.aqaqeer.app

import android.content.Context
import android.net.Uri
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.backup.backupTables
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.security.KeyStore

/** Host-managed phases: a killed instrumentation invocation is never itself a pass. */
class RestoreProcessDeathInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val base get() = ApplicationProvider.getApplicationContext<Context>()
    private fun argument(name: String) = requireNotNull(args.getString("crash" + name))
    private fun id(name: String) = argument(name).also { require(it.matches(Regex("[a-f0-9]{32}"))) }
    private val runId get() = id("Run")
    private val caseId get() = id("Case")
    private val runRoot get() = File(base.noBackupFilesDir, "restore-death/" + runId).apply {
        mkdirs(); Os.chmod(path, 448)
    }
    private val meta get() = RestoreCrashProfile(base, File(runRoot, "meta").apply { mkdirs() }, "crash_meta_" + runId)
    private fun profile(gate: RestoreCrashGate? = null) = RestoreCrashProfile(base,
        File(runRoot, caseId).apply { mkdirs() }, "crash_" + runId + "_" + caseId, gate)
    private fun password() = " exact synthetic backup 😀 ".toCharArray()
    private fun uri(paused: Boolean = false) = Uri.parse("content://" + instrumentation.context.packageName +
        ".restore-crash/crash-" + caseId + if (paused) "?pause=true" else "")
    private fun store(name: String, value: JsonElement) {
        val bytes = value.toString().toByteArray()
        try { meta.real.write(name, bytes) } finally { bytes.fill(0) }
    }
    private fun load(name: String): JsonObject {
        val bytes = requireNotNull(meta.real.read(name))
        return try { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject } finally { bytes.fill(0) }
    }
    private fun marker() = File(runRoot, "marker-" + caseId + "-" + id("Event") + ".json")
    private fun outcome() {
        val file = File(runRoot, "outcome-" + caseId + "-" + id("Event") + ".json")
        file.writeText(buildJsonObject { put("phase", argument("Phase")); put("passed", true); put("pid", android.os.Process.myPid()); put("uid", android.os.Process.myUid()) }.toString())
        Os.chmod(file.path, 384)
    }

    @Test fun phase(): Unit = runBlocking {
        Assume.assumeTrue("Requires the host process-death orchestrator", args.containsKey("crashRun"))
        check(!BuildConfig.IS_DEMO && BuildConfig.APPLICATION_ID.contains(".verification."))
        when (argument("Phase")) {
            "prepare" -> prepare()
            "interrupt" -> interrupt()
            "verify" -> verify()
            "cleanup" -> cleanup()
            "cleanupRun" -> cleanupRun()
            else -> error("Unknown process-death phase")
        }
        outcome()
    }

    private suspend fun template(): JsonObject {
        val archive = File(runRoot, "template.aqbak")
        if (archive.isFile) return load("template")
        val donor = RestoreCrashProfile(base, File(runRoot, "donor").apply { mkdirs() }, "crash_donor_" + runId)
        donor.create("صيدلية المصدر")
        val med = donor.medication("Restored medicine")
        val remote = SuiteRemote()
        val sharing = donor.sharing(remote)
        sharing.saveSetup(M3Fixtures.token, M3Fixtures.groupId, remote.title)
        sharing.publish(); assertTrue("Synthetic baseline must publish", sharing.status.value.verified)
        donor.admin.saveMedication(med.copy(note = "retained pending edit"))
        remote.loseWriteResponse = true
        sharing.publish()
        assertTrue("Lost write response must be retryable and unverified", sharing.status.value.retryable && !sharing.status.value.verified)
        donor.admin.transaction { dao ->
            assertTrue("Fixture must contain an uncertain attempt", dao.attemptCount() > 0)
            dao.insertHistory((0 until 60).map { i ->
                HistoryEntry(newId(), null, "medication", med.id, "Retained medicine", "note", null,
                    "synthetic recovery history ".repeat(80) + i, null, null, donor.clock.instant(), null, null)
            })
        }
        donor.backups.export(Uri.fromFile(archive), password(), password())
        assertTrue("Input checkpoint needs an archive larger than its prefix", archive.length() > 65536)
        val data = buildJsonObject {
            put("tables", donor.tables())
            put("active", donor.connection("active-connection")); put("setup", donor.connection("setup-connection"))
            put("pharmacyId", donor.ready().catalog.pharmacy!!.pharmacyId)
            put("requirements", donor.rawCredentials(listOf(donor.ready().catalog.pharmacy!!.pharmacyId))
                .filterKeys { it.startsWith("snapshot-requirements-") }.let(::JsonObject))
        }
        store("template", data)
        return data
    }
    private suspend fun prepare() {
        val source = template()
        val target = profile()
        check(!target.context.getDatabasePath(target.factory.databaseName).exists()) { "Scenario must be fresh" }
        target.ready()
        if (argument("Target") == "configured") {
            target.create("صيدلية الهدف")
            val med = target.medication("Original target medicine")
            val remote = SuiteRemote(); val sharing = target.sharing(remote)
            sharing.saveSetup(M3Fixtures.token, M3Fixtures.groupId, remote.title); sharing.publish()
            assertTrue("Original target must be usable", sharing.status.value.verified)
            target.admin.saveMedication(med.copy(note = "original pending edit"))
            target.admin.transaction { dao ->
                dao.control(dao.control()!!.copy(firstAdminAt = target.clock.millis() - 50000,
                    lastBackupAt = target.clock.millis() - 30000, backupDismissedAt = target.clock.millis() - 10000))
            }
        } else check(argument("Target") == "empty")
        val oldId = target.ready().catalog.pharmacy?.pharmacyId
        val ids = listOfNotNull(source.getValue("pharmacyId").jsonPrimitive.content, oldId)
        store("before-" + caseId, buildJsonObject {
            put("tables", target.tables()); put("credentials", target.rawCredentials(ids))
            put("sourceId", source.getValue("pharmacyId"))
        })
        base.contentResolver.openOutputStream(uri(), "wt")!!.use { out ->
            File(runRoot, "template.aqbak").inputStream().use { it.copyTo(out) }
        }
    }
    private suspend fun interrupt() = coroutineScope {
        val checkpoint = argument("Checkpoint")
        val gate = RestoreCrashGate(marker(), checkpoint, id("Event"))
        val target = profile(gate)
        val operation = async(Dispatchers.IO) {
            target.backups.restore(uri(checkpoint == "during_input"), password(),
                if (argument("Target") == "configured") "صيدلية الهدف" else "",
                argument("Target") == "configured")
        }
        if (checkpoint == "during_input") {
            withTimeout(180000) {
                while (true) {
                    if (operation.isCompleted) { operation.await(); error("Restore ended before the input checkpoint") }
                    val paused = base.contentResolver.call(uri(), "paused", "crash-" + caseId, null)?.getBoolean("paused") == true
                    val staged = File(target.root, "aqaqeer-backup-stage").listFiles().orEmpty().any { it.length() > 0 }
                    if (paused && staged) break
                    delay(100)
                }
            }
            gate.hit("during_input")
        }
        operation.await()
        check(checkpoint == "none") { "Requested checkpoint was not reached" }
    }

    private fun sameTables(expected: JsonObject, actual: JsonObject, tables: Iterable<String>) {
        for (table in tables) assertTrue("Retained table differs: " + table, expected[table] == actual[table])
    }
    private suspend fun verify() {
        val checkpoint = argument("Checkpoint")
        val gate = if (checkpoint == "none") null else RestoreCrashGate(marker(), checkpoint, id("Event"))
        val target = profile(gate)
        val before = load("before-" + caseId)
        val source = load("template")
        val restored = argument("Restored") == "true"
        val beforeTables = before.getValue("tables").jsonObject
        val actualBeforeStartup = target.tables()
        target.factory.open().let { db ->
            try {
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE name='backup_cells'").use {
                    assertFalse("Replacement staging must not survive rollback/activation", it.moveToFirst())
                }
            } finally { db.close() }
        }
        sameTables(beforeTables, actualBeforeStartup, listOf("installation_state"))
        if (restored) {
            sameTables(source.getValue("tables").jsonObject, actualBeforeStartup, backupTables.keys)
            val beforeIdentity = beforeTables.getValue("device_identity").jsonArray.single().jsonObject
            val actualIdentity = actualBeforeStartup.getValue("device_identity").jsonArray.single().jsonObject
            assertTrue("Installation identity must be retained with administration revoked",
                actualIdentity == JsonObject(beforeIdentity + ("adminMode" to JsonPrimitive(0))))
        } else sameTables(beforeTables, actualBeforeStartup, beforeTables.keys)

        val state = target.throughViewModel()
        val after = target.tables()
        if (restored) {
            sameTables(source.getValue("tables").jsonObject, after, backupTables.keys)
            assertTrue("Restored view must remain paused and unauthorized", state.restorePaused && !state.admin)
            assertTrue("Restored visible catalog must match", state.catalog.pharmacy!!.pharmacyId == source.getValue("pharmacyId").jsonPrimitive.content)
            target.admin.transaction { dao ->
                assertTrue("Activation must complete idempotently", dao.control() == LocalControl(lifecycle = "SHARED", restorePaused = true))
            }
            assertTrue("Restored active credentials must match", target.connection("active-connection") == source["active"])
            assertTrue("Restored setup credentials must match", target.connection("setup-connection") == source["setup"])
            assertNull("Pending credentials must be cleared", PendingConnectionStore(target.real, "pending-connection").info())
            for ((name, value) in source.getValue("requirements").jsonObject) {
                val bytes = target.real.read(name)
                try { assertTrue("Compatibility requirements must be activated",
                    bytes != null && JsonPrimitive(java.util.Base64.getEncoder().encodeToString(bytes)) == value) }
                finally { bytes?.fill(0) }
            }
            assertFalse("Restored attempts must never reconnect automatically", target.admin.shouldReconnect())
        } else {
            sameTables(beforeTables, after, beforeTables.keys)
            val ids = before.getValue("credentials").jsonObject.keys.filter { it.startsWith("snapshot-requirements-") }
                .map { it.removePrefix("snapshot-requirements-") }
            assertTrue("Pre-commit credentials must remain byte-identical",
                target.rawCredentials(ids) == before.getValue("credentials"))
        }
        assertFalse("Staging database must be removed", target.context.getDatabasePath("aqaqeer-backup_stage.db").exists())
        assertFalse("Staging key wrapper must be removed", File(target.root, "secure-backup_stage").exists())
        assertFalse("Staging Keystore key must be removed", KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .containsAlias(base.packageName + ".aqaqeer.backup_stage.v1"))
        assertTrue("Encrypted staging must be empty", File(target.root, "aqaqeer-backup-stage").listFiles().orEmpty().isEmpty())
        val stable = buildJsonObject { put("tables", after); put("active", target.connection("active-connection"))
            put("setup", target.connection("setup-connection")); put("requirements", target.rawCredentials(
                listOf(before.getValue("sourceId").jsonPrimitive.content)).filterKeys { it.startsWith("snapshot-requirements-") }.let(::JsonObject)) }
        val stableName = "stable-" + caseId
        if (argument("Cold") == "second") assertTrue("Second cold start must make no further changes", load(stableName) == stable)
        else store(stableName, stable)
    }
    private fun cleanup() {
        base.contentResolver.delete(uri(), null, null)
        profile().deleteOwned()
    }
    private fun cleanupRun() {
        RestoreCrashProfile(base, File(runRoot, "donor"), "crash_donor_" + runId).deleteOwned()
        meta.deleteOwned()
    }
}
