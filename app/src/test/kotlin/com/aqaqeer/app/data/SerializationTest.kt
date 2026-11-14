package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SerializationTest {
    @Test fun catalogRoundTripPreservesUnicodeDatesInstantsAndOptionalFields() {
        val c = F.catalog(); val m = c.medications.single().copy(nameAr = "دواء 😀", expiryDate = LocalDate.of(2024,2,29),
            note = "line\nquote\"", priceMinor = 1234L, currency = Currency.USD)
        val value = c.copy(medications = listOf(m))
        assertEquals(value, Json.decodeFromString<CatalogSnapshot>(Json.encodeToString(value)))
    }
    @Test fun roomConvertersPreserveNullsAndRejectInvalidDatesAndEnums() {
        val converter = DbConverters()
        assertNull(converter.instantToLong(null)); assertNull(converter.longToInstant(null))
        assertNull(converter.dateToString(null)); assertNull(converter.stringToDate(null))
        assertEquals(F.time, converter.longToInstant(converter.instantToLong(F.time)))
        assertEquals(LocalDate.of(2024,2,29),converter.stringToDate("2024-02-29"))
        assertEquals(F.editor,converter.stringToEditor(converter.editorToString(F.editor)))
        val pin = F.catalog().pharmacy!!.adminPinVerifier
        assertEquals(pin,converter.stringToVerifier(converter.verifierToString(pin)))
        assertThrows(Exception::class.java) { converter.stringToDate("2026-02-30") }
        assertThrows(Exception::class.java) { Json.decodeFromString<AvailabilityStatus>("\"INVALID\"") }
    }
}
