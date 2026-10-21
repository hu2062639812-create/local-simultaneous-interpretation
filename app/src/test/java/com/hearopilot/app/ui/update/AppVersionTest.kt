package com.hearopilot.app.ui.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun detectsNewerVersions() {
        assertTrue(AppVersion.isNewer("1.35.0", "1.34.9"))
        assertTrue(AppVersion.isNewer("v1.35.00", "1.34.00"))
        assertTrue(AppVersion.isNewer("2.0", "1.99.99"))
        assertTrue(AppVersion.isNewer("1.34.1", "1.34"))
    }

    @Test
    fun rejectsSameOrOlderVersions() {
        assertFalse(AppVersion.isNewer("1.35.0", "1.35.0"))
        assertFalse(AppVersion.isNewer("1.34.00", "1.35.0"))
        assertFalse(AppVersion.isNewer("1.34", "1.34.0"))
        assertFalse(AppVersion.isNewer("", "1.34.0"))
        assertFalse(AppVersion.isNewer("1.35.0", ""))
    }
}
