package com.aqaqeer.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MedicationPresentationTest {
    private val seed = CatalogSeeds.unconfigured()
    private val editor = EditorRef("device", "Test")
    private val today = LocalDate.of(2026, 11, 9)
    private fun med(id: String, ar: String? = null, en: String = id) = Medication(id, en, "10 mg",
        CatalogSeeds.OTHER_FORM_ID, CatalogSeeds.UNCATEGORIZED_ID, Instant.EPOCH, Instant.EPOCH, editor, nameAr = ar)
    private fun browse(meds: List<Medication>, query: String = "", sort: HomeSort = HomeSort.ARABIC_NAME) =
        MedicationPresentation.browse(seed.copy(medications = meds), seed.sections.single().id, query, HomeFilters(), sort).map { it.id }

    @Test fun normalizedArabicEnglishAndWhitespaceDoNotAlterStoredNames() {
        val med = med("1", "إِبـرَةٌ فِتَى", "MEDICINE")
        assertEquals(listOf("1"), browse(listOf(med), "ابره فتي"))
        assertEquals(listOf("1"), browse(listOf(med), "  medicine  "))
        assertEquals("إِبـرَةٌ فِتَى", med.nameAr)
    }
    @Test fun allTokensMaySpanFieldsAndMissingOptionalsNeverMatch() {
        val med = med("1", en = "Saline").copy(genericEn = "Sodium chloride", brand = "Example")
        assertEquals(listOf("1"), browse(listOf(med), "saline sodium example"))
        assertTrue(browse(listOf(med), "saline missing").isEmpty())
        assertTrue(browse(listOf(med), "null").isEmpty())
    }
    @Test fun fieldPriorityComesBeforeExactPrefixAndSubstringQuality() {
        val meds = listOf(med("brand", en = "Z").copy(brand = "alpha"),
            med("generic", en = "Y").copy(genericEn = "alpha"),
            med("substring", en = "Xalpha"), med("prefix", en = "Alpha beta"),
            med("exact", en = "Alpha"), med("arabic", ar = "alpha", en = "Last"))
        assertEquals(listOf("arabic", "exact", "prefix", "substring", "generic", "brand"), browse(meds.reversed(), "alpha"))
    }
    @Test fun searchTiesAndArabicSortHaveEnglishFallbackAndStableIdentity() {
        val a = med("a", en = "Saline")
        val b = a.copy(id = "b")
        assertEquals(listOf("a", "b"), browse(listOf(b, a), "saline"))
        assertEquals(listOf("a", "b"), browse(listOf(b, a)))
        assertEquals(listOf("a", "b"), browse(listOf(b, a), sort = HomeSort.ENGLISH_NAME))
    }
    @Test fun englishSortingUsesArabicAlternateThenCreationTimeThenId() {
        val a = med("a", ar = "ب", en = "Same")
        val b = med("b", ar = "ا", en = "Same")
        val c = b.copy(id = "c", createdAt = Instant.ofEpochSecond(1))
        assertEquals(listOf("b", "c", "a"), browse(listOf(a, c, b), sort = HomeSort.ENGLISH_NAME))
    }
    @Test fun relevanceOverridesBrowsingSortAndClearingRestoresIt() {
        val a = med("a", ar = "ا", en = "Xalpha")
        val b = med("b", ar = "ب", en = "Alpha")
        assertEquals(listOf("b", "a"), browse(listOf(a, b), "alpha"))
        assertEquals(listOf("a", "b"), browse(listOf(b, a), " "))
    }
    @Test fun filtersUseOrWithinAndAcrossTypesAndMissingStatusIsUnknown() {
        val section = seed.sections.single().id
        val meds = listOf(med("1"), med("2").copy(categoryId = "other"), med("3"), med("4"))
        val availability = listOf(Availability("1", section, AvailabilityStatus.OUT, Instant.EPOCH, editor),
            Availability("2", section, AvailabilityStatus.LOW, Instant.EPOCH, editor),
            Availability("3", section, AvailabilityStatus.AVAILABLE, Instant.EPOCH, editor))
        val catalog = seed.copy(medications = meds, availability = availability)
        fun filtered(statuses: Set<AvailabilityStatus>, categories: Set<String>) = MedicationPresentation.browse(
            catalog, section, "", HomeFilters(statuses, categories), HomeSort.ENGLISH_NAME).map { it.id }
        assertEquals(listOf("1", "2"), filtered(setOf(AvailabilityStatus.OUT, AvailabilityStatus.LOW), emptySet()))
        assertEquals(listOf("1"), filtered(setOf(AvailabilityStatus.OUT, AvailabilityStatus.LOW), setOf(CatalogSeeds.UNCATEGORIZED_ID)))
        assertEquals(listOf("1", "2", "3", "4"), filtered(emptySet(), setOf(CatalogSeeds.UNCATEGORIZED_ID, "other")))
        assertEquals(listOf("4"), filtered(setOf(AvailabilityStatus.UNKNOWN), emptySet()))
    }
    @Test fun availabilityOrderReappliesToSelectedSectionAndUsesArabicFallback() {
        val section = seed.sections.single().id
        val meds = listOf(med("available"), med("unknown"), med("low"), med("out"))
        val statuses = listOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.UNKNOWN, AvailabilityStatus.LOW, AvailabilityStatus.OUT)
        val availability = meds.zip(statuses).map { (m, status) -> Availability(m.id, section, status, Instant.EPOCH, editor) } +
            meds.zip(statuses.reversed()).map { (m, status) -> Availability(m.id, "second", status, Instant.EPOCH, editor) }
        val catalog = seed.copy(medications = meds, availability = availability)
        assertEquals(listOf("out", "low", "unknown", "available"), MedicationPresentation.browse(catalog, section, "", HomeFilters(), HomeSort.AVAILABILITY).map { it.id })
        assertEquals(listOf("available", "unknown", "low", "out"), MedicationPresentation.browse(catalog, "second", "", HomeFilters(), HomeSort.AVAILABILITY).map { it.id })
    }
    @Test fun displayModesUseExistingNamesAndGenericFallbackWithoutPlaceholder() {
        fun text(ar: String?, en: String?, mode: NameDisplay) = MedicationPresentation.names(ar, en, mode).map { it.text }
        assertEquals(listOf("English"), text(null, "English", NameDisplay.ARABIC))
        assertEquals(listOf("عربي"), text("عربي", null, NameDisplay.ENGLISH))
        assertEquals(listOf("English"), text(null, "English", NameDisplay.BOTH))
        assertEquals(listOf("عربي", "English"), text("عربي", "English", NameDisplay.BOTH))
        assertTrue(text(null, null, NameDisplay.BOTH).isEmpty())
    }
    @Test fun expiryBoundariesIncludeTodayAndThresholdButNotYesterdayOrNoDate() {
        val med = med("1")
        assertNull(MedicationPresentation.expiry(med, today))
        assertEquals(ExpiryState.EXPIRED, MedicationPresentation.expiry(med.copy(expiryDate = today.minusDays(1)), today))
        assertEquals(ExpiryState.SOON, MedicationPresentation.expiry(med.copy(expiryDate = today), today))
        assertEquals(ExpiryState.SOON, MedicationPresentation.expiry(med.copy(expiryDate = today.plusDays(60)), today))
        assertNull(MedicationPresentation.expiry(med.copy(expiryDate = today.plusDays(61)), today))
        assertEquals(ExpiryState.SOON, MedicationPresentation.expiry(med.copy(expiryDate = today, expirySoonThresholdDays = 0), today))
        assertNull(MedicationPresentation.expiry(med.copy(expiryDate = today.plusDays(1), expirySoonThresholdDays = 0), today))
        assertEquals(ExpiryState.SOON, MedicationPresentation.expiry(med.copy(expiryDate = LocalDate.MAX, expirySoonThresholdDays = Int.MAX_VALUE), LocalDate.MAX))
    }
    @Test fun businessDayUsesPharmacyTimezoneAtMidnight() {
        assertEquals(today, MedicationPresentation.today(Instant.parse("2026-11-08T21:00:00Z"), "Asia/Damascus"))
        assertEquals(today.minusDays(1), MedicationPresentation.today(Instant.parse("2026-11-08T20:59:59Z"), "Asia/Damascus"))
    }
    @Test fun smartTipsOnlyUseOtherActiveAvailableSectionsWhenCurrentIsOut() {
        val current = seed.sections.single()
        val sections = listOf(current, current.copy(id = "available"), current.copy(id = "closed", isActive = false),
            current.copy(id = "low"), current.copy(id = "unknown"))
        val values = listOf(AvailabilityStatus.OUT, AvailabilityStatus.AVAILABLE, AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW, AvailabilityStatus.UNKNOWN)
        val catalog = seed.copy(sections = sections, availability = sections.zip(values).map { (s, status) -> Availability("med", s.id, status, Instant.EPOCH, editor) })
        assertEquals(listOf("available"), MedicationPresentation.availableElsewhere(catalog, "med", current.id).map { it.id })
        for (status in listOf(AvailabilityStatus.LOW, AvailabilityStatus.UNKNOWN, AvailabilityStatus.AVAILABLE)) {
            assertTrue(MedicationPresentation.availableElsewhere(catalog.copy(availability = catalog.availability.map { if (it.sectionId == current.id) it.copy(status = status) else it }), "med", current.id).isEmpty())
        }
        assertTrue(MedicationPresentation.availableElsewhere(catalog, "med", null).isEmpty())
        assertTrue(MedicationPresentation.availableElsewhere(catalog, "missing", current.id).isEmpty())
    }
    @Test fun moneyFormattingIsDecimalSafeIncludingZeroAndLongMax() {
        assertEquals("١٣٬٠٠٠ ل.س", MedicationPresentation.price(13_000, Currency.SYP))
        assertEquals("$12.50", MedicationPresentation.price(1250, Currency.USD))
        assertEquals("$0.00", MedicationPresentation.price(0, Currency.USD))
        assertEquals("٠ ل.س", MedicationPresentation.price(0, Currency.SYP))
        assertEquals("$92233720368547758.07", MedicationPresentation.price(Long.MAX_VALUE, Currency.USD))
    }
    @Test fun decimalPriceInputNormalizesDigitsAndRejectsPrecisionNegativesAndOverflow() {
        assertEquals(1250L, MedicationPresentation.parsePrice("١٢٫٥٠", Currency.USD))
        assertEquals(13000L, MedicationPresentation.parsePrice("۱۳۰۰۰", Currency.SYP))
        assertEquals(0L, MedicationPresentation.parsePrice("0", Currency.USD))
        for ((value, currency) in listOf("-1" to Currency.SYP, "1.0" to Currency.SYP,
            "1.001" to Currency.USD, "1e3" to Currency.USD, "NaN" to Currency.USD, "1,000" to Currency.SYP, "" to Currency.USD)) {
            assertThrows(IllegalArgumentException::class.java) { MedicationPresentation.parsePrice(value, currency) }
        }
        assertThrows(ArithmeticException::class.java) { MedicationPresentation.parsePrice("9223372036854775808", Currency.SYP) }
    }
    @Test fun freshnessTimestampsAreGregorianInPharmacyTimezone() {
        assertEquals("2026-11-09 00:00", MedicationPresentation.timestamp(Instant.parse("2026-11-08T21:00:00Z"), "Asia/Damascus"))
        assertEquals("500 mg / 20", MedicationPresentation.westernDigits("٥٠٠ mg / ۲۰"))
    }
}
