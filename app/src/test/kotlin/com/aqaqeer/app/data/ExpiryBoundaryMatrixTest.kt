package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate

@RunWith(Parameterized::class)
class ExpiryBoundaryMatrixTest(private val label: String, private val today: LocalDate,
    private val threshold: Int, private val delta: Long, private val expected: ExpiryState?) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> = buildList {
            for (today in listOf(LocalDate.of(2024, 2, 28), LocalDate.of(2026, 12, 31)))
                for (threshold in listOf(0, 1, 60))
                    for (delta in listOf(-2L, -1L, 0L, 1L, 59L, 60L, 61L)) {
                        // Explicit truth table avoids deriving expectations from the production calculation.
                        val expected = if (delta < 0) ExpiryState.EXPIRED else when (threshold) {
                            0 -> if (delta == 0L) ExpiryState.SOON else null
                            1 -> if (delta in 0L..1L) ExpiryState.SOON else null
                            else -> if (delta in 0L..60L) ExpiryState.SOON else null
                        }
                        add(arrayOf("$today/threshold=$threshold/delta=$delta", today, threshold, delta, expected))
                    }
        }
    }
    @Test fun perMedicationBoundaryHandlesLeapDaysAndYearRollover() {
        val med = F.catalog().medications.single().copy(expiryDate = today.plusDays(delta), expirySoonThresholdDays = threshold)
        assertEquals(label, expected, MedicationPresentation.expiry(med, today))
        assertNull(MedicationPresentation.expiry(med.copy(expiryDate = null), today))
        assertEquals(today.plusDays(delta), med.expiryDate)
    }
}
