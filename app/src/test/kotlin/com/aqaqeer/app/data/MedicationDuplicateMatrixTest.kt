package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Independent name, strength and form equivalence boundaries; no unit conversion is inferred. */
@RunWith(Parameterized::class)
class MedicationDuplicateMatrixTest(private val label: String, private val name: String,
    private val strength: String, private val form: String, private val expected: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            val names = listOf("Alpha" to true, " ALPHA " to true, "Beta" to false, "Alpha plus" to false)
            val strengths = listOf("500mg" to true, "٥٠٠ mg" to true, "۵۰۰ MG" to true,
                " 500\u00a0mg " to true, "0.5 g" to false, "50 mg" to false)
            for ((name, sameName) in names) for ((strength, sameStrength) in strengths)
                for ((form, sameForm) in listOf(CatalogSeeds.OTHER_FORM_ID to true, CatalogSeeds.TABLET_FORM_ID to false))
                    add(arrayOf("$name/$strength/$form", name, strength, form, sameName && sameStrength && sameForm))
        }
    }
    @Test fun identityMatchingIsSymmetricAndIndexAgrees() {
        val a = F.catalog().medications.single().copy(nameEn = null, genericEn = "Alpha", strength = "500 mg")
        val b = a.copy(id = F.otherPharmacyId, genericEn = name, strength = strength, formId = form)
        assertEquals(label, expected, MedicationDuplicates.matches(a, b))
        assertEquals(label, expected, MedicationDuplicates.matches(b, a))
        assertEquals(if (expected) listOf(a.id) else emptyList<String>(), MedicationDuplicates.find(b, MedicationDuplicates.index(listOf(a))))
        assertTrue(MedicationDuplicates.find(a, MedicationDuplicates.index(listOf(a))).isEmpty())
        assertEquals("500 mg", a.strength)
    }
}
