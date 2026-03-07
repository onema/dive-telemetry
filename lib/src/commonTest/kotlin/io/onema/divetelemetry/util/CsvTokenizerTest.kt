package io.onema.divetelemetry.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvTokenizerTest {
    @Test
    fun splitBasicLine() {
        // Arrange
        val line = "a,b,c"

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun splitPreservesTrailingEmpties() {
        // Arrange
        val line = "a,b,,,"

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf("a", "b", "", "", ""), result)
    }

    @Test
    fun splitEmptyString() {
        // Arrange
        val line = ""

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf(""), result)
    }

    @Test
    fun splitSingleField() {
        // Arrange
        val line = "hello"

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf("hello"), result)
    }

    @Test
    fun joinBasicFields() {
        // Arrange
        val fields = listOf("a", "b", "c")

        // Act
        val result = joinCsvFields(fields)

        // Assert
        assertEquals("a,b,c", result)
    }

    @Test
    fun joinPreservesEmpties() {
        // Arrange
        val fields = listOf("a", "", "", "b")

        // Act
        val result = joinCsvFields(fields)

        // Assert
        assertEquals("a,,,b", result)
    }

    @Test
    fun roundTrip() {
        // Arrange
        val original = "Time (sec),6.4,0,0,0.21,0.18,0.45,0,0,1,0,63,False,True,0,0,0,0,0,1.45,AI is off,No comms for 90s +,N/A,N/A,N/A,GTR and SAC are off,0,0,0"

        // Act
        val result = joinCsvFields(splitCsvLine(original))

        // Assert
        assertEquals(original, result)
    }

    // --- Quoted field parsing ---

    @Test
    fun splitQuotedFieldWithComma() {
        // Arrange
        val line = """a,"b,c",d"""

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf("a", "b,c", "d"), result)
    }

    @Test
    fun splitQuotedFieldWithEscapedQuotes() {
        // Arrange — a,"say ""hello""",b
        val line = "a,\"say \"\"hello\"\"\",b"

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf("a", "say \"hello\"", "b"), result)
    }

    @Test
    fun splitQuotedEmptyField() {
        // Arrange
        val line = """a,"",b"""

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf("a", "", "b"), result)
    }

    // --- joinCsvFields quoting ---

    @Test
    fun joinQuotesFieldWithComma() {
        // Arrange
        val fields = listOf("a", "b,c", "d")

        // Act
        val result = joinCsvFields(fields)

        // Assert
        assertEquals("""a,"b,c",d""", result)
    }

    @Test
    fun joinQuotesFieldWithDoubleQuote() {
        // Arrange
        val fields = listOf("a", "say \"hello\"", "b")

        // Act
        val result = joinCsvFields(fields)

        // Assert — a,"say ""hello""",b
        assertEquals("a,\"say \"\"hello\"\"\",b", result)
    }

    @Test
    fun roundTripWithQuotedFields() {
        // Arrange
        val fields = listOf("normal", "has,comma", "has \"quotes\"", "also normal")

        // Act
        val csv = joinCsvFields(fields)
        val parsed = splitCsvLine(csv)

        // Assert
        assertEquals(fields, parsed)
    }

    // --- Embedded newlines ---

    @Test
    fun splitQuotedFieldWithEmbeddedNewline() {
        // Arrange
        val line = "a,\"line1\nline2\",b"

        // Act
        val result = splitCsvLine(line)

        // Assert
        assertEquals(listOf("a", "line1\nline2", "b"), result)
    }

    @Test
    fun joinQuotesFieldWithNewline() {
        // Arrange
        val fields = listOf("a", "has\nnewline", "b")

        // Act
        val result = joinCsvFields(fields)

        // Assert
        assertEquals("a,\"has\nnewline\",b", result)
    }

    @Test
    fun joinQuotesFieldWithCarriageReturn() {
        // Arrange
        val fields = listOf("a", "has\rreturn", "b")

        // Act
        val result = joinCsvFields(fields)

        // Assert
        assertEquals("a,\"has\rreturn\",b", result)
    }

    @Test
    fun roundTripWithNewlineInField() {
        // Arrange
        val fields = listOf("normal", "has\nnewline", "also normal")

        // Act
        val csv = joinCsvFields(fields)
        val parsed = splitCsvLine(csv)

        // Assert
        assertEquals(fields, parsed)
    }
}
