package io.onema.divetelemetry.integration

import arrow.core.getOrElse
import io.onema.divetelemetry.plugins.InterpolationPlugin
import io.onema.divetelemetry.service.ShearwaterFormat
import io.onema.divetelemetry.service.transformDiveLog
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class EndToEndTransformTest {
    @Test
    fun fullPipelineMatchesExpectedOutput() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val expectedPath = "src/commonTest/resources/2025-12-7-ellen-telemetry.csv".toPath()

        val expectedBytes = FileSystem.SYSTEM.read(expectedPath) { readByteArray() }
        val outputBuffer = Buffer()

        // Act
        val bufferedSource = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(bufferedSource, outputBuffer, ShearwaterFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            bufferedSource.close()
        }

        // Assert
        val actualBytes = outputBuffer.readByteArray()
        assertTrue(
            expectedBytes.contentEquals(actualBytes),
            "Output bytes do not match expected: expected ${expectedBytes.size} bytes, got ${actualBytes.size} bytes"
        )
    }

    @Test
    fun `shearwater pipeline with interpolation produces more rows`() {
        // Arrange — Shearwater has 5-second intervals, so interpolation should ~5x the rows
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val normalBuffer = Buffer()
        val interpolatedBuffer = Buffer()
        val plugins = listOf(InterpolationPlugin)

        // Act — run without interpolation
        val source1 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source1, normalBuffer, ShearwaterFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source1.close()
        }

        // Act — run with interpolation
        val source2 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source2, interpolatedBuffer, ShearwaterFormat, plugins = plugins)
                .getOrElse { fail("Pipeline with interpolation failed: ${it.message}") }
        } finally {
            source2.close()
        }

        // Assert
        val normalText = normalBuffer.readByteArray().decodeToString(3)
        val interpolatedText = interpolatedBuffer.readByteArray().decodeToString(3)
        val normalLines = normalText.split("\r\n")
        val interpolatedLines = interpolatedText.split("\r\n")

        // Headers should match
        assertEquals(normalLines[0], interpolatedLines[0], "Headers should be identical")

        // Interpolated should have ~5x data rows (Shearwater 5-second intervals)
        val normalDataRows = normalLines.size - 1
        val interpolatedDataRows = interpolatedLines.size - 1
        assertTrue(
            interpolatedDataRows > normalDataRows * 3,
            "Interpolated rows ($interpolatedDataRows) should be significantly more than normal ($normalDataRows)"
        )

        // All rows should have the correct number of columns
        val expectedCols = normalLines[0].split(",").size
        for (i in 1 until interpolatedLines.size) {
            val cols = interpolatedLines[i].split(",").size
            assertEquals(expectedCols, cols, "Interpolated row $i should have $expectedCols columns, got $cols")
        }

        // Time values should be consecutive 1-second intervals
        val timeIndex = 0
        val times = interpolatedLines.drop(1).map { it.split(",")[timeIndex].toLong() }
        for (i in 1 until times.size) {
            assertEquals(times[i - 1] + 1, times[i], "Time should increase by 1s: ${times[i - 1]} -> ${times[i]}")
        }
    }
}
