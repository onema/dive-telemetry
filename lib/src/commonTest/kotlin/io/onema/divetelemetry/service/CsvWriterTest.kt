package io.onema.divetelemetry.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.onema.divetelemetry.domain.TelemetryOutput
import io.onema.divetelemetry.domain.TelemetryRow
import io.onema.divetelemetry.error.WriteError
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Timeout
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class CsvWriterTest {
    private fun writeOrFail(output: TelemetryOutput, buffer: Buffer) {
        either {
            with(CsvWriter()) { write(output, buffer) }
        }.getOrElse { fail("Write failed: ${it.message}") }
    }

    @Test
    fun startsWithUtf8Bom() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("A", "B"),
            rows = listOf(TelemetryRow(mapOf("A" to "1", "B" to "2")))
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert
        val bytes = buffer.readByteArray()
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
    }

    @Test
    fun usesCrlfLineEndings() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("H1", "H2"),
            rows = listOf(
                TelemetryRow(mapOf("H1" to "a", "H2" to "b")),
                TelemetryRow(mapOf("H1" to "c", "H2" to "d"))
            )
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert
        val text = buffer.readByteArray().decodeToString()
        val content = text.removePrefix("\uFEFF")
        assertTrue(content.contains("\r\n"))
        val lines = content.split("\r\n")
        assertEquals("H1,H2", lines[0])
        assertEquals("a,b", lines[1])
        assertEquals("c,d", lines[2])
    }

    @Test
    fun noTrailingNewline() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("X"),
            rows = listOf(TelemetryRow(mapOf("X" to "1")))
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert
        val bytes = buffer.readByteArray()
        val lastTwo = bytes.takeLast(2)
        assertTrue(lastTwo != listOf(0x0D.toByte(), 0x0A.toByte()))
    }

    @Test
    fun headerAndDataFormatCorrect() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("Name", "Value", "Extra"),
            rows = listOf(
                TelemetryRow(mapOf("Name" to "x", "Value" to "1", "Extra" to "")),
                TelemetryRow(mapOf("Name" to "y", "Value" to "2", "Extra" to ""))
            )
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert
        val content = buffer.readByteArray().decodeToString().removePrefix("\uFEFF")
        assertEquals("Name,Value,Extra\r\nx,1,\r\ny,2,", content)
    }

    @Test
    fun emptyRowsOnlyWritesHeader() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("A", "B"),
            rows = emptyList()
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert — no trailing CRLF when there are no data rows
        val content = buffer.readByteArray().decodeToString().removePrefix("\uFEFF")
        assertEquals("A,B", content)
    }

    @Test
    fun `write raises WriteError on closed sink`() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("A"),
            rows = listOf(TelemetryRow(mapOf("A" to "1")))
        )
        val brokenSink = object : Sink {
            override fun write(source: Buffer, byteCount: Long) = throw IOException("closed")

            override fun flush() = throw IOException("closed")

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {}
        }.buffer()

        // Act
        val result = either<WriteError, Unit> {
            with(CsvWriter()) { write(output, brokenSink) }
        }

        // Assert
        assertIs<Either.Left<WriteError>>(result)
        assertIs<WriteError.IoFailure>(result.value)
    }

    @Test
    fun `missing row keys default to empty string`() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("A", "B", "C"),
            rows = listOf(TelemetryRow(mapOf("A" to "1")))
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert
        val content = buffer.readByteArray().decodeToString().removePrefix("\uFEFF")
        val lines = content.split("\r\n")
        assertEquals("A,B,C", lines[0])
        assertEquals("1,,", lines[1])
    }

    @Test
    fun `values with commas are properly quoted in output`() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("Name", "Value"),
            rows = listOf(TelemetryRow(mapOf("Name" to "a,b", "Value" to "normal")))
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert
        val content = buffer.readByteArray().decodeToString().removePrefix("\uFEFF")
        val lines = content.split("\r\n")
        assertEquals("\"a,b\",normal", lines[1])
    }

    @Test
    fun `values with double quotes are escaped in output`() {
        // Arrange
        val output = TelemetryOutput(
            headers = listOf("Name"),
            rows = listOf(TelemetryRow(mapOf("Name" to "say \"hello\"")))
        )
        val buffer = Buffer()

        // Act
        writeOrFail(output, buffer)

        // Assert
        val content = buffer.readByteArray().decodeToString().removePrefix("\uFEFF")
        val lines = content.split("\r\n")
        assertEquals("\"say \"\"hello\"\"\"", lines[1])
    }
}
