package io.onema.divetelemetry

import arrow.core.getOrElse
import io.onema.divetelemetry.service.ShearwaterFormat
import io.onema.divetelemetry.service.transformDiveLog
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class PlaygroundTest {
    @Test
    fun convertShearwaterCsv() {
        // Arrange
        val inputPath = "src/commonTest/resources/2025-12-7-ellen.csv".toPath()
        val outputPath = "build/2025-12-7-ellen-telemetry.csv".toPath()

        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        val sink = FileSystem.SYSTEM.sink(outputPath).buffer()

        // Act
        try {
            transformDiveLog(source, sink, ShearwaterFormat)
                .getOrElse { fail("Transform failed: ${it.message}") }
        } finally {
            source.close()
            sink.close()
        }

        // Assert
        assertTrue(FileSystem.SYSTEM.exists(outputPath), "Output file was not created")
    }
}
