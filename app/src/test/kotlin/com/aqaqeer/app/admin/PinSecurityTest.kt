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
        listOf("", "123", " 1234", "1234 ", "12a456").forEach {
            assertThrows(AdminProblem::class.java) { PinSecurity.create(it.toCharArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PinSecurity.matches("123456".toCharArray(), PinVerifier("AA==", 1, "AA=="))
        }
    }
    @Test fun fourDigitMinimumRoundTripsAcrossDigitScripts() {
        val verifier = PinSecurity.create("١٩٠٧".toCharArray())
        assertTrue(PinSecurity.matches("1907".toCharArray(), verifier))
        assertTrue(PinSecurity.matches("۱۹۰۷".toCharArray(), verifier))
        assertFalse(PinSecurity.matches("1908".toCharArray(), verifier))
    }
    @Test fun longerNumericCredentialsRemainValid() {
        val input = "190728460513".toCharArray(); val before = input.copyOf()
        val verifier = PinSecurity.create(input)
        assertTrue(PinSecurity.matches(input, verifier)); assertArrayEquals(before, input)
    }
    @Test fun mixedScriptsNormalizeWithoutDroppingLeadingZeroes() {
        assertArrayEquals("0123456789".toCharArray(), PinSecurity.digits("٠1۲٣4۵٦7۸٩".toCharArray()))
        val verifier = PinSecurity.create("0019".toCharArray())
        assertTrue(PinSecurity.matches("٠٠١٩".toCharArray(), verifier))
        assertFalse(PinSecurity.matches("0190".toCharArray(), verifier))
    }
    @Test fun malformedVerifierEncodingFailsInsteadOfAcceptingCredential() {
        assertThrows(IllegalArgumentException::class.java) {
            PinSecurity.matches("1907".toCharArray(), PinVerifier("not base64!", 200000, "AA=="))
        }
    }
    @Test fun rejectedInputIsNotModifiedByValidation() {
        val input = "19x7".toCharArray(); val before = input.copyOf()
        assertThrows(AdminProblem::class.java) { PinSecurity.create(input) }
        assertArrayEquals(before, input)
    }
}
