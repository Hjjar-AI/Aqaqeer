package com.aqaqeer.app.admin

import com.aqaqeer.app.data.PinVerifier
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class PinSecurityTest {
    @Test fun arabicAndPersianDigitsMatchWithoutMutatingCallerInput() {
        val input = "١٢٣٤٥٦".toCharArray(); val verifier = PinSecurity.create(input)
        assertTrue(PinSecurity.matches("۱۲۳۴۵۶".toCharArray(), verifier))
        assertTrue(PinSecurity.matches("123456".toCharArray(), verifier))
        assertFalse(PinSecurity.matches("123450".toCharArray(), verifier))
        assertEquals("١٢٣٤٥٦", String(input)); assertEquals(200000, verifier.iterations)
        assertEquals(16, Base64.getDecoder().decode(verifier.salt).size)
        assertEquals(32, Base64.getDecoder().decode(verifier.hash).size)
        assertNotEquals(verifier.salt, PinSecurity.create(input).salt)
    }
    @Test fun invalidDigitsLengthsAndIterationCountsReject() {
        listOf("", "12345", "1234567", " 12345", "12a456").forEach {
            assertThrows(IllegalArgumentException::class.java) { PinSecurity.create(it.toCharArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PinSecurity.matches("123456".toCharArray(), PinVerifier("AA==", 1, "AA=="))
        }
    }
}
