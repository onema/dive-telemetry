package io.onema.divetelemetry.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import io.onema.divetelemetry.service.ShearwaterFormat
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.error.ParseError
import io.onema.divetelemetry.error.PipelineError
import io.onema.divetelemetry.plugins.DiveLogPlugin
import io.onema.divetelemetry.plugins.InterpolationPlugin
import io.onema.divetelemetry.plugins.OutputPlugin
import io.onema.divetelemetry.plugins.PluginError
import io.onema.divetelemetry.plugins.PluginParameter
import io.onema.divetelemetry.plugins.SafetyStopPlugin
import io.onema.divetelemetry.plugins.TechnicalCCRPlugin
import io.onema.divetelemetry.plugins.TechnicalOCPlugin
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class PipelinePluginTest {

    @Test
    fun `pipeline with no plugins produces same output as before`() {
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val expectedPath = "src/commonTest/resources/2025-12-7-ellen-telemetry.csv".toPath()
        val expectedBytes = FileSystem.SYSTEM.read(expectedPath) { readByteArray() }
        val outputBuffer = Buffer()

        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source, outputBuffer, ShearwaterFormat, plugins = emptyList())
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source.close()
        }

        assertTrue(expectedBytes.contentEquals(outputBuffer.readByteArray()))
    }

    @Test
    fun `pipeline applies plugin that modifies dive log`() {
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val withoutPlugins = Buffer()
        val withPlugins = Buffer()

        val truncatePlugin = object : DiveLogPlugin {
            override val id = "test.truncate"
            override val name = "Truncate"
            override val description = "Keep only first 2 samples"
            override val parameters: List<PluginParameter<*>> = emptyList()
            override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
                return diveLog.copy(samples = diveLog.samples.take(2))
            }
        }

        val source1 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source1, withoutPlugins, ShearwaterFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source1.close()
        }

        val source2 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source2, withPlugins, ShearwaterFormat, plugins = listOf(truncatePlugin))
                .getOrElse { fail("Pipeline with plugin failed: ${it.message}") }
        } finally {
            source2.close()
        }

        val normalLines = withoutPlugins.readByteArray().decodeToString(3).split("\r\n")
        val truncatedLines = withPlugins.readByteArray().decodeToString(3).split("\r\n")

        // Header + 2 data rows
        assertEquals(3, truncatedLines.size)
        assertTrue(normalLines.size > truncatedLines.size)
        // Headers match
        assertEquals(normalLines[0], truncatedLines[0])
    }

    @Test
    fun `pipeline chains multiple plugins in order`() {
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputBuffer = Buffer()

        val executionOrder = mutableListOf<String>()

        fun createOrderPlugin(pluginId: String) = object : DiveLogPlugin {
            override val id = pluginId
            override val name = pluginId
            override val description = ""
            override val parameters: List<PluginParameter<*>> = emptyList()
            override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
                executionOrder.add(pluginId)
                return diveLog
            }
        }

        val pluginA = createOrderPlugin("a")
        val pluginB = createOrderPlugin("b")
        val pluginC = createOrderPlugin("c")

        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(
                source, outputBuffer, ShearwaterFormat,
                plugins = listOf(pluginA, pluginB, pluginC)
            ).getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source.close()
        }

        assertEquals(listOf("a", "b", "c"), executionOrder)
    }

    @Test
    fun `pipeline maps plugin error to PluginError`() {
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputBuffer = Buffer()

        val failingPlugin = object : DiveLogPlugin {
            override val id = "test.fail"
            override val name = "Failing Plugin"
            override val description = ""
            override val parameters: List<PluginParameter<*>> = emptyList()
            override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
                raise(PluginError.ExecutionError("Something went wrong"))
            }
        }

        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            val result = transformDiveLog(
                source, outputBuffer, ShearwaterFormat,
                plugins = listOf(failingPlugin),
            )
            assertIs<Either.Left<*>>(result)
            val error = result.value
            assertIs<PluginError.ExecutionError>(error)
            assertTrue(error.message.contains("Something went wrong"))
        } finally {
            source.close()
        }
    }

    @Test
    fun `plugin failure stops chain — subsequent plugins do not run`() {
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputBuffer = Buffer()
        var secondPluginRan = false

        val failingPlugin = object : DiveLogPlugin {
            override val id = "test.fail"
            override val name = "Failing"
            override val description = ""
            override val parameters: List<PluginParameter<*>> = emptyList()
            override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
                raise(PluginError.ExecutionError("boom"))
            }
        }
        val secondPlugin = object : DiveLogPlugin {
            override val id = "test.second"
            override val name = "Second"
            override val description = ""
            override val parameters: List<PluginParameter<*>> = emptyList()
            override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
                secondPluginRan = true
                return diveLog
            }
        }

        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(
                source, outputBuffer, ShearwaterFormat,
                plugins = listOf(failingPlugin, secondPlugin),
            )
        } finally {
            source.close()
        }

        assertEquals(false, secondPluginRan)
    }

    @Test
    fun `pipeline with output plugins appends headers and columns`() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val baseBuffer = Buffer()
        val enrichedBuffer = Buffer()

        // Act
        val source1 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(source1, baseBuffer, ShearwaterFormat)
                .getOrElse { fail("Pipeline failed: ${it.message}") }
        } finally {
            source1.close()
        }

        val source2 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(
                source2, enrichedBuffer, ShearwaterFormat,
                outputPlugins = listOf(TechnicalOCPlugin),
            ).getOrElse { fail("Pipeline with output plugins failed: ${it.message}") }
        } finally {
            source2.close()
        }

        // Assert
        val baseLines = baseBuffer.readByteArray().decodeToString(3).split("\r\n")
        val enrichedLines = enrichedBuffer.readByteArray().decodeToString(3).split("\r\n")

        val baseHeaders = baseLines[0].split(",")
        val enrichedHeaders = enrichedLines[0].split(",")

        assertEquals(24, baseHeaders.size)
        assertEquals(24 + 16, enrichedHeaders.size)
        assertTrue(enrichedHeaders.contains("White NDL (text)"))
        assertTrue(enrichedHeaders.contains("Cleared TTS Label (text)"))
        assertTrue(enrichedHeaders.contains("NDL Before Clear (text)"))
        assertTrue(enrichedHeaders.contains("NDL Before Clear Time (text)"))

        assertEquals(baseLines.size, enrichedLines.size)
    }

    @Test
    fun `pipeline with all output plugins produces 49 columns`() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputBuffer = Buffer()

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(
                source, outputBuffer, ShearwaterFormat,
                outputPlugins = listOf(
                    TechnicalOCPlugin,
                    TechnicalCCRPlugin,
                    SafetyStopPlugin,
                ),
            ).getOrElse { fail("Pipeline with all output plugins failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val lines = outputBuffer.readByteArray().decodeToString(3).split("\r\n")
        val headers = lines[0].split(",")
        assertEquals(24 + 16 + 8 + 1, headers.size)

        for (i in 1 until lines.size) {
            val fields = lines[i].split(",")
            assertEquals(headers.size, fields.size, "Row $i should have ${headers.size} fields")
        }
    }

    @Test
    fun `output plugin error is mapped to PluginError`() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputBuffer = Buffer()

        val failingOutputPlugin = object : OutputPlugin {
            override val id = "test.fail-output"
            override val name = "Failing Output"
            override val description = ""
            override val parameters: List<PluginParameter<*>> = emptyList()
            override fun additionalHeaders(metadata: DiveMetadata) = listOf("X")
            override fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>> {
                raise(PluginError.ExecutionError("output plugin boom"))
            }
        }

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            val result = transformDiveLog(
                source, outputBuffer, ShearwaterFormat,
                outputPlugins = listOf(failingOutputPlugin),
            )

            // Assert
            assertIs<Either.Left<*>>(result)
            val error = result.value
            assertIs<PluginError.ExecutionError>(error)
            assertTrue(error.message.contains("output plugin boom"))
        } finally {
            source.close()
        }
    }

    @Test
    fun `output plugin row count mismatch is mapped to PluginError`() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputBuffer = Buffer()

        val badPlugin = object : OutputPlugin {
            override val id = "test.bad-row-count"
            override val name = "Bad Row Count"
            override val description = ""
            override val parameters: List<PluginParameter<*>> = emptyList()
            override fun additionalHeaders(metadata: DiveMetadata) = listOf("X")
            override fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>> {
                return listOf(mapOf("X" to "only-one-row"))
            }
        }

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            val result = transformDiveLog(
                source, outputBuffer, ShearwaterFormat,
                outputPlugins = listOf(badPlugin),
            )

            // Assert
            assertIs<Either.Left<*>>(result)
            val error = result.value
            assertIs<PluginError.ExecutionError>(error)
            assertTrue(error.message.contains("Bad Row Count"))
        } finally {
            source.close()
        }
    }

    @Test
    fun `parse error propagates as ParseError on malformed input`() {
        // Arrange
        val malformedCsv = "only-one-line-no-metadata-values\n"
        val source = Buffer().apply { writeUtf8(malformedCsv) }
        val outputBuffer = Buffer()

        // Act
        val result = transformDiveLog(source, outputBuffer, ShearwaterFormat)

        // Assert
        assertIs<Either.Left<PipelineError>>(result)
        assertIs<ParseError.UnexpectedEof>(result.value)
    }

    @Test
    fun `parse error propagates as MissingColumns when data headers lack required columns`() {
        // Arrange
        val csv = buildString {
            appendLine("Imperial Units")
            appendLine("true")
            appendLine("Time (sec),Depth")
            appendLine("0,10.0")
        }
        val source = Buffer().apply { writeUtf8(csv) }
        val outputBuffer = Buffer()

        // Act
        val result = transformDiveLog(source, outputBuffer, ShearwaterFormat)

        // Assert
        assertIs<Either.Left<PipelineError>>(result)
        assertIs<ParseError.MissingColumns>(result.value)
        val missing = (result.value as ParseError.MissingColumns).missing
        assertTrue(missing.contains("Average PPO2"))
        assertTrue(missing.contains("Fraction O2"))
    }

    @Test
    fun `combined DiveLogPlugin and OutputPlugin in same pipeline`() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputBuffer = Buffer()

        // Act
        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(
                source, outputBuffer, ShearwaterFormat,
                plugins = listOf(InterpolationPlugin),
                outputPlugins = listOf(TechnicalOCPlugin),
            ).getOrElse { fail("Pipeline with both plugin types failed: ${it.message}") }
        } finally {
            source.close()
        }

        // Assert
        val lines = outputBuffer.readByteArray().decodeToString(3).split("\r\n")
        val headers = lines[0].split(",")

        assertEquals(24 + 16, headers.size)
        assertTrue(headers.contains("White NDL (text)"))

        val dataRows = lines.drop(1)
        assertTrue(dataRows.size > 1)

        for (i in dataRows.indices) {
            val fields = dataRows[i].split(",")
            assertEquals(headers.size, fields.size, "Row $i should have ${headers.size} fields")
        }
    }

    @Test
    fun `combined DiveLogPlugin and multiple OutputPlugins produces expected column count`() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val withoutInterpolation = Buffer()
        val withInterpolation = Buffer()

        // Act
        val source1 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(
                source1, withoutInterpolation, ShearwaterFormat,
                outputPlugins = listOf(TechnicalOCPlugin, TechnicalCCRPlugin, SafetyStopPlugin),
            ).getOrElse { fail("Pipeline without interpolation failed: ${it.message}") }
        } finally {
            source1.close()
        }

        val source2 = FileSystem.SYSTEM.source(inputPath).buffer()
        try {
            transformDiveLog(
                source2, withInterpolation, ShearwaterFormat,
                plugins = listOf(InterpolationPlugin),
                outputPlugins = listOf(TechnicalOCPlugin, TechnicalCCRPlugin, SafetyStopPlugin),
            ).getOrElse { fail("Pipeline with interpolation failed: ${it.message}") }
        } finally {
            source2.close()
        }

        // Assert
        val normalLines = withoutInterpolation.readByteArray().decodeToString(3).split("\r\n")
        val interpolatedLines = withInterpolation.readByteArray().decodeToString(3).split("\r\n")

        val normalHeaders = normalLines[0].split(",")
        val interpolatedHeaders = interpolatedLines[0].split(",")

        assertEquals(24 + 16 + 8 + 1, normalHeaders.size)
        assertEquals(normalHeaders.size, interpolatedHeaders.size)
        assertEquals(normalHeaders, interpolatedHeaders)

        assertTrue(interpolatedLines.size > normalLines.size)
    }
}
