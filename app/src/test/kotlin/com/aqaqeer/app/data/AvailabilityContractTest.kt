package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.reports.*
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AvailabilityContractTest {
    @Test fun enumAndSerializedValuesContainExactlyThreeStates() {
        assertEquals(listOf("AVAILABLE", "LOW", "OUT"), AvailabilityStatus.entries.map { it.name })
        AvailabilityStatus.entries.forEach { value ->
            assertEquals("\"${value.name}\"", Json.encodeToString(value))
            assertEquals(value, Json.decodeFromString<AvailabilityStatus>(Json.encodeToString(value)))
        }
        assertThrows(SerializationException::class.java) { Json.decodeFromString<AvailabilityStatus>("\"UNKNOWN\"") }
    }
    @Test fun missingPairAndDefaultRecordUseOutAndShortageCountsAgree() {
        val catalog = F.catalog(); val med = catalog.medications.single(); val section = catalog.sections.single().id
        val missing = catalog.copy(availability = emptyList())
        assertNull(missing.availability(med.id, section)); assertEquals(AvailabilityStatus.OUT, missing.status(med.id, section))
        assertEquals(1, missing.outCounts[section])
        assertEquals(AvailabilityStatus.OUT, Availability(med.id, section, updatedAt = F.time).status)
        assertEquals(0, catalog.outCounts[section])
    }
    @Test fun readerStillHidesOutIncludingMissingPairs(): Unit = runBlocking {
        val c = F.catalog().copy(availability = emptyList()); val section = c.sections.single().id
        val index = CatalogSearchIndex(c)
        val projection = index.project(section, "", HomeFilters(), HomePreferences(), emptySet(), readerStockOnly = true)
        assertTrue(projection.rows.isEmpty())
    }
    @Test fun csvAcceptsAllSupportedLabelsAndRejectsRemovedState() {
        val catalog = F.catalog(); val section = catalog.sections.single().id
        for (status in AvailabilityStatus.entries) for (label in listOf(status.name.lowercase(), MedicationPresentation.statusLabel(status))) {
            val table = CsvCodec.read("genericEn,strength,section:$section\nNew sample,20 mg,$label".byteInputStream()) {}
            val row = CsvRowPlanner.preview(F.publishId, catalog, table, emptyMap()) {}.rows.single()
            assertNull(label, row.error); assertEquals(status, row.plan!!.statuses[section])
        }
        for (label in listOf("UNKNOWN", "غير معروف")) {
            val table = CsvCodec.read("genericEn,strength,section:$section\nNew sample,20 mg,$label".byteInputStream()) {}
            assertNotNull(CsvRowPlanner.preview(F.publishId, catalog, table, emptyMap()) {}.rows.single().error)
        }
    }
    @Test fun authenticatedSnapshotWithRemovedStateRejectsBeforeApplying(): Unit = runBlocking {
        val snapshot = F.snapshot(); val pair = snapshot.getValue("availability").jsonArray.single().jsonObject
        val invalid = JsonObject(snapshot + ("availability" to JsonArray(listOf(JsonObject(pair + ("status" to JsonPrimitive("UNKNOWN")))))))
        AuthenticatedJson(invalid.toString().toByteArray(), EnvelopeDomain.SNAPSHOT).use { authenticated ->
            assertThrows(PayloadException::class.java) {
                runBlocking { PayloadReader { Long.MAX_VALUE }.snapshot(authenticated, F.pharmacyId, 1, 1) }
            }
        }
    }
}
