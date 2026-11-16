package com.aqaqeer.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqaqeer.app.data.FetchedChanges
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Uses only owned preference files; does not touch the installation's real marker/session state. */
@RunWith(AndroidJUnit4::class)
class FetchedChangesInstrumentedTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "fetched_test_${UUID.randomUUID()}_"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(namespace + name, mode)
    }
    @Before fun openOwnedSession() { FetchedChanges.finishTask(); FetchedChanges.beginSession(context) }
    @After fun removeOwnedPreferences() {
        context.getSharedPreferences("fetched_changes", Context.MODE_PRIVATE).edit().clear().commit()
        FetchedChanges.finishTask(); FetchedChanges.beginSession(context); FetchedChanges.finishTask()
        base.deleteSharedPreferences(namespace + "fetched_changes")
    }
    private fun markers() = FetchedChanges.ids.value[F.pharmacyId].orEmpty()
    private fun nextSession() { FetchedChanges.finishTask(); FetchedChanges.beginSession(context) }

    @Test fun accumulatedMarkersSurviveNextSessionAndRetireBeforeThird() {
        FetchedChanges.record(context, F.pharmacyId, setOf("first"))
        FetchedChanges.record(context, F.pharmacyId, setOf("second"))
        assertEquals(setOf("first", "second"), markers())
        nextSession(); assertEquals(setOf("first", "second"), markers())
        nextSession(); assertTrue(markers().isEmpty())
    }
    @Test fun changingAgainRenewsOnlyThatMedicationLifetime() {
        FetchedChanges.record(context, F.pharmacyId, setOf("first", "second"))
        nextSession(); FetchedChanges.record(context, F.pharmacyId, setOf("second"))
        nextSession(); assertEquals(setOf("second"), markers())
        nextSession(); assertTrue(markers().isEmpty())
    }
    @Test fun repeatedActivityStartAndNoChangeDoNotAdvanceOrClearSession() {
        FetchedChanges.record(context, F.pharmacyId, setOf("retained"))
        repeat(3) { FetchedChanges.beginSession(context); FetchedChanges.record(context, F.pharmacyId, emptySet()) }
        assertEquals(setOf("retained"), markers())
        nextSession(); assertEquals(setOf("retained"), markers())
        nextSession(); assertTrue(markers().isEmpty())
    }
    @Test fun pharmacyMarkersRemainIndependentAndReopenFromPersistedState() {
        FetchedChanges.record(context, F.pharmacyId, setOf("first"))
        FetchedChanges.record(context, F.otherPharmacyId, setOf("other"))
        nextSession()
        assertEquals(setOf("first"), markers())
        assertEquals(setOf("other"), FetchedChanges.ids.value[F.otherPharmacyId])
        val persisted = context.getSharedPreferences("fetched_changes", Context.MODE_PRIVATE).getStringSet("markers", emptySet()).orEmpty()
        assertEquals(2, persisted.size)
    }
}
