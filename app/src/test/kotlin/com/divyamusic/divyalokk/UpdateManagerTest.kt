package com.divyamusic.divyalokk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun newerMinorVersionIsNewer() {
        assertTrue(UpdateManager.isNewer("3.2", "3.1"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(UpdateManager.isNewer("3.1", "3.2"))
    }

    @Test
    fun tenIsNewerThanNine() {
        // Numeric comparison, not string comparison
        assertTrue(UpdateManager.isNewer("3.10", "3.9"))
        assertFalse(UpdateManager.isNewer("3.9", "3.10"))
    }

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(UpdateManager.isNewer("3.2", "3.2"))
        assertFalse(UpdateManager.isNewer("3.2.0", "3.2.0"))
    }

    @Test
    fun differentLengthsTreatMissingPartsAsZero() {
        assertFalse(UpdateManager.isNewer("3.2", "3.2.0"))
        assertFalse(UpdateManager.isNewer("3.2.0", "3.2"))
        assertTrue(UpdateManager.isNewer("3.2.1", "3.2"))
        assertTrue(UpdateManager.isNewer("4", "3.9.9"))
        assertFalse(UpdateManager.isNewer("3", "3.0.1"))
    }

    @Test
    fun majorVersionDominatesMinor() {
        assertTrue(UpdateManager.isNewer("4.0", "3.99"))
    }
}
