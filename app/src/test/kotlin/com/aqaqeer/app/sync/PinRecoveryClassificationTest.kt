package com.aqaqeer.app.sync

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PinRecoveryClassificationTest {
    private fun group(sender: Long, document: Boolean) = SetupGroup(-100, "Public fixture", buildJsonObject {
        put("from", buildJsonObject { put("id", sender); put("is_bot", sender == 123L) })
        if (document) put("document", buildJsonObject { put("file_id", "fixture") })
    }.toString())

    @Test fun humanAndOtherBotPinsCanBePositivelyIdentified() {
        assertTrue(group(456, false).hasUnrelatedPin(123))
        assertTrue(group(789, true).hasUnrelatedPin(123))
    }
    @Test fun damagedBotOwnedDocumentNeverQualifiesAsUnrelated() {
        assertFalse(group(123, true).hasUnrelatedPin(123))
        assertTrue(group(123, false).hasUnrelatedPin(123))
    }
    @Test fun absentMalformedOrUnidentifiedPinCannotEnableForcedRecovery() {
        for (pin in listOf(null, "unrelated", "{}", "{\"from\":{}}", "{\"from\":{\"id\":0}}"))
            assertFalse(SetupGroup(-100, "Public fixture", pin).hasUnrelatedPin(123))
    }
}
