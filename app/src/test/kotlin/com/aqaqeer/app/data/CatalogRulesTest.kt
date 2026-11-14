package com.aqaqeer.app.data

import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.security.DatabaseKeyProvider
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CatalogRulesTest {
    private val seed = CatalogSeeds.unconfigured()
    private val editor = EditorRef("00000000-0000-4000-8000-000000000900", "اختبار")
    private fun medication() = Medication("00000000-0000-4000-8000-000000000999",
        "Example", "10 mg", CatalogSeeds.OTHER_FORM_ID, CatalogSeeds.UNCATEGORIZED_ID,
        Instant.EPOCH, Instant.EPOCH, editor)

    @Test fun seedsContainOnlyOneProtectedCategoryAndAllFifteenForms() {
        CatalogRules.validate(seed)
        assertEquals(1, seed.categories.size)
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, seed.categories.single().id)
        assertEquals(15, seed.forms.size)
        assertEquals(15, seed.forms.map { it.id }.distinct().size)
        assertEquals("Other", seed.forms.single { it.isSystem }.nameEn)
        assertEquals(CatalogSeeds.OTHER_FORM_ID, seed.forms.single { it.isSystem }.id)
        assertEquals(1, seed.sections.size)
    }
    @Test fun missingAvailabilityIsUnknownAndIsNotOut() {
        assertEquals(AvailabilityStatus.UNKNOWN, seed.status(medication().id, seed.sections.single().id))
        assertNotEquals(AvailabilityStatus.OUT, seed.status(medication().id, seed.sections.single().id))
    }
    @Test fun englishOnlyMedicationIsValidWithoutInventedArabicName() {
        CatalogRules.validate(seed.copy(medications = listOf(medication())))
        assertNull(medication().nameAr)
    }
    @Test fun requiredEnglishAndStrengthCannotBeBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(medications = listOf(medication().copy(nameEn = " "))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(medications = listOf(medication().copy(strength = ""))))
        }
    }
    @Test fun danglingMedicationAndAvailabilityReferencesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(medications = listOf(medication().copy(categoryId = "missing"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(availability = listOf(Availability(medication().id,
                seed.sections.single().id, updatedAt = Instant.EPOCH, updatedBy = editor))))
        }
    }
    @Test fun duplicateAvailabilityPairIsRejected() {
        val pair = Availability(medication().id, seed.sections.single().id,
            updatedAt = Instant.EPOCH, updatedBy = editor)
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(medications = listOf(medication()), availability = listOf(pair, pair)))
        }
    }
    @Test fun sectionBoundsAndProtectedFallbacksAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(seed.copy(sections = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(seed.copy(sections = List(6) { seed.sections.single() })) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(seed.copy(forms = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(seed.copy(categories = emptyList())) }
    }
    @Test fun normalizedDuplicateTaxonomyNamesAreRejectedWithoutChangingStoredSpelling() {
        val extra = listOf(Category("00000000-0000-4000-8000-000000000701", "أدوية"),
            Category("00000000-0000-4000-8000-000000000702", "ادويه"))
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(categories = seed.categories + extra))
        }
        assertEquals("أدوية", extra.first().nameAr)
    }
    @Test fun protectedFallbackNamesCannotBeChanged() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(categories = listOf(seed.categories.single().copy(nameAr = "اسم آخر"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(forms = seed.forms.map { if (it.isSystem) it.copy(nameEn = "Renamed") else it }))
        }
    }
    @Test fun basicPricePairingAndNonnegativeValuesAreStorageInvariants() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(medications = listOf(medication().copy(priceMinor = 0))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRules.validate(seed.copy(medications = listOf(medication().copy(priceMinor = -1, currency = Currency.SYP))))
        }
        CatalogRules.validate(seed.copy(medications = listOf(medication().copy(priceMinor = 0, currency = Currency.SYP))))
    }
    @Test fun buildFlavorSeedDoesNotCrossIntoTheStandardProfile() {
        val actual = BuildSeed.catalog()
        CatalogRules.validate(actual)
        assertEquals(BuildConfig.IS_DEMO, actual.pharmacy != null)
        if (!BuildConfig.IS_DEMO) assertTrue(actual.medications.isEmpty())
    }
    @Test fun sharedCatalogSerializationHasNoDeviceAuthorizationOrLocalQueue() {
        val payload = Json.encodeToString(seed.copy(medications = listOf(medication())))
        listOf("adminMode", "botToken", "snapshotKey", "pending_operations", "device_identity", "history")
            .forEach { assertFalse(payload.contains(it)) }
        assertEquals(seed.copy(medications = listOf(medication())), Json.decodeFromString<CatalogSnapshot>(payload))
    }
    @Test fun missingWrappedKeyForExistingDatabaseNeverGeneratesAReplacement() {
        var writes = 0
        val store = object : SecureStore {
            override fun read(name: String): ByteArray? = null
            override fun write(name: String, value: ByteArray) { writes++ }
        }
        assertThrows(StorageRecoveryRequired::class.java) { DatabaseKeyProvider(store).get(databaseExists = true) }
        assertEquals(0, writes)
    }
}
