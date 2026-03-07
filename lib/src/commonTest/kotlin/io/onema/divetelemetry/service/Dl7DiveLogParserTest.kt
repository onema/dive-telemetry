package io.onema.divetelemetry.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.error.ParseError
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class Dl7DiveLogParserTest {

    private fun bufferOf(text: String): Buffer = Buffer().apply { writeUtf8(text) }

    private fun parseOrFail(input: String): DiveLog = either<ParseError, DiveLog> {
        with(Dl7DiveLogParser()) { parse(bufferOf(input)) }
    }.getOrElse { fail("Expected successful parse but got: ${it.message}") }

    private fun parseResult(input: String): Either<ParseError, DiveLog> = either {
        with(Dl7DiveLogParser()) { parse(bufferOf(input)) }
    }

    private val minimalDl7 = """
        |ZRH|1|2|3|4|5|C|bar|extra
        |ZDH|1|2|3|4|20260115093000|extra
        |ZDP{
        ||1.0|5.0|0|0|0|0|0|25|extra
        ||2.0|10.0|0|0|0|0|0|26|extra
        |ZDP}
    """.trimMargin()

    @Test
    fun `parses valid DL7 file with samples`() {
        // Arrange / Act
        val log = parseOrFail(minimalDl7)

        // Assert
        assertEquals(2, log.samples.size)
    }

    @Test
    fun `depth always in meters`() {
        // Arrange / Act
        val log = parseOrFail(minimalDl7)

        // Assert
        assertEquals(DepthUnit.M, log.metadata.depthUnit)
    }

    @Test
    fun `temperature unit from ZRH field 6 is Celsius`() {
        // Arrange
        val input = """
            |ZRH|1|2|3|4|5|C|bar|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(TempUnit.CELSIUS, log.metadata.tempUnit)
    }

    @Test
    fun `temperature unit from ZRH field 6 is Fahrenheit`() {
        // Arrange
        val input = """
            |ZRH|1|2|3|4|5|F|bar|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|77|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(TempUnit.FAHRENHEIT, log.metadata.tempUnit)
    }

    @Test
    fun `pressure unit from ZRH field 7 is BAR`() {
        // Arrange
        val input = """
            |ZRH|1|2|3|4|5|C|bar|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(PressureUnit.BAR, log.metadata.pressureUnit)
    }

    @Test
    fun `pressure unit from ZRH field 7 is PSI`() {
        // Arrange
        val input = """
            |ZRH|1|2|3|4|5|C|psi|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(PressureUnit.PSI, log.metadata.pressureUnit)
    }

    @Test
    fun `pressure unit PSI is case insensitive`() {
        // Arrange
        val input = """
            |ZRH|1|2|3|4|5|C|PSI|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(PressureUnit.PSI, log.metadata.pressureUnit)
    }

    @Test
    fun `defaults to CELSIUS and BAR when ZRH missing`() {
        // Arrange
        val input = """
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(TempUnit.CELSIUS, log.metadata.tempUnit)
        assertEquals(PressureUnit.BAR, log.metadata.pressureUnit)
    }

    @Test
    fun `startTime parsed from ZDH field 5 AM`() {
        // Arrange / Act
        val log = parseOrFail(minimalDl7)

        // Assert
        assertEquals("1/15/2026 9:30:00 AM", log.metadata.startTime)
    }

    @Test
    fun `startTime parsed for PM hour`() {
        // Arrange
        val input = """
            |ZDH|1|2|3|4|20260115143000|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals("1/15/2026 2:30:00 PM", log.metadata.startTime)
    }

    @Test
    fun `startTime midnight formats as 12 AM`() {
        // Arrange
        val input = """
            |ZDH|1|2|3|4|20260115000000|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals("1/15/2026 12:00:00 AM", log.metadata.startTime)
    }

    @Test
    fun `startTime noon formats as 12 PM`() {
        // Arrange
        val input = """
            |ZDH|1|2|3|4|20260115120000|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals("1/15/2026 12:00:00 PM", log.metadata.startTime)
    }

    @Test
    fun `startTime null when ZDH missing`() {
        // Arrange
        val input = """
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertNull(log.metadata.startTime)
    }

    @Test
    fun `startTime null for malformed time string`() {
        // Arrange
        val input = """
            |ZDH|1|2|3|4|bad|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertNull(log.metadata.startTime)
    }

    @Test
    fun `startTime null when ZDH field 5 too short`() {
        // Arrange
        val input = """
            |ZDH|1|2|3|4|2026011509|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertNull(log.metadata.startTime)
    }

    @Test
    fun `time conversion from minutes to seconds`() {
        // Arrange / Act
        val log = parseOrFail(minimalDl7)

        // Assert
        assertEquals(60L, log.samples[0].timeSeconds)
        assertEquals(120L, log.samples[1].timeSeconds)
    }

    @Test
    fun `fractional minutes converted correctly`() {
        // Arrange
        val input = """
            |ZDP{
            ||1.5|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(90L, log.samples[0].timeSeconds)
    }

    @Test
    fun `depth parsed from field 2`() {
        // Arrange / Act
        val log = parseOrFail(minimalDl7)

        // Assert
        assertEquals(5.0, log.samples[0].depth)
        assertEquals(10.0, log.samples[1].depth)
    }

    @Test
    fun `water temp parsed from field 8`() {
        // Arrange / Act
        val log = parseOrFail(minimalDl7)

        // Assert
        assertEquals(25.0, log.samples[0].waterTemp)
        assertEquals(26.0, log.samples[1].waterTemp)
    }

    @Test
    fun `sentinel rows filtered out`() {
        // Arrange
        val input = """
            |ZDP{
            ||0.0|0.0|0|0|0|0|0|-17|extra
            ||1.0|5.0|0|0|0|0|0|25|extra
            ||2.0|0.0|0|0|0|0|0|-17|extra
            ||3.0|8.0|0|0|0|0|0|24|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(2, log.samples.size)
        assertEquals(5.0, log.samples[0].depth)
        assertEquals(8.0, log.samples[1].depth)
    }

    @Test
    fun `temp -17 treated as null in non-sentinel rows`() {
        // Arrange
        val input = """
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|-17|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(1, log.samples.size)
        assertNull(log.samples[0].waterTemp)
    }

    @Test
    fun `temp null when field 8 missing`() {
        // Arrange
        val input = """
            |ZDP{
            ||1.0|5.0|0|0|0|0|0
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(1, log.samples.size)
        assertNull(log.samples[0].waterTemp)
    }

    @Test
    fun `default values for fields not in DL7`() {
        // Arrange / Act
        val log = parseOrFail(minimalDl7)
        val sample = log.samples[0]

        // Assert
        assertEquals(0.0, sample.avgPpo2)
        assertEquals(0.21, sample.fractionO2)
        assertEquals(0.0, sample.fractionHe)
        assertEquals(0.0, sample.firstStopDepth)
        assertEquals(0L, sample.firstStopTime)
        assertEquals(0L, sample.timeToSurface)
        assertNull(sample.currentNdl)
        assertEquals(0, sample.currentCircuitMode)
        assertEquals(0, sample.currentCcrMode)
        assertEquals(false, sample.gasSwitchNeeded)
        assertEquals(false, sample.externalPpo2)
    }

    @Test
    fun `raises MissingFitData when no valid samples`() {
        // Arrange
        val input = """
            |ZDP{
            ||0.0|0.0|0|0|0|0|0|-17|extra
            |ZDP}
        """.trimMargin()

        // Act
        val result = parseResult(input)

        // Assert
        assertIs<Either.Left<ParseError>>(result)
        assertIs<ParseError.MissingFitData>(result.value)
        assertTrue(result.value.message.contains("dive samples"))
    }

    @Test
    fun `raises MissingFitData when ZDP block is empty`() {
        // Arrange
        val input = """
            |ZDP{
            |ZDP}
        """.trimMargin()

        // Act
        val result = parseResult(input)

        // Assert
        assertIs<Either.Left<ParseError>>(result)
        assertIs<ParseError.MissingFitData>(result.value)
    }

    @Test
    fun `raises MissingFitData when no ZDP block at all`() {
        // Arrange
        val input = """
            |ZRH|1|2|3|4|5|C|bar|extra
        """.trimMargin()

        // Act
        val result = parseResult(input)

        // Assert
        assertIs<Either.Left<ParseError>>(result)
        assertIs<ParseError.MissingFitData>(result.value)
    }

    @Test
    fun `lines not starting with pipe are ignored in ZDP block`() {
        // Arrange
        val input = """
            |ZDP{
            |COMMENT LINE
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ANOTHER COMMENT
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(1, log.samples.size)
    }

    @Test
    fun `sample with missing time field returns null and is skipped`() {
        // Arrange
        val input = """
            |ZDP{
            ||abc|5.0|0|0|0|0|0|25|extra
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(1, log.samples.size)
    }

    @Test
    fun `sample with missing depth field returns null and is skipped`() {
        // Arrange
        val input = """
            |ZDP{
            ||1.0|abc|0|0|0|0|0|25|extra
            ||2.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(1, log.samples.size)
    }

    @Test
    fun `factory creates Dl7 parser`() {
        // Arrange / Act
        val parser = Dl7Format.createParser()

        // Assert
        assertTrue(parser is Dl7DiveLogParser)
    }

    @Test
    fun `month and day leading zeros are trimmed`() {
        // Arrange
        val input = """
            |ZDH|1|2|3|4|20260101093000|extra
            |ZDP{
            ||1.0|5.0|0|0|0|0|0|25|extra
            |ZDP}
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals("1/1/2026 9:30:00 AM", log.metadata.startTime)
    }
}