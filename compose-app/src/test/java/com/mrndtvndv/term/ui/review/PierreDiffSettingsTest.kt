package com.mrndtvndv.term.ui.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PierreDiffSettingsTest {

    @Test
    fun `default settings do not enable custom font`() {
        val settings = DiffDisplaySettings(
            isDarkTheme = true,
            showLineNumbers = true,
            isWordDiffEnabled = true
        )
        assertFalse(settings.useCustomFont)
        assertEquals(0L, settings.fontVersion)
    }

    @Test
    fun `settings preserve custom font and font version`() {
        val settings = DiffDisplaySettings(
            isDarkTheme = false,
            showLineNumbers = false,
            isWordDiffEnabled = false,
            useCustomFont = true,
            fontVersion = 1712345678000L
        )
        assertTrue(settings.useCustomFont)
        assertEquals(1712345678000L, settings.fontVersion)
    }

    @Test
    fun `copy with custom font updates font fields`() {
        val base = DiffDisplaySettings(
            isDarkTheme = true,
            showLineNumbers = true,
            isWordDiffEnabled = true
        )
        val updated = base.copy(useCustomFont = true, fontVersion = 42L)
        assertTrue(updated.useCustomFont)
        assertEquals(42L, updated.fontVersion)
        assertTrue(updated.isDarkTheme)
    }
}
