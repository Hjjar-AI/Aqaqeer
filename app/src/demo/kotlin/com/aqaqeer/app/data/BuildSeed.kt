package com.aqaqeer.app.data

import com.aqaqeer.app.admin.replace
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/** Demo-only local catalog. Names/forms/strengths: docs/demo-data.md; no clinical instructions. */
object BuildSeed {
    const val VERSION = 3
    private const val PHARMACY_ID = "00000000-0000-4000-8000-000000000901"
    private const val CAPSULE_FORM_ID = "00000000-0000-4000-8000-000000000202"
    private fun id(value: String) = UUID.nameUUIDFromBytes("aqaqeer-demo-$value".toByteArray(Charsets.UTF_8)).toString()

    fun catalog(): CatalogSnapshot {
        val seeds = CatalogSeeds.unconfigured()
        val time = Instant.now()
        val today = LocalDate.now(ZoneId.of("Asia/Damascus"))
        val editor = EditorRef("00000000-0000-4000-8000-000000000900", "فريق العرض التجريبي")
        fun randomBase64(size: Int) = Base64.getEncoder().encodeToString(ByteArray(size).also { SecureRandom().nextBytes(it) })
        val pharmacy = Pharmacy(PHARMACY_ID, "صيدلية الصحة النفسية — عرض تجريبي",
            "Asia/Damascus", PinVerifier(randomBase64(16), 200_000, randomBase64(32)))
        val antidepressants = Category(id("antidepressants"), "مضادات الاكتئاب", "Antidepressants", orderIndex = 1)
        val antipsychotics = Category(id("antipsychotics"), "مضادات الذهان", "Antipsychotics", orderIndex = 2)
        val moodStabilizers = Category(id("mood-stabilizers"), "مثبتات المزاج", "Mood stabilizers", orderIndex = 3)
        val anxiety = Category(id("anxiety"), "مضادات القلق", "Anxiolytics", orderIndex = 4)
        val movement = Category(id("movement-effects"), "الأعراض الحركية", "Movement symptoms", orderIndex = 5)
        val restlessness = Category(id("restlessness-tremor"), "التململ والرعاش", "Restlessness and tremor", orderIndex = 6)
        val metabolic = Category(id("metabolic-effects"), "الوزن والاستقلاب", "Weight and metabolic effects", orderIndex = 7)
        val constipation = Category(id("constipation"), "الإمساك", "Constipation", orderIndex = 8)
        val nausea = Category(id("nausea"), "الغثيان والقيء", "Nausea and vomiting", orderIndex = 9)
        val reflux = Category(id("reflux"), "الحموضة والارتجاع", "Acidity and reflux", orderIndex = 10)
        val dryEyes = Category(id("dry-eyes"), "جفاف العين", "Dry eyes", orderIndex = 11)
        val sexualEffects = Category(id("sexual-effects"), "الآثار الجنسية", "Sexual effects", orderIndex = 12)
        val categories = listOf(antidepressants, antipsychotics, moodStabilizers, anxiety, movement,
            restlessness, metabolic, constipation, nausea, reflux, dryEyes, sexualEffects)
        val drops = seeds.forms.single { it.nameEn == "Drops" }.id
        val sachet = seeds.forms.single { it.nameEn == "Sachet" }.id
        val sections = listOf(
            Section(CatalogSeeds.STARTER_SECTION_ID, "العيادة الخارجية", "Outpatient", orderIndex = 0),
            Section(id("section-inpatient"), "القسم الداخلي", "Inpatient", orderIndex = 1))
        data class Sample(val key: String, val ar: String, val en: String, val strength: String,
            val form: String, val category: String)
        val rows = listOf(
            Sample("sertraline", "سيرترالين", "Sertraline", "50 mg", CatalogSeeds.TABLET_FORM_ID, antidepressants.id),
            Sample("fluoxetine", "فلوكسيتين", "Fluoxetine", "20 mg", CAPSULE_FORM_ID, antidepressants.id),
            Sample("escitalopram", "إسيتالوبرام", "Escitalopram", "10 mg", CatalogSeeds.TABLET_FORM_ID, antidepressants.id),
            Sample("mirtazapine", "ميرتازابين", "Mirtazapine", "15 mg", CatalogSeeds.TABLET_FORM_ID, antidepressants.id),
            Sample("risperidone", "ريسبيريدون", "Risperidone", "2 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("olanzapine", "أولانزابين", "Olanzapine", "5 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("lamotrigine", "لاموتريجين", "Lamotrigine", "25 mg", CatalogSeeds.TABLET_FORM_ID, moodStabilizers.id),
            Sample("lithium-carbonate", "كربونات الليثيوم", "Lithium carbonate", "300 mg", CAPSULE_FORM_ID, moodStabilizers.id),
            Sample("paroxetine-20-mg", "باروكسيتين", "Paroxetine", "20 mg", CatalogSeeds.TABLET_FORM_ID, antidepressants.id),
            Sample("paroxetine-40-mg", "باروكسيتين", "Paroxetine", "40 mg", CatalogSeeds.TABLET_FORM_ID, antidepressants.id),
            Sample("venlafaxine-er-37-5-mg", "فينلافاكسين ممتد التحرر", "Venlafaxine (extended release)", "37.5 mg", CAPSULE_FORM_ID, antidepressants.id),
            Sample("venlafaxine-er-75-mg", "فينلافاكسين ممتد التحرر", "Venlafaxine (extended release)", "75 mg", CAPSULE_FORM_ID, antidepressants.id),
            Sample("quetiapine-25-mg", "كويتيابين", "Quetiapine", "25 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("quetiapine-100-mg", "كويتيابين", "Quetiapine", "100 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("quetiapine-200-mg", "كويتيابين", "Quetiapine", "200 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("aripiprazole-5-mg", "أريبيبرازول", "Aripiprazole", "5 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("aripiprazole-10-mg", "أريبيبرازول", "Aripiprazole", "10 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("aripiprazole-15-mg", "أريبيبرازول", "Aripiprazole", "15 mg", CatalogSeeds.TABLET_FORM_ID, antipsychotics.id),
            Sample("carbamazepine-200-mg", "كاربامازيبين", "Carbamazepine", "200 mg", CatalogSeeds.TABLET_FORM_ID, moodStabilizers.id),
            Sample("buspirone-5-mg", "بوسبيرون هيدروكلوريد", "Buspirone hydrochloride", "5 mg", CatalogSeeds.TABLET_FORM_ID, anxiety.id),
            Sample("buspirone-10-mg", "بوسبيرون هيدروكلوريد", "Buspirone hydrochloride", "10 mg", CatalogSeeds.TABLET_FORM_ID, anxiety.id),
            Sample("benztropine-0-5-mg", "بنزتروبين ميسيلات", "Benztropine mesylate", "0.5 mg", CatalogSeeds.TABLET_FORM_ID, movement.id),
            Sample("benztropine-1-mg", "بنزتروبين ميسيلات", "Benztropine mesylate", "1 mg", CatalogSeeds.TABLET_FORM_ID, movement.id),
            Sample("benztropine-2-mg", "بنزتروبين ميسيلات", "Benztropine mesylate", "2 mg", CatalogSeeds.TABLET_FORM_ID, movement.id),
            Sample("propranolol-10-mg", "بروبرانولول هيدروكلوريد", "Propranolol hydrochloride", "10 mg", CatalogSeeds.TABLET_FORM_ID, restlessness.id),
            Sample("propranolol-20-mg", "بروبرانولول هيدروكلوريد", "Propranolol hydrochloride", "20 mg", CatalogSeeds.TABLET_FORM_ID, restlessness.id),
            Sample("propranolol-40-mg", "بروبرانولول هيدروكلوريد", "Propranolol hydrochloride", "40 mg", CatalogSeeds.TABLET_FORM_ID, restlessness.id),
            Sample("metformin-500-mg", "ميتفورمين هيدروكلوريد", "Metformin hydrochloride", "500 mg", CatalogSeeds.TABLET_FORM_ID, metabolic.id),
            Sample("metformin-850-mg", "ميتفورمين هيدروكلوريد", "Metformin hydrochloride", "850 mg", CatalogSeeds.TABLET_FORM_ID, metabolic.id),
            Sample("metformin-1000-mg", "ميتفورمين هيدروكلوريد", "Metformin hydrochloride", "1000 mg", CatalogSeeds.TABLET_FORM_ID, metabolic.id),
            Sample("polyethylene-glycol-17-g", "بولي إيثيلين غليكول 3350", "Polyethylene glycol 3350", "17 g", sachet, constipation.id),
            Sample("sennosides-8-6-mg", "سينوسيدات", "Sennosides", "8.6 mg", CatalogSeeds.TABLET_FORM_ID, constipation.id),
            Sample("docusate-100-mg", "دوكوسات الصوديوم", "Docusate sodium", "100 mg", CAPSULE_FORM_ID, constipation.id),
            Sample("ondansetron-4-mg", "أوندانسيترون", "Ondansetron", "4 mg", CatalogSeeds.TABLET_FORM_ID, nausea.id),
            Sample("ondansetron-8-mg", "أوندانسيترون", "Ondansetron", "8 mg", CatalogSeeds.TABLET_FORM_ID, nausea.id),
            Sample("famotidine-20-mg", "فاموتيدين", "Famotidine", "20 mg", CatalogSeeds.TABLET_FORM_ID, reflux.id),
            Sample("famotidine-40-mg", "فاموتيدين", "Famotidine", "40 mg", CatalogSeeds.TABLET_FORM_ID, reflux.id),
            Sample("carboxymethylcellulose-0-5percent", "كربوكسي ميثيل سيلولوز الصوديوم", "Carboxymethylcellulose sodium", "0.5%", drops, dryEyes.id),
            Sample("sildenafil-25-mg", "سيلدينافيل", "Sildenafil", "25 mg", CatalogSeeds.TABLET_FORM_ID, sexualEffects.id),
            Sample("sildenafil-50-mg", "سيلدينافيل", "Sildenafil", "50 mg", CatalogSeeds.TABLET_FORM_ID, sexualEffects.id),
        )
        val medications = rows.mapIndexed { index, row ->
            Medication(id("med-${row.key}"), strength = row.strength, formId = row.form,
                categoryId = row.category, createdAt = time, updatedAt = time, updatedBy = editor,
                genericAr = row.ar, genericEn = row.en,
                note = "سجل عرض محلي. التوفر والصلاحية افتراضيان، ولا يمثلان صيدلية حقيقية أو تعليمات علاجية.",
                expiryDate = when (index) { 0 -> today.minusDays(7); 1 -> today.plusDays(21); 7 -> null; else -> today.plusMonths(12) })
        }
        val states = listOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW, AvailabilityStatus.OUT, AvailabilityStatus.UNKNOWN)
        val availability = sections.flatMapIndexed { sectionIndex, section ->
            medications.mapIndexedNotNull { index, med ->
                // One missing pair intentionally exercises the normal UNKNOWN presentation.
                if (sectionIndex == 1 && index == medications.lastIndex) null else
                    Availability(med.id, section.id, states[(index + sectionIndex) % states.size], time, editor)
            }
        }
        return seeds.copy(pharmacy = pharmacy, categories = seeds.categories + categories,
            sections = sections, medications = medications, availability = availability).also(CatalogRules::validate)
    }

    /** Caller holds the Room transaction. Never replace an unexpected pharmacy or real work. */
    suspend fun refreshIfNeeded(dao: CatalogDao, installation: InstallationState) {
        check(installation.isDemo)
        val pharmacy = dao.pharmacy()
        val empty = dao.medications().isEmpty()
        if (installation.seedVersion > VERSION || installation.seedVersion == VERSION && pharmacy != null && !empty) return
        check(pharmacy == null || pharmacy.pharmacyId == PHARMACY_ID) { "Unexpected pharmacy in demo storage" }
        check(pharmacy != null || empty) { "Unexpected medication data without a demo pharmacy" }
        check(dao.pendingCount() == 0 && dao.attemptCount() == 0 && dao.historyCount() == 0 && dao.tombstones().isEmpty())
        check(dao.deviceIdentity()?.adminMode == false)
        val control = requireNotNull(dao.control())
        check(!control.leaving && !control.restorePaused && control.restoreActivationId == null)
        val state = requireNotNull(dao.snapshotState())
        check(state.confirmedPayloadJson == null && state.publishId == null)
        dao.replace(catalog())
        dao.updateSnapshotState(SnapshotState())
        dao.control(control.copy(lifecycle = "SHARED", reconnectPending = false))
        dao.updateInstallation(installation.copy(seedVersion = VERSION))
    }
}
