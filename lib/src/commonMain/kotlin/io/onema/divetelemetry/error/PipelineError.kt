package io.onema.divetelemetry.error

/** Top-level error type returned by the pipeline. Covers parsing, writing, and plugin failures. */
interface PipelineError {
    val message: String
}

/** Errors raised while reading and interpreting the source file. */
sealed interface ParseError : PipelineError {
    data class UnexpectedEof(val expectedRow: String) : ParseError {
        override val message: String = "Unexpected end of input while reading $expectedRow"
    }

    data class InvalidFitFile(val reason: String) : ParseError {
        override val message: String = "Invalid FIT file: $reason"
    }

    data class MissingFitData(val field: String) : ParseError {
        override val message: String = "FIT file missing required dive data: $field"
    }

    data class MissingColumns(val missing: List<String>) : ParseError {
        override val message: String = "Missing required columns: ${missing.joinToString(", ")}"
    }
}

/** Errors raised while writing the output file. */
sealed interface WriteError : PipelineError {
    data class IoFailure(val cause: String) : WriteError {
        override val message: String get() = "I/O error: $cause"
    }
}
