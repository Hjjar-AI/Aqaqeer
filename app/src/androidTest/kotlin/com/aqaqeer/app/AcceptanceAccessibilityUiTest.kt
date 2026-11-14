package com.aqaqeer.app

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Framework accessibility exposure/focus only; does not claim audible TalkBack acceptance. */
@dagger.hilt.android.testing.HiltAndroidTest
class AcceptanceAccessibilityUiTest {
    @get:Rule(order=0) val hilt=dagger.hilt.android.testing.HiltAndroidRule(this)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    @Test fun frameworkLabelsRolesAndAccessibilityFocusAreExposed() {
        Assume.assumeTrue(BuildConfig.IS_DEMO)
        check(BuildConfig.APPLICATION_ID.contains(".verification."))
        compose.waitUntil(15000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_menu").performClick()
        compose.onNodeWithTag("settings_menu").performClick()
        compose.waitForIdle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val nodes=mutableListOf<JsonElement>();var labeledActions=0;var focused=false
        fun visit(node: AccessibilityNodeInfo) {
            if(node.packageName?.toString()==BuildConfig.APPLICATION_ID) {
                val label=listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).joinToString(" ")
                if(node.isVisibleToUser && (node.isClickable || node.isCheckable) && label.isNotBlank()) {
                    labeledActions++
                    if(!focused && node.actionList.any { it.id==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS }) {
                        val accepted=node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        if(accepted) { node.refresh();focused=node.isAccessibilityFocused }
                    }
                }
                if(label.isNotBlank()) nodes+=buildJsonObject {
                    put("label",label);put("class",node.className?.toString().orEmpty())
                    put("clickable",node.isClickable);put("checkable",node.isCheckable)
                    put("visible",node.isVisibleToUser);put("heading",AccessibilityNodeInfoCompat.wrap(node).isHeading)
                }
            }
            for(i in 0 until node.childCount) node.getChild(i)?.let { visit(it);it.recycle() }
        }
        val root=requireNotNull(automation.rootInActiveWindow)
        try { visit(root) } finally { root.recycle() }
        assertTrue("Settings must expose labeled interactive framework nodes",labeledActions>=2)
        assertTrue("An owned labeled control must accept and retain accessibility focus",focused)
        val id=requireNotNull(InstrumentationRegistry.getArguments().getString("acceptanceRun"))
        check(id.matches(Regex("[a-f0-9]{32}")))
        val dir=File(compose.activity.noBackupFilesDir,"acceptance-$id").apply { mkdirs() }
        File(dir,"accessibility.json").writeText(buildJsonObject {
            put("nodes",JsonArray(nodes));put("labeledActions",labeledActions);put("accessibilityFocusVerified",focused)
            put("spokenOutputVerified",false)
        }.toString())
    }
}
