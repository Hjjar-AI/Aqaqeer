package com.aqaqeer.app.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.files.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.Writer

/** Stable v1 table/column allowlist, deliberately independent of future Room schema additions. */
internal val backupTables = linkedMapOf(
    "pharmacy" to "pharmacyId,name,timezone,adminPinVerifier,schemaVersion,minVersionCode,keyVersion",
    "categories" to "id,nameAr,nameEn,orderIndex,isSystem",
    "forms" to "id,nameAr,nameEn,orderIndex,isSystem",
    "sections" to "id,nameAr,nameEn,orderIndex,isActive",
    "medications" to "id,nameEn,strength,formId,categoryId,createdAt,updatedAt,updatedBy,nameAr,genericAr,genericEn,brand,note,expiryDate,expirySoonThresholdDays",
    "availability" to "medicationId,sectionId,status,updatedAt,updatedBy",
    "tombstones" to "entityType,entityId,deletedAt,operationId",
    "history" to "id,operationId,entityType,entityId,entityName,field,oldValue,newValue,editorId,editorName,editedAt,observedAt,publishedRevision",
    "snapshot_state" to "slot,publishedAt,confirmedPayloadJson,revision,publishId,pinnedMessageId,documentFileId,documentFileUniqueId,lastSuccessfulSyncAt,requiredSchemaVersion,requiredMinVersionCode",
    "pending_operations" to "id,transactionId,entityType,targetId,field,baseValueJson,intendedValueJson,authorId,authorName,authoredAt,basePublishId",
    "publish_attempts" to "publishId,basePayloadJson,candidatePayloadJson,includedOperationIdsJson,transportPhase,uploadedFileId,uploadedMessageId",
).mapValues { it.value.split(',') }

@Serializable
internal data class BackupHeader(val schemaVersion: Int = 1, val lifecycle: String,
    val active: ConnectionWire? = null, val setup: ConnectionWire? = null,
    val requiredSchemaVersion: Int = 1, val requiredMinVersionCode: Int = 1)

/** JSON cell chunks avoid CursorWindow limits even for retained large recovery candidates. */
internal object BackupArchive {
    private val json = Json { encodeDefaults = true }
    fun capture(db: SupportSQLiteDatabase, header: BackupHeader, out: Writer, check: () -> Unit) {
        out.write("{\"header\":${json.encodeToString(header)},\"tables\":{")
        backupTables.entries.forEachIndexed { tableIndex, (table, columns) ->
            if (tableIndex > 0) out.write(",")
            out.write("\"$table\":[")
            var previous = Long.MIN_VALUE; var first = true
            val expressions = columns.joinToString(",") { column ->
                "CASE WHEN length(CAST(`$column` AS BLOB))<=8192 THEN CAST(`$column` AS BLOB) END, length(CAST(`$column` AS BLOB))"
            }
            while (true) {
                check()
                val row = db.query("SELECT rowid,$expressions FROM `$table` WHERE rowid > ? ORDER BY rowid LIMIT 1", arrayOf<Any?>(previous)).use { cursor ->
                    if (!cursor.moveToFirst()) null else Triple(cursor.getLong(0), columns.indices.map { c -> if (cursor.isNull(c * 2 + 1)) null else cursor.getBlob(c * 2 + 1) },
                        columns.indices.map { c -> if (cursor.isNull(c * 2 + 2)) null else cursor.getLong(c * 2 + 2) })
                } ?: break
                if (!first) out.write(","); first = false; out.write("[")
                columns.forEachIndexed { index, column ->
                    if (index > 0) out.write(",")
                    val length = row.third[index]
                    if (length == null) out.write("null") else {
                        out.write("[")
                        val small = row.second[index]
                        if (small != null) out.write(JsonPrimitive(java.util.Base64.getEncoder().encodeToString(small)).toString())
                        else {
                            var offset = 1L
                            while (offset <= length) {
                                check()
                                val value = db.query("SELECT substr(CAST(`$column` AS BLOB),?,8192) FROM `$table` WHERE rowid=?", arrayOf<Any?>(offset, row.first)).use { it.moveToFirst(); it.getBlob(0) }
                                if (offset > 1) out.write(",")
                                out.write(JsonPrimitive(java.util.Base64.getEncoder().encodeToString(value)).toString()); offset += value.size
                            }
                        }
                        out.write("]")
                    }
                }
                out.write("]"); previous = row.first
            }
            out.write("]")
        }
        out.write("}}")
    }

    fun parse(spool: ProtectedSpool, db: SupportSQLiteDatabase, check: () -> Unit): BackupHeader {
        db.execSQL("CREATE TABLE backup_cells(t TEXT NOT NULL,r INTEGER NOT NULL,c INTEGER NOT NULL,n INTEGER NOT NULL,v BLOB,PRIMARY KEY(t,r,c,n))")
        var header: BackupHeader? = null; var tablesSeen = false
        spool.input().use { input -> V1BackupEnvelope.strict(input).use { reader ->
            val json = StrictJson(reader, check)
            json.objectMembers { key -> when (key) {
                "header" -> {
                    var schema: Int? = null; var lifecycle: String? = null; var active: ConnectionWire? = null; var setup: ConnectionWire? = null
                    var requiredSchema = 1; var requiredMinimum = 1
                    json.objectMembers { name -> when (name) {
                        "schemaVersion" -> schema = json.number().toInt()
                        "lifecycle" -> lifecycle = json.string(32)
                        "active", "setup" -> {
                            val wire = if (json.takeNull()) null else readConnection(json)
                            if (name == "active") active = wire else setup = wire
                        }
                        "requiredSchemaVersion" -> requiredSchema = json.number().toInt()
                        "requiredMinVersionCode" -> requiredMinimum = json.number().toInt()
                        else -> invalid()
                    } }
                    if (schema != 1) throw EnvelopeException(EnvelopeError.UNSUPPORTED_FORMAT)
                    header = BackupHeader(schema, lifecycle ?: invalid(), active, setup, requiredSchema, requiredMinimum)
                }
                "tables" -> {
                    tablesSeen = true; val seen = mutableSetOf<String>()
                    json.objectMembers { table ->
                        val columns = backupTables[table] ?: invalid(); seen.add(table)
                        json.expect('['); var row = 0L
                        if (!json.consume(']')) { do {
                            check(); json.expect('[')
                            columns.indices.forEach { column ->
                                if (column > 0) json.expect(',')
                                if (json.takeNull()) db.execSQL("INSERT INTO backup_cells VALUES(?,?,?,?,NULL)", arrayOf<Any?>(table, row, column, 0))
                                else {
                                    json.expect('['); var chunk = 0
                                    if (!json.consume(']')) { do {
                                        val encoded = json.string(10924)
                                        val value = try { java.util.Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { invalid() }
                                        if (value.size > 8192 || java.util.Base64.getEncoder().encodeToString(value) != encoded) invalid()
                                        db.execSQL("INSERT INTO backup_cells VALUES(?,?,?,?,?)", arrayOf<Any?>(table, row, column, chunk++, value))
                                    } while (json.consume(',')); json.expect(']') }
                                    if (chunk == 0) db.execSQL("INSERT INTO backup_cells VALUES(?,?,?,?,?)", arrayOf<Any?>(table, row, column, 0, byteArrayOf()))
                                }
                            }
                            json.expect(']'); row++
                        } while (json.consume(',')); json.expect(']') }
                    }
                    if (seen != backupTables.keys) invalid()
                }
                else -> invalid()
            } }; json.end()
        } }
        if (!tablesSeen) invalid()
        return header ?: invalid()
    }

    private fun readConnection(json: StrictJson): ConnectionWire {
        val fields = mutableMapOf<String, String>(); var createdBy: EditorRef? = null
        json.objectMembers { name -> when (name) {
            "schemaVersion", "groupId", "keyVersion" -> fields[name] = json.number()
            "pharmacyId", "pharmacyName", "botToken", "snapshotKey", "createdAt" -> fields[name] = json.string(8192)
            "createdBy" -> { val editor = mutableMapOf<String, String>(); json.objectMembers { k -> if (k !in setOf("id", "name")) invalid(); editor[k] = json.string(8192) }; createdBy = EditorRef(editor["id"] ?: invalid(), editor["name"] ?: invalid()) }
            else -> invalid()
        } }
        fun f(name: String) = fields[name] ?: invalid()
        return ConnectionWire(fields["schemaVersion"]?.toInt() ?: 1, f("pharmacyId"), f("pharmacyName"), f("botToken"), f("groupId").toLong(), f("snapshotKey"), f("keyVersion").toInt(), java.time.Instant.parse(f("createdAt")), createdBy ?: invalid())
    }

    private class CellInput(val db: SupportSQLiteDatabase, val table: String, val row: Long, val column: Int) : java.io.InputStream() {
        var next = 0; var buffer = byteArrayOf(); var offset = 0; var ended = false
        override fun read(): Int { val one = ByteArray(1); return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255 }
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (offset == buffer.size) {
                buffer.fill(0)
                if (ended) return -1
                buffer = db.query("SELECT v FROM backup_cells WHERE t=? AND r=? AND c=? AND n=?", arrayOf<Any?>(table, row, column, next++)).use {
                    if (!it.moveToFirst() || it.isNull(0)) { ended = true; byteArrayOf() } else it.getBlob(0)
                }
                offset = 0
            }
            val n = minOf(len, buffer.size - offset); buffer.copyInto(bytes, off, offset, offset + n); offset += n; return n
        }
        override fun close() { buffer.fill(0) }
    }

    /** Reconstruct entirely inside SQLite; no full large TEXT value crosses an Android cursor. */
    fun materialize(db: SupportSQLiteDatabase, check: () -> Unit, validateUtf8: Boolean = true) {
        backupTables.forEach { (table, columns) ->
            var previous = -1L
            while (true) {
                check()
                val row = db.query("SELECT r FROM backup_cells WHERE t=? AND r>? ORDER BY r LIMIT 1", arrayOf<Any?>(table, previous)).use { if (it.moveToFirst()) it.getLong(0) else null } ?: break
                if (validateUtf8) columns.indices.forEach { c ->
                    CellInput(db, table, row, c).use { input -> V1BackupEnvelope.strict(input).use { reader ->
                        val buffer = CharArray(8192)
                        while (true) { check(); if (reader.read(buffer) < 0) break }
                        buffer.fill('\u0000')
                    } }
                }
                val values = columns.indices.joinToString(",") { c -> "(SELECT group_concat(v,'') FROM (SELECT v FROM backup_cells WHERE t='$table' AND r=$row AND c=$c ORDER BY n))" }
                db.execSQL("INSERT INTO `$table` (${columns.joinToString(",") { "`$it`" }}) SELECT $values")
                previous = row
            }
        }
    }
}
