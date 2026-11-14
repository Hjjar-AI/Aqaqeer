package com.aqaqeer.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Creates genuine historical schemas, rather than relabelling a current database. */
class MigrationInstrumentedTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        AqaqeerDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())
    private val names = mutableListOf<String>()
    @After fun cleanup() { names.forEach { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(it) } }
    private fun migrate(from: Int, to: Int) {
        val name = "suite-migration-${UUID.randomUUID()}.db"; names += name
        helper.createDatabase(name, from).use { db ->
            db.execSQL("INSERT INTO categories VALUES('retained','محفوظ',NULL,7,0)")
            if (from == 2) db.execSQL("INSERT INTO local_control VALUES(1,'SHARED',3,1234,1,0,'observed','authorized')")
        }
        helper.runMigrationsAndValidate(name, to, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
            db.query("SELECT nameAr,orderIndex FROM categories WHERE id='retained'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("محفوظ", c.getString(0)); assertEquals(7, c.getInt(1))
            }
            db.query("SELECT lifecycle,failedPins,retryPinAt FROM local_control").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(if (from == 1) "UNCONFIGURED" else "SHARED", c.getString(0))
                assertEquals(if (from == 1) 0 else 3, c.getInt(1)); assertEquals(if (from == 1) 0L else 1234L, c.getLong(2))
            }
            if (to == 3) db.query("SELECT restorePaused,restoreConnectionConfirmed,restoreActivationId,firstAdminAt,lastBackupAt,backupDismissedAt FROM local_control").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); assertEquals(0, c.getInt(1))
                for (i in 2..5) assertTrue(c.isNull(i))
            }
        }
    }
    @Test fun versionOneToTwoPreservesData() = migrate(1,2)
    @Test fun versionTwoToThreePreservesRetryState() = migrate(2,3)
    @Test fun versionOneToThreeValidatesEntireChain() = migrate(1,3)
}
