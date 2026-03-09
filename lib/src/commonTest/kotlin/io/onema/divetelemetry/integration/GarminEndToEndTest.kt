package io.onema.divetelemetry.integration

import arrow.core.getOrElse
import io.onema.divetelemetry.plugins.InterpolationPlugin
import io.onema.divetelemetry.service.GarminFormat
import io.onema.divetelemetry.service.transformDiveLog
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GarminEndToEndTest {
    @Test
    fun `full pipeline produces valid output`() {
        // Arrange
        val inputPath = "src/commonTest/resources/ACTIVITY.fit".toPath()
        val outputBuffer = Buffer()

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source, outputBuffer, GarminFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val outputBytes = outputBuffer.readByteArray()
        assertTrue(outputBytes.isNotEmpty(), "Output should not be empty")

        assertEquals(0xEF.toByte(), outputBytes[0], "Expected BOM byte 1")
        assertEquals(0xBB.toByte(), outputBytes[1], "Expected BOM byte 2")
        assertEquals(0xBF.toByte(), outputBytes[2], "Expected BOM byte 3")

        val text = outputBytes.decodeToString(3, outputBytes.size)

        assertTrue(text.contains("\r\n"), "Expected CRLF line endings")
        assertTrue(!text.endsWith("\r\n"), "Should not end with trailing newline")

        val lines = text.split("\r\n")
        val headerLine = lines[0]
        val headers = headerLine.split(",")
        assertEquals(24, headers.size, "Expected 24 headers, got ${headers.size}")

        assertEquals("Time (s)", headers[0])
        assertEquals("Actual Depth (m)", headers[1])

        assertTrue("Display Minutes (text)" in headers, "Expected Display Minutes header")
        assertTrue("Display Seconds (text)" in headers, "Expected Display Seconds header")
        assertTrue("Max Depth (text)" in headers, "Expected Max Depth header")
        assertTrue("Actual Time (text)" in headers, "Expected Actual Time header")
        assertTrue("AM/PM Indicator (text)" in headers, "Expected AM/PM Indicator header")

        assertTrue(lines.size > 100, "Expected at least 100 data rows, got ${lines.size - 1}")

        for (i in 1 until lines.size) {
            val fields = lines[i].split(",")
            assertEquals(24, fields.size, "Row $i should have 24 fields, got ${fields.size}")
        }
    }

    @Test
    fun `depth values are negative`() {
        // Arrange
        val inputPath = "src/commonTest/resources/ACTIVITY.fit".toPath()
        val outputBuffer = Buffer()

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source, outputBuffer, GarminFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val text = outputBuffer.readByteArray().decodeToString(3)
        val lines = text.split("\r\n")
        val depthIndex = 1

        val depthValues = lines.drop(1).map { line ->
            line.split(",")[depthIndex].toDoubleOrNull() ?: 0.0
        }

        assertTrue(depthValues.any { it < 0 }, "Expected negative depth values for underwater readings")
    }

    @Test
    fun `temperature values are in celsius`() {
        // Arrange
        val inputPath = "src/commonTest/resources/ACTIVITY.fit".toPath()
        val outputBuffer = Buffer()

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source, outputBuffer, GarminFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val text = outputBuffer.readByteArray().decodeToString(3)
        val lines = text.split("\r\n")
        val tempIndex = 10

        val temps = lines.drop(1).mapNotNull { line ->
            line.split(",")[tempIndex].toIntOrNull()
        }

        assertTrue(temps.isNotEmpty(), "Expected temperature values")
        assertTrue(temps.all { it in 0..40 }, "Temperature should be in reasonable C range (0-40)")
    }

    @Test
    fun `wall clock time is empty for garmin`() {
        // Arrange
        val inputPath = "src/commonTest/resources/ACTIVITY.fit".toPath()
        val outputBuffer = Buffer()

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source, outputBuffer, GarminFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val text = outputBuffer.readByteArray().decodeToString(3)
        val lines = text.split("\r\n")
        val headers = lines[0].split(",")
        val timeIndex = headers.indexOf("Actual Time (text)")
        val ampmIndex = headers.indexOf("AM/PM Indicator (text)")

        assertTrue(timeIndex >= 0, "Expected Actual Time (text) header")
        assertTrue(ampmIndex >= 0, "Expected AM/PM Indicator (text) header")

        for (i in 1 until lines.size) {
            val fields = lines[i].split(",")
            assertEquals("", fields[timeIndex], "Row $i: Actual Time should be empty for Garmin")
            assertEquals("", fields[ampmIndex], "Row $i: AM/PM should be empty for Garmin")
        }
    }

    @Test
    fun `interpolation produces 1-second intervals`() {
        // Arrange
        val inputPath = "src/commonTest/resources/ACTIVITY.fit".toPath()
        val outputBuffer = Buffer()
        val plugins = listOf(InterpolationPlugin)

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source, outputBuffer, GarminFormat, plugins = plugins)
                .getOrElse { fail("Pipeline with interpolation failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val text = outputBuffer.readByteArray().decodeToString(3)
        val lines = text.split("\r\n")
        assertTrue(lines.size > 100, "Expected substantial output")

        val headers = lines[0].split(",")
        assertEquals(24, headers.size, "Expected 24 headers")

        // Time values should be consecutive 1-second intervals
        val times = lines.drop(1).map { it.split(",")[0].toLong() }
        for (i in 1 until times.size) {
            assertEquals(
                times[i - 1] + 1, times[i],
                "Time should increase by 1s: ${times[i - 1]} -> ${times[i]} at row $i"
            )
        }

        // All rows have correct column count
        for (i in 1 until lines.size) {
            assertEquals(24, lines[i].split(",").size, "Row $i should have 24 fields")
        }
    }

    @Test
    fun `max depth increases monotonically`() {
        // Arrange
        val inputPath = "src/commonTest/resources/ACTIVITY.fit".toPath()
        val outputBuffer = Buffer()

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source, outputBuffer, GarminFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val text = outputBuffer.readByteArray().decodeToString(3)
        val lines = text.split("\r\n")
        val headers = lines[0].split(",")
        val maxDepthIndex = headers.indexOf("Max Depth (text)")

        val maxDepths = lines.drop(1).mapNotNull { line ->
            line.split(",")[maxDepthIndex].toDoubleOrNull()
        }

        for (i in 1 until maxDepths.size) {
            assertTrue(
                maxDepths[i] >= maxDepths[i - 1],
                "Max depth should be non-decreasing: ${maxDepths[i]} < ${maxDepths[i - 1]} at row $i"
            )
        }
    }
}
