package com.aqaqeer.app.backup

import org.junit.Assert.*
import org.junit.Test

class BackupReminderTest {
    @Test fun reminderRequiresAuthorizationAndUsesLatestSuccessfulCheckpoint() {
        assertNull(BackupReminder(null, 20, 30).nextAt())
        val week = 604800000L
        assertEquals(100L + week, BackupReminder(100, null, null).nextAt())
        assertEquals(300L + week, BackupReminder(100, 300, 200).nextAt())
        assertEquals(400L + week, BackupReminder(100, 300, 400).nextAt())
        assertEquals(100L + week, BackupReminder(100, 50, 20).nextAt())
    }
}
