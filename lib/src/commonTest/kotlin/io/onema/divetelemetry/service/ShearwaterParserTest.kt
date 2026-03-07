package io.onema.divetelemetry.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.onema.divetelemetry.service.ShearwaterFormat
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.error.ParseError
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class ShearwaterParserTest {

    private val sampleInput = """
        |Dive Number,GF Minimum,Product,Imperial Units,Start Date
        |398,20,Perdix 2,True,12/7/2025 8:39:59 PM
        |Time (sec),Depth,Average PPO2,Fraction O2,Fraction He,Water Temp,Time To Surface (min),Gas Switch Needed,External PPO2
        |0,0,0.99,0.18,0.0,77,0,False,True
        |5,6.4,0.99,0.18,0.0,77,0,False,True
        |10,12.3,0.99,0.18,0.0,77,0,True,True
    """.trimMargin()

    private fun bufferOf(text: String): Buffer = Buffer().apply { writeUtf8(text) }

    private fun parseOrFail(input: String): DiveLog = either<ParseError, DiveLog> {
        with(ShearwaterDiveLogParser()) { parse(bufferOf(input)) }
    }.getOrElse { fail("Expected successful parse but got: $it") }

    @Test
    fun `parses metadata with typed fields`() {
        // Act
        val log = parseOrFail(sampleInput)

        // Assert
        assertEquals(DepthUnit.FT, log.metadata.depthUnit)
        assertEquals(TempUnit.FAHRENHEIT, log.metadata.tempUnit)
        assertEquals("12/7/2025 8:39:59 PM", log.metadata.startTime)
        assertEquals("398", log.metadata.extra["Dive Number"])
        assertEquals("20", log.metadata.extra["GF Minimum"])
        assertEquals("Perdix 2", log.metadata.extra["Product"])
    }

    @Test
    fun `parses data samples with typed values`() {
        // Act
        val log = parseOrFail(sampleInput)

        // Assert
        assertEquals(3, log.samples.size)

        val first = log.samples[0]
        assertEquals(0L, first.timeSeconds)
        assertEquals(0.0, first.depth)
        assertEquals(0.99, first.avgPpo2)
        assertEquals(0.18, first.fractionO2)
        assertEquals(0.0, first.fractionHe)
        assertEquals(77.0, first.waterTemp)
        assertEquals(false, first.gasSwitchNeeded)
        assertEquals(true, first.externalPpo2)

        val second = log.samples[1]
        assertEquals(5L, second.timeSeconds)
        assertEquals(6.4, second.depth)

        val third = log.samples[2]
        assertEquals(10L, third.timeSeconds)
        assertEquals(12.3, third.depth)
        assertEquals(true, third.gasSwitchNeeded)
    }

    @Test
    fun `factory creates shearwater parser`() {
        // Arrange / Act
        val parser = ShearwaterFormat.createParser()

        // Assert
        assertTrue(parser is ShearwaterDiveLogParser)
    }

    @Test
    fun `parses empty data section`() {
        // Arrange
        val input = """
            |MetaH1,MetaH2
            |V1,V2
            |Time (sec),Depth,Average PPO2,Fraction O2,Fraction He,Water Temp,Time To Surface (min),Gas Switch Needed,External PPO2
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(0, log.samples.size)
    }

    @Test
    fun `empty input returns UnexpectedEof`() {
        // Arrange
        val result: Either<ParseError, DiveLog> = either {
            with(ShearwaterDiveLogParser()) { parse(bufferOf("")) }
        }

        // Assert
        val error = assertIs<Either.Left<ParseError>>(result).value
        val eof = assertIs<ParseError.UnexpectedEof>(error)
        assertEquals("metadata headers (row 1)", eof.expectedRow)
    }

    @Test
    fun `one line input returns UnexpectedEof`() {
        // Arrange
        val result: Either<ParseError, DiveLog> = either {
            with(ShearwaterDiveLogParser()) { parse(bufferOf("Dive Number,GF Minimum")) }
        }

        // Assert
        val error = assertIs<Either.Left<ParseError>>(result).value
        val eof = assertIs<ParseError.UnexpectedEof>(error)
        assertEquals("metadata values (row 2)", eof.expectedRow)
    }

    @Test
    fun `missing required columns returns MissingColumns`() {
        // Arrange
        val input = """
            |Actual Depth (ft),Average PP02 (text),Fraction O2 (text)
            |V1,V2,V3
            |H1,H2,H3
        """.trimMargin()
        val result: Either<ParseError, DiveLog> = either {
            with(ShearwaterDiveLogParser()) { parse(bufferOf(input)) }
        }

        // Assert
        val error = assertIs<Either.Left<ParseError>>(result).value
        val missing = assertIs<ParseError.MissingColumns>(error)
        assertTrue(missing.missing.contains("Depth"))
        assertTrue(missing.missing.contains("Time (sec)"))
    }

    @Test
    fun `two line input returns UnexpectedEof`() {
        // Arrange
        val input = """
            |Dive Number,GF Minimum
            |398,20
        """.trimMargin()
        val result: Either<ParseError, DiveLog> = either {
            with(ShearwaterDiveLogParser()) { parse(bufferOf(input)) }
        }

        // Assert
        val error = assertIs<Either.Left<ParseError>>(result).value
        val eof = assertIs<ParseError.UnexpectedEof>(error)
        assertEquals("data headers (row 3)", eof.expectedRow)
    }

    @Test
    fun `metric units set depthUnit and tempUnit correctly`() {
        // Arrange
        val input = """
            |Product,Imperial Units
            |Perdix 2,False
            |Time (sec),Depth,Average PPO2,Fraction O2,Fraction He,Water Temp,Time To Surface (min),Gas Switch Needed,External PPO2
            |0,0,0.99,0.18,0.0,25,0,False,True
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(DepthUnit.M, log.metadata.depthUnit)
        assertEquals(TempUnit.CELSIUS, log.metadata.tempUnit)
    }

    @Test
    fun `missing Imperial Units defaults to imperial`() {
        // Arrange
        val input = """
            |Product
            |Perdix 2
            |Time (sec),Depth,Average PPO2,Fraction O2,Fraction He,Water Temp,Time To Surface (min),Gas Switch Needed,External PPO2
            |0,0,0.99,0.18,0.0,77,0,False,True
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(DepthUnit.FT, log.metadata.depthUnit)
        assertEquals(TempUnit.FAHRENHEIT, log.metadata.tempUnit)
    }

    @Test
    fun `currentNdl is null when field is empty`() {
        // Arrange
        val input = """
            |Product,Imperial Units
            |Perdix 2,True
            |Time (sec),Depth,Average PPO2,Fraction O2,Fraction He,Water Temp,Time To Surface (min),Gas Switch Needed,External PPO2,Current NDL
            |0,0,0.99,0.18,0.0,77,0,False,True,
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(null, log.samples[0].currentNdl)
    }

    @Test
    fun `currentNdl is parsed when present`() {
        // Arrange
        val input = """
            |Product,Imperial Units
            |Perdix 2,True
            |Time (sec),Depth,Average PPO2,Fraction O2,Fraction He,Water Temp,Time To Surface (min),Gas Switch Needed,External PPO2,Current NDL
            |0,0,0.99,0.18,0.0,77,0,False,True,99
        """.trimMargin()

        // Act
        val log = parseOrFail(input)

        // Assert
        assertEquals(99L, log.samples[0].currentNdl)
    }
}
