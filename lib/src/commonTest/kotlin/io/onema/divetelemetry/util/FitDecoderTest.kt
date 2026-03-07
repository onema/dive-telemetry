package io.onema.divetelemetry.util

import arrow.core.getOrElse
import arrow.core.raise.either
import io.onema.divetelemetry.domain.FitMessage
import io.onema.divetelemetry.error.ParseError
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import kotlin.test.*

class FitDecoderTest {

    private fun decodeTestFile(): List<FitMessage> {
        val path = "src/commonTest/resources/ACTIVITY.fit".toPath()
        val source = FileSystem.SYSTEM.source(path).buffer()
        return try {
            either<ParseError, List<FitMessage>> {
                decodeFitFile(source)
            }.getOrElse { fail("FIT decode failed: ${it.message}") }
        } finally {
            source.close()
        }
    }

    @Test
    fun decodesActivityFitWithoutError() {
        // Act
        val messages = decodeTestFile()

        // Assert
        assertTrue(messages.isNotEmpty(), "Expected at least one FIT message")
    }

    @Test
    fun containsRecordMessages() {
        // Act
        val messages = decodeTestFile()

        // Assert
        val recordMessages = messages.filter { it.globalMessageNumber == 20 }
        assertTrue(recordMessages.isNotEmpty(), "Expected record messages (type 20)")
    }

    @Test
    fun recordMessagesHaveDepthField() {
        // Act
        val messages = decodeTestFile()

        // Assert — field 92 is depth
        val recordsWithDepth = messages
            .filter { it.globalMessageNumber == 20 }
            .filter { it.fields.containsKey(92) }
        assertTrue(recordsWithDepth.isNotEmpty(), "Expected records with depth field (92)")
    }

    @Test
    fun containsDiveGasMessages() {
        // Act
        val messages = decodeTestFile()

        // Assert — msg 259 is dive_gas
        val gasMessages = messages.filter { it.globalMessageNumber == 259 }
        assertTrue(gasMessages.isNotEmpty(), "Expected dive_gas messages (type 259)")
    }

    @Test
    fun diveGasHasOxygenField() {
        // Act
        val messages = decodeTestFile()

        // Assert — field 1 is oxygen percent
        val gasMsg = messages.first { it.globalMessageNumber == 259 }
        val oxygen = gasMsg.fields[1] as? Long
        assertEquals(32L, oxygen, "Expected oxygen = 32% (EAN32)")
    }

    @Test
    fun diveGasHasHeliumField() {
        // Act
        val messages = decodeTestFile()

        // Assert — field 3 is helium percent
        val gasMsg = messages.first { it.globalMessageNumber == 259 }
        val helium = gasMsg.fields[3] as? Long
        assertEquals(0L, helium, "Expected helium = 0%")
    }

    @Test
    fun depthFieldIsScaled() {
        // Act
        val messages = decodeTestFile()

        // Assert — depth values should be raw (scale 1000 for meters)
        val firstRecord = messages
            .filter { it.globalMessageNumber == 20 }
            .first { it.fields.containsKey(92) }
        val depthRaw = firstRecord.fields[92] as? Long
        assertTrue(depthRaw != null, "Expected non-null depth")
        // Raw depth in mm, so a dive to ~2.6m would be ~2635
        assertTrue(depthRaw >= 0, "Expected non-negative depth")
    }

    @Test
    fun rejectsNonFitFile() {
        // Arrange
        val buffer = Buffer().apply { writeUtf8("This is not a FIT file at all") }

        // Act
        val result = either<ParseError, List<FitMessage>> {
            decodeFitFile(buffer)
        }

        // Assert
        assertTrue(result.isLeft(), "Expected error for non-FIT file")
        val error = result.leftOrNull()
        assertIs<ParseError.InvalidFitFile>(error)
    }

    @Test
    fun rejectsEmptyInput() {
        // Arrange
        val buffer = Buffer()

        // Act
        val result = either<ParseError, List<FitMessage>> {
            decodeFitFile(buffer)
        }

        // Assert
        assertTrue(result.isLeft(), "Expected error for empty input")
        val error = result.leftOrNull()
        assertIs<ParseError.InvalidFitFile>(error)
    }

    @Test
    fun recordsHaveTimestampField() {
        // Act
        val messages = decodeTestFile()

        // Assert — field 253 is timestamp
        val records = messages.filter { it.globalMessageNumber == 20 }
        val withTimestamp = records.filter { it.fields.containsKey(253) }
        assertEquals(records.size, withTimestamp.size, "All records should have timestamps")
    }

    @Test
    fun `rejects file with header size less than 12`() {
        // Arrange
        val buffer = Buffer()
        buffer.writeByte(8) // header size = 8, which is < 12

        // Act
        val result = either<ParseError, List<FitMessage>> {
            decodeFitFile(buffer)
        }

        // Assert
        assertTrue(result.isLeft(), "Expected error for small header size")
        val error = result.leftOrNull()
        assertIs<ParseError.InvalidFitFile>(error)
        assertTrue(error.reason.contains("header size 8 < 12"))
    }

    @Test
    fun `rejects truncated header`() {
        // Arrange
        val buffer = Buffer()
        buffer.writeByte(14) // header size = 14, needs 13 more bytes
        buffer.writeByte(0)  // only 1 more byte instead of 13

        // Act
        val result = either<ParseError, List<FitMessage>> {
            decodeFitFile(buffer)
        }

        // Assert
        assertTrue(result.isLeft(), "Expected error for truncated header")
        val error = result.leftOrNull()
        assertIs<ParseError.InvalidFitFile>(error)
        assertTrue(error.reason.contains("truncated header"))
    }

    @Test
    fun `rejects file with missing FIT signature`() {
        // Arrange
        val buffer = Buffer()
        buffer.writeByte(14) // header size
        // 13 more bytes for the rest of the header (indices 0..12)
        // [0] protocol version, [1..2] profile version, [3..6] data size, [7..10] signature, [11..12] CRC
        val headerRest = ByteArray(13)
        // Put wrong signature at bytes [7..10]
        headerRest[7] = 'X'.code.toByte()
        headerRest[8] = 'X'.code.toByte()
        headerRest[9] = 'X'.code.toByte()
        headerRest[10] = 'X'.code.toByte()
        buffer.write(headerRest)

        // Act
        val result = either<ParseError, List<FitMessage>> {
            decodeFitFile(buffer)
        }

        // Assert
        assertTrue(result.isLeft(), "Expected error for wrong signature")
        val error = result.leftOrNull()
        assertIs<ParseError.InvalidFitFile>(error)
        assertTrue(error.reason.contains("missing .FIT signature"))
    }

    @Test
    fun `rejects truncated data section`() {
        // Arrange
        val buffer = Buffer()
        buffer.writeByte(14) // header size = 14
        val headerRest = ByteArray(13)
        // data size = 100 (little-endian at indices 3..6)
        headerRest[3] = 100.toByte()
        headerRest[4] = 0
        headerRest[5] = 0
        headerRest[6] = 0
        // .FIT signature at indices 7..10
        headerRest[7] = '.'.code.toByte()
        headerRest[8] = 'F'.code.toByte()
        headerRest[9] = 'I'.code.toByte()
        headerRest[10] = 'T'.code.toByte()
        buffer.write(headerRest)
        // Only write 2 bytes of data instead of 100
        buffer.writeByte(0)
        buffer.writeByte(0)

        // Act
        val result = either<ParseError, List<FitMessage>> {
            decodeFitFile(buffer)
        }

        // Assert
        assertTrue(result.isLeft(), "Expected error for truncated data")
        val error = result.leftOrNull()
        assertIs<ParseError.InvalidFitFile>(error)
    }

    @Test
    fun `decodes valid file with zero data size`() {
        // Arrange
        val buffer = Buffer()
        buffer.writeByte(14) // header size = 14
        val headerRest = ByteArray(13)
        // data size = 0
        headerRest[3] = 0
        headerRest[4] = 0
        headerRest[5] = 0
        headerRest[6] = 0
        // .FIT signature
        headerRest[7] = '.'.code.toByte()
        headerRest[8] = 'F'.code.toByte()
        headerRest[9] = 'I'.code.toByte()
        headerRest[10] = 'T'.code.toByte()
        buffer.write(headerRest)

        // Act
        val result = either<ParseError, List<FitMessage>> {
            decodeFitFile(buffer)
        }

        // Assert
        assertTrue(result.isRight(), "Expected success for zero-length data")
        assertEquals(0, result.getOrNull()!!.size)
    }
}
