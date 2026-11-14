package com.aqaqeer.app.data

import com.aqaqeer.app.security.AdminPinKdfV1
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

object CatalogSeeds {
    const val UNCATEGORIZED_ID = "00000000-0000-4000-8000-000000000001"
    const val TABLET_FORM_ID = "00000000-0000-4000-8000-000000000201"
    const val OTHER_FORM_ID = "00000000-0000-4000-8000-00000000020f"
    const val STARTER_SECTION_ID = "00000000-0000-4000-8000-000000000100"
    val categories get() = listOf(Category(UNCATEGORIZED_ID, "غير مصنف", "Uncategorized", isSystem = true))
    private val formNames = listOf(
        "أقراص" to "Tablet", "كبسولات" to "Capsule", "شراب" to "Syrup",
        "معلّق" to "Suspension", "حقن" to "Injection", "قطرات" to "Drops",
        "كريم" to "Cream", "مرهم" to "Ointment", "تحاميل" to "Suppository",
        "جهاز استنشاق" to "Inhaler", "لصقة" to "Patch", "أكياس" to "Sachet",
        "بخاخ" to "Spray", "هلام" to "Gel", "أخرى" to "Other",
    )
    val forms get() = formNames.mapIndexed { index, (ar, en) ->
        MedicationForm("00000000-0000-4000-8000-%012x".format(Locale.ROOT, 0x201 + index),
            ar, en, index, isSystem = en == "Other")
    }
    fun defaultMedicationFormId(forms: List<MedicationForm>): String =
        TABLET_FORM_ID.takeIf { id -> forms.any { it.id == id } } ?: OTHER_FORM_ID

    fun unconfigured() = CatalogSnapshot(null, categories, forms,
        listOf(Section(STARTER_SECTION_ID, "القسم الرئيسي", "Main section")))
}

private val marks = Regex("[\\p{M}ـ]")
private val alef = Regex("[أإآٱ]")
private val spaces = Regex("\\s+")

/** Normalization never mutates stored spelling. Also defines taxonomy uniqueness. */
fun normalizedName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(marks, "")
    .replace(alef, "ا").replace('ى', 'ي').replace('ة', 'ه')
    .trim().lowercase(Locale.ROOT).replace(spaces, " ")

object CatalogRules {
    fun validate(catalog: CatalogSnapshot) {
        require(catalog.sections.size in 1..5) { "A catalog requires one to five sections" }
        fun uuid(value: String) { require(UUID.fromString(value).toString() == value.lowercase(Locale.ROOT)) }
        fun names(values: List<Pair<String, String>>) {
            require(values.map { it.first }.distinct().size == values.size) { "Duplicate IDs" }
            values.forEach { (id, name) -> uuid(id); require(name.isNotBlank() && name == name.trim()) }
            require(values.map { normalizedName(it.second) }.distinct().size == values.size) { "Duplicate normalized names" }
        }
        names(catalog.categories.map { it.id to it.nameAr })
        names(catalog.forms.map { it.id to it.nameAr })
        names(catalog.sections.map { it.id to it.nameAr })
        val fallbackCategory = catalog.categories.singleOrNull { it.id == CatalogSeeds.UNCATEGORIZED_ID }
        val fallbackForm = catalog.forms.singleOrNull { it.id == CatalogSeeds.OTHER_FORM_ID }
        require(fallbackCategory?.isSystem == true && fallbackCategory.nameAr == "غير مصنف" && fallbackCategory.nameEn == "Uncategorized")
        require(fallbackForm?.isSystem == true && fallbackForm.nameAr == "أخرى" && fallbackForm.nameEn == "Other")
        require(catalog.categories.count { it.isSystem } == 1 && catalog.forms.count { it.isSystem } == 1)
        catalog.pharmacy?.let {
            uuid(it.pharmacyId); require(it.name.isNotBlank() && it.name == it.name.trim())
            ZoneId.of(it.timezone)
            require(it.schemaVersion > 0 && it.minVersionCode > 0 && it.keyVersion > 0)
            require(it.adminPinVerifier.iterations == AdminPinKdfV1.ITERATIONS)
        }
        val categories = catalog.categories.map { it.id }.toSet()
        val forms = catalog.forms.map { it.id }.toSet()
        val sections = catalog.sections.map { it.id }.toSet()
        val meds = catalog.medications.map { it.id }.toSet()
        require(meds.size == catalog.medications.size) { "Duplicate medication IDs" }
        catalog.medications.forEach {
            uuid(it.id)
            listOf(it.nameEn, it.nameAr, it.genericAr, it.genericEn).forEach { name ->
                require(name == null || (name.isNotBlank() && name == name.trim()))
            }
            require(!it.genericAr.isNullOrBlank() || !it.genericEn.isNullOrBlank()) {
                "أدخل اسماً علمياً بالعربية أو الإنجليزية على الأقل"
            }
            require(it.nameAr == null || (it.nameAr.isNotBlank() && it.nameAr == it.nameAr.trim()))
            require(it.strength.isNotBlank() && it.strength == it.strength.trim())
            require(it.categoryId in categories && it.formId in forms)
            require(it.expirySoonThresholdDays >= 0)
        }
        require(catalog.availability.map { it.medicationId to it.sectionId }.distinct().size == catalog.availability.size)
        catalog.availability.forEach { require(it.medicationId in meds && it.sectionId in sections) }
        catalog.tombstones.forEach { uuid(it.entityId); uuid(it.operationId) }
    }
}
