package io.onema.divetelemetry.service

import arrow.core.raise.Raise
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.error.ParseError
import okio.BufferedSource

/** Parses a dive computer export into a typed [DiveLog]. */
sealed interface DiveLogParser {
    /**
     * @receiver Raise context for signalling a [ParseError] on malformed or incomplete input.
     * @param source Raw bytes of the dive computer export file.
     * @return The parsed [DiveLog].
     */
    fun Raise<ParseError>.parse(source: BufferedSource): DiveLog
}
