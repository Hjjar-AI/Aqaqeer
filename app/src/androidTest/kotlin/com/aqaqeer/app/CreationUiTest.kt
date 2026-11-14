package com.aqaqeer.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.aqaqeer.app.admin.AdminRepository
import com.aqaqeer.app.data.DatabaseFactory
import com.aqaqeer.app.security.KeystoreSecureStore
import com.aqaqeer.app.security.files.SnapshotRequirementsStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Only run in an explicitly isolated verification package; never clear a user's installed pharmacy. */
@dagger.hilt.android.testing.HiltAndroidTest
class CreationUiTest {
    @get:Rule(order = 0) val hilt = dagger.hilt.android.testing.HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private var created = false
    @After fun cleanupCreatedPharmacy() {
        if (created) {
            val context = compose.activity.applicationContext
            val store = KeystoreSecureStore(context)
            runBlocking { AdminRepository(DatabaseFactory(context), SnapshotRequirementsStore(store), store, com.aqaqeer.app.maintenance.MaintenanceStage(context)).leave("صيدلية واجهة الاختبار", true) }
        }
    }
    @Test fun createOfflineThenAddMedicineAndRetainItAfterActivityRecreation() {
        assumeFalse(BuildConfig.IS_DEMO)
        assumeTrue(BuildConfig.APPLICATION_ID.contains(".verification"))
        compose.waitUntil(15000) { compose.onAllNodesWithTag("create_pharmacy").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("create_pharmacy").performClick()
        compose.onNodeWithTag("اسم الصيدلية").performTextInput("صيدلية واجهة الاختبار")
        compose.onNodeWithTag("اسم المحرر").performScrollTo().performTextInput("محرر الاختبار")
        compose.onNodeWithTag("رمز الإدارة — ستة أرقام").performScrollTo().performTextInput("123456")
        compose.onNodeWithTag("تأكيد رمز الإدارة").performScrollTo().performTextInput("123456")
        compose.onNodeWithText("إنشاء الصيدلية").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("مسودة محلية — غير مشتركة").fetchSemanticsNodes().isNotEmpty() }
        created = true
        compose.onNodeWithText("إضافة دواء").performClick()
        compose.onNodeWithTag("الاسم الإنجليزي — مطلوب").performTextInput("UI Medicine")
        compose.onNodeWithTag("العيار — مطلوب").performScrollTo().performTextInput("10 mg")
        compose.onNodeWithText("حفظ محلياً").performScrollTo().performClick()
        // Saving must commit the medication, independently of lazy-list composition.
        compose.waitUntil(15000) {
            runBlocking(Dispatchers.IO) {
                val database = DatabaseFactory(compose.activity.applicationContext).open()
                try { database.catalogDao().medications().any { it.nameEn == "UI Medicine" } }
                finally { database.close() }
            }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_UI Medicine"))
        compose.onNodeWithTag("med_UI Medicine").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_UI Medicine"))
        compose.onNodeWithTag("med_UI Medicine").assertIsDisplayed()
        compose.onNodeWithTag("home").performScrollToNode(hasText("مسودة محلية — غير مشتركة"))
        compose.onNodeWithText("مسودة محلية — غير مشتركة").assertIsDisplayed()
        compose.onNodeWithText("إضافة دواء").assertIsDisplayed()
    }
}
