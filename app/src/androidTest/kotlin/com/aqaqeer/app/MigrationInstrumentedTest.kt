package com.aqaqeer.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.AqaqeerDatabase
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** The initial v1 contract has no historical upgrade chain or destructive fallback. */
class MigrationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        AqaqeerDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    private val names = mutableListOf<String>()
    @After fun cleanup() { names.forEach { context.deleteDatabase(it) } }

    private fun seededDatabase(version: Int = 1): String {
        val name = "suite-migration-${UUID.randomUUID()}.db"; names += name
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES('retained','محفوظ',NULL,7,0)")
            db.execSQL("""INSERT INTO local_control
                (slot,lifecycle,failedPins,retryPinAt,reconnectPending,leaving,observedPinVerifier,authorizedPinVerifier)
                VALUES(1,'SHARED',3,1234,1,0,'observed','authorized')""")
            // Deliberately unsupported version marker, not a fabricated historical schema.
            if (version != 1) db.version = version
        }
        return name
    }
    private fun open(name: String) = Room.databaseBuilder(context, AqaqeerDatabase::class.java, name)
        .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
        .build()

    private fun assertRetained(db: SupportSQLiteDatabase) {
        db.query("SELECT nameAr,orderIndex FROM categories WHERE id='retained'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("محفوظ", c.getString(0)); assertEquals(7, c.getInt(1))
        }
        db.query("SELECT lifecycle,failedPins,retryPinAt FROM local_control").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("SHARED", c.getString(0))
            assertEquals(3, c.getInt(1)); assertEquals(1234L, c.getLong(2))
        }
    }
    @Test fun initialSchemaReopensPreservingDataAndRestoreDefaults() {
        val name = seededDatabase()
        val room = open(name)
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(1, db.version)
            assertRetained(db)
            db.query("SELECT restorePaused,restoreConnectionConfirmed,restoreActivationId,firstAdminAt,lastBackupAt,backupDismissedAt FROM local_control").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); assertEquals(0, c.getInt(1))
                for (i in 2..5) assertTrue(c.isNull(i))
            }
        } finally { room.close() }
    }
    private fun rejectUnsupportedVersion(version: Int) {
        val name = seededDatabase(version)
        val room = open(name)
        try {
            assertThrows(IllegalStateException::class.java) { room.openHelper.writableDatabase }
        } finally { room.close() }
        // Raw, read-only inspection confirms refusal did not erase or rewrite the fixture.
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,
            null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(version, db.version)
            db.rawQuery("SELECT nameAr,orderIndex FROM categories WHERE id='retained'", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("محفوظ", c.getString(0)); assertEquals(7, c.getInt(1))
            }
            db.rawQuery("SELECT lifecycle,failedPins,retryPinAt FROM local_control", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("SHARED", c.getString(0))
                assertEquals(3, c.getInt(1)); assertEquals(1234L, c.getLong(2))
            }
        }
    }
    @Test fun unsupportedVersionTwoIsRejectedWithoutDeletingData() = rejectUnsupportedVersion(2)
    @Test fun unsupportedVersionThreeIsRejectedWithoutDeletingData() = rejectUnsupportedVersion(3)
}
