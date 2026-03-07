package io.onema.divetelemetry.service

import arrow.core.raise.Raise
import arrow.core.raise.catch
import io.onema.divetelemetry.domain.TelemetryOutput
import io.onema.divetelemetry.error.WriteError
import io.onema.divetelemetry.util.joinCsvFields
import okio.BufferedSink

class CsvWriter {
    companion object {
        private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private const val CRLF = "\r\n"
    }

    /**
     * @receiver Raise context for signalling a [WriteError] on I/O failure.
     * @param output The formatted telemetry data to write.
     * @param sink The destination stream.
     */
    fun Raise<WriteError>.write(output: TelemetryOutput, sink: BufferedSink) {
        catch({
            sink.write(BOM)
            sink.writeUtf8(joinCsvFields(output.headers))

            output.rows.forEach { row ->
                sink.writeUtf8(CRLF)
                val fields = output.headers.map { header -> row.values[header] ?: "" }
                sink.writeUtf8(joinCsvFields(fields))
            }

            sink.flush()
        }) { e: Throwable ->
            raise(WriteError.IoFailure(e.message ?: "Unknown I/O error"))
        }
    }
}
