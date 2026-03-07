package io.onema.divetelemetry.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValueTransformsTest {
    // --- parseStartTimeSeconds ---

    @Test
    fun `parseStartTimeSeconds parses PM time`() {
        // Arrange / Act
        val result = parseStartTimeSeconds("12/7/2025 8:39:59 PM")

        // Assert — 20:39:59 = 20*3600 + 39*60 + 59 = 74399
        assertEquals(74399L, result)
    }

    @Test
    fun `parseStartTimeSeconds parses AM time`() {
        // Arrange / Act
        val result = parseStartTimeSeconds("1/1/2026 9:05:00 AM")

        // Assert — 9:05:00 = 9*3600 + 5*60 = 32700
        assertEquals(32700L, result)
    }

    @Test
    fun `parseStartTimeSeconds handles 12 PM as noon`() {
        // Arrange / Act
        val result = parseStartTimeSeconds("1/1/2026 12:00:00 PM")

        // Assert — 12:00:00 PM = 43200
        assertEquals(43200L, result)
    }

    @Test
    fun `parseStartTimeSeconds handles 12 AM as midnight`() {
        // Arrange / Act
        val result = parseStartTimeSeconds("1/1/2026 12:00:00 AM")

        // Assert — 12:00:00 AM = 0
        assertEquals(0L, result)
    }

    @Test
    fun `parseStartTimeSeconds returns null for malformed input`() {
        // Arrange / Act / Assert
        assertNull(parseStartTimeSeconds("not a date"))
        assertNull(parseStartTimeSeconds(""))
        assertNull(parseStartTimeSeconds("12/7/2025 8:39 PM")) // missing seconds
    }

    // --- wallClockTime ---

    @Test
    fun `wallClockTime returns empty for null start`() {
        // Arrange / Act
        val (time, amPm) = wallClockTime(null, 60L)

        // Assert
        assertEquals("", time)
        assertEquals("", amPm)
    }

    @Test
    fun `wallClockTime computes PM time`() {
        // Arrange — start at 8:39 PM (74340s), elapsed 60s
        val start = 20 * 3600L + 39 * 60L

        // Act
        val (time, amPm) = wallClockTime(start, 60L)

        // Assert — 8:40 PM
        assertEquals("8:40", time)
        assertEquals("pm", amPm)
    }

    @Test
    fun `wallClockTime computes AM time`() {
        // Arrange — start at 9:00 AM (32400s), elapsed 300s
        val start = 9 * 3600L

        // Act
        val (time, amPm) = wallClockTime(start, 300L)

        // Assert — 9:05 AM
        assertEquals("9:05", time)
        assertEquals("am", amPm)
    }

    @Test
    fun `wallClockTime wraps past midnight`() {
        // Arrange — start at 11:59 PM (86340s), elapsed 120s
        val start = 23 * 3600L + 59 * 60L

        // Act
        val (time, amPm) = wallClockTime(start, 120L)

        // Assert — 12:01 AM
        assertEquals("12:01", time)
        assertEquals("am", amPm)
    }

    @Test
    fun `wallClockTime noon boundary`() {
        // Arrange — start at 11:59 AM, elapsed 60s
        val start = 11 * 3600L + 59 * 60L

        // Act
        val (time, amPm) = wallClockTime(start, 60L)

        // Assert — 12:00 PM
        assertEquals("12:00", time)
        assertEquals("pm", amPm)
    }
}
