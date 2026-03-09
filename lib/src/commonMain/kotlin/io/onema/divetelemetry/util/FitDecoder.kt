package io.onema.divetelemetry.util

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.FitMessage
import io.onema.divetelemetry.error.ParseError
import okio.BufferedSource

/**
 * Internal: one field inside a definition message.
 */
internal data class FieldDef(
    val fieldDefNum: Int,
    val size: Int,
    val baseType: Int
)

/**
 * Internal: one developer field inside a definition message.
 */
internal data class DevFieldDef(
    val fieldNum: Int,
    val size: Int,
    val devDataIndex: Int
)

/**
 * Internal: a definition message describing the layout of subsequent data messages.
 */
internal data class DefinitionMessage(
    val architecture: Int, // 0 = little-endian, 1 = big-endian
    val globalMessageNumber: Int,
    val fieldDefs: List<FieldDef>,
    val devFieldDefs: List<DevFieldDef>
)

// FIT base type sizes and invalid sentinels
private val BASE_TYPE_SIZES = mapOf(
    0x00 to 1, // enum
    0x01 to 1, // sint8
    0x02 to 1, // uint8
    0x83 to 2, // sint16
    0x84 to 2, // uint16
    0x85 to 4, // sint32
    0x86 to 4, // uint32
    0x88 to 4, // float32
    0x89 to 8, // float64
    0x8A to 1, // uint8z
    0x8B to 2, // uint16z
    0x8C to 4, // uint32z
    0x07 to 1, // string (per-byte)
    0x0D to 1, // byte array (per-byte)
    0x8E to 8, // uint64
    0x8F to 8, // sint64
    0x90 to 8, // uint64z
)

private val INVALID_VALUES: Map<Int, Long> = mapOf(
    0x00 to 0xFF, // enum
    0x01 to 0x7F, // sint8
    0x02 to 0xFF, // uint8
    0x83 to 0x7FFF, // sint16
    0x84 to 0xFFFF, // uint16
    0x85 to 0x7FFFFFFF, // sint32
    0x86 to 0xFFFFFFFFL, // uint32
    0x8A to 0x00, // uint8z
    0x8B to 0x0000, // uint16z
    0x8C to 0x00000000, // uint32z
)

/**
 * Decode a FIT binary file from the given source.
 * Returns a list of all data messages as [FitMessage].
 */
fun Raise<ParseError>.decodeFitFile(source: BufferedSource): List<FitMessage> {
    // --- File Header ---
    ensure(source.request(1)) { ParseError.InvalidFitFile("file is empty") }
    val headerSize = source.readByte().toInt() and 0xFF
    ensure(headerSize >= 12) { ParseError.InvalidFitFile("header size $headerSize < 12") }

    // Read remaining header bytes
    ensure(source.request((headerSize - 1).toLong())) {
        ParseError.InvalidFitFile("truncated header")
    }
    val headerRest = source.readByteArray((headerSize - 1).toLong())

    // headerRest[0] = protocol version, headerRest[1..2] = profile version (skip)
    // headerRest[3..6] = data size (little-endian uint32)
    val dataSize = (headerRest[3].toLong() and 0xFF) or
        ((headerRest[4].toLong() and 0xFF) shl 8) or
        ((headerRest[5].toLong() and 0xFF) shl 16) or
        ((headerRest[6].toLong() and 0xFF) shl 24)

    // headerRest[7..10] = ".FIT" signature
    val sig = headerRest.sliceArray(7..10)
    val fitSig = byteArrayOf('.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())
    ensure(sig.contentEquals(fitSig)) {
        ParseError.InvalidFitFile("missing .FIT signature")
    }
    // headerRest may have 2 more bytes (CRC) if headerSize == 14, already consumed

    // --- Data Records ---
    tailrec fun parseRecords(
        bytesRead: Long,
        definitions: Map<Int, DefinitionMessage>,
        messages: List<FitMessage>,
    ): List<FitMessage> {
        if (bytesRead >= dataSize) return messages

        ensure(source.request(1)) { ParseError.InvalidFitFile("truncated data at offset $bytesRead") }
        val recordHeader = source.readByte().toInt() and 0xFF

        return when {
            recordHeader and 0x80 != 0 -> {
                // Compressed timestamp header
                val localType = (recordHeader shr 5) and 0x03
                val def = definitions[localType]
                    ?: run { raise(ParseError.InvalidFitFile("compressed timestamp for undefined local type $localType")) }
                val (bytes, msg) = readDataMessage(source, def)
                parseRecords(bytesRead + 1 + bytes, definitions, messages + msg)
            }

            recordHeader and 0x40 != 0 -> {
                // Definition message
                val hasDeveloperData = (recordHeader and 0x20) != 0
                val localType = recordHeader and 0x0F
                val (bytes, def) = readDefinitionMessage(source, hasDeveloperData)
                parseRecords(bytesRead + 1 + bytes, definitions + (localType to def), messages)
            }

            else -> {
                // Normal data message
                val localType = recordHeader and 0x0F
                val def = definitions[localType]
                    ?: run { raise(ParseError.InvalidFitFile("data message for undefined local type $localType")) }
                val (bytes, msg) = readDataMessage(source, def)
                parseRecords(bytesRead + 1 + bytes, definitions, messages + msg)
            }
        }
    }

    val messages = parseRecords(0L, emptyMap(), emptyList())

    // Skip the 2-byte file CRC at the end (if present)
    if (source.request(2)) {
        source.skip(2)
    }

    return messages
}

/**
 * Read a definition message from source. Returns (bytes consumed, definition message).
 */
private fun Raise<ParseError>.readDefinitionMessage(
    source: BufferedSource,
    hasDeveloperData: Boolean
): Pair<Long, DefinitionMessage> {
    ensure(source.request(5)) { ParseError.InvalidFitFile("truncated definition message") }
    source.readByte() // reserved byte
    val architecture = source.readByte().toInt() and 0xFF
    val globalMsgNum = readUint16(source, architecture)
    val numFields = source.readByte().toInt() and 0xFF

    val fieldDefs = (0 until numFields).map {
        ensure(source.request(3)) { ParseError.InvalidFitFile("truncated field definition") }
        FieldDef(
            fieldDefNum = source.readByte().toInt() and 0xFF,
            size = source.readByte().toInt() and 0xFF,
            baseType = source.readByte().toInt() and 0xFF,
        )
    }

    val (devFieldDefs, devConsumed) = if (hasDeveloperData) {
        ensure(source.request(1)) { ParseError.InvalidFitFile("truncated dev field count") }
        val numDevFields = source.readByte().toInt() and 0xFF
        val defs = (0 until numDevFields).map {
            ensure(source.request(3)) { ParseError.InvalidFitFile("truncated dev field definition") }
            DevFieldDef(
                fieldNum = source.readByte().toInt() and 0xFF,
                size = source.readByte().toInt() and 0xFF,
                devDataIndex = source.readByte().toInt() and 0xFF,
            )
        }
        defs to (1L + numDevFields * 3L)
    } else {
        emptyList<DevFieldDef>() to 0L
    }

    val consumed = 5L + numFields * 3L + devConsumed
    return consumed to DefinitionMessage(architecture, globalMsgNum, fieldDefs, devFieldDefs)
}

/**
 * Read a data message according to its definition. Returns (bytes consumed, fit message).
 */
private fun Raise<ParseError>.readDataMessage(
    source: BufferedSource,
    def: DefinitionMessage
): Pair<Long, FitMessage> {
    val (fieldsConsumed, fields) = def.fieldDefs.fold(0L to emptyMap<Int, Any?>()) { (acc, map), fieldDef ->
        ensure(source.request(fieldDef.size.toLong())) {
            ParseError.InvalidFitFile("truncated data field ${fieldDef.fieldDefNum}")
        }
        val value = readFieldValue(source, fieldDef, def.architecture)
        (acc + fieldDef.size) to (map + (fieldDef.fieldDefNum to value))
    }

    // Skip developer fields
    val devConsumed = def.devFieldDefs.fold(0L) { acc, devField ->
        ensure(source.request(devField.size.toLong())) {
            ParseError.InvalidFitFile("truncated dev data field")
        }
        source.skip(devField.size.toLong())
        acc + devField.size
    }

    return (fieldsConsumed + devConsumed) to FitMessage(def.globalMessageNumber, fields)
}

/**
 * Read a single field value from the source. Returns null for invalid sentinels.
 */
private fun readFieldValue(source: BufferedSource, fieldDef: FieldDef, architecture: Int): Any? {
    val fullBaseType = fieldDef.baseType

    return when {
        // String type
        fullBaseType == 0x07 -> {
            val bytes = source.readByteArray(fieldDef.size.toLong())
            val nullIdx = bytes.indexOf(0)
            if (nullIdx >= 0) bytes.decodeToString(0, nullIdx) else bytes.decodeToString()
        }

        // Byte array
        fullBaseType == 0x0D -> {
            source.readByteArray(fieldDef.size.toLong())
        }

        // sint8
        fullBaseType == 0x01 -> {
            val v = source.readByte().toLong()
            if (fieldDef.size > 1) source.skip((fieldDef.size - 1).toLong())
            if (v == INVALID_VALUES[0x01]) null else v
        }

        // uint8 / enum / uint8z
        fullBaseType == 0x02 || fullBaseType == 0x00 || fullBaseType == 0x8A -> {
            val v = source.readByte().toLong() and 0xFF
            if (fieldDef.size > 1) source.skip((fieldDef.size - 1).toLong())
            val invalid = INVALID_VALUES[fullBaseType]
            if (invalid != null && v == invalid) null else v
        }

        // sint16
        fullBaseType == 0x83 -> {
            val v = readSint16(source, architecture)
            if (fieldDef.size > 2) source.skip((fieldDef.size - 2).toLong())
            if (v == INVALID_VALUES[0x83]) null else v
        }

        // uint16 / uint16z
        fullBaseType == 0x84 || fullBaseType == 0x8B -> {
            val v = readUint16(source, architecture).toLong()
            if (fieldDef.size > 2) source.skip((fieldDef.size - 2).toLong())
            val invalid = INVALID_VALUES[fullBaseType]
            if (invalid != null && v == invalid) null else v
        }

        // sint32
        fullBaseType == 0x85 -> {
            val v = readSint32(source, architecture)
            if (fieldDef.size > 4) source.skip((fieldDef.size - 4).toLong())
            if (v == INVALID_VALUES[0x85]) null else v
        }

        // uint32 / uint32z
        fullBaseType == 0x86 || fullBaseType == 0x8C -> {
            val v = readUint32(source, architecture)
            if (fieldDef.size > 4) source.skip((fieldDef.size - 4).toLong())
            val invalid = INVALID_VALUES[fullBaseType]
            if (invalid != null && v == invalid) null else v
        }

        // float32
        fullBaseType == 0x88 -> {
            val bits = readUint32(source, architecture).toInt()
            if (fieldDef.size > 4) source.skip((fieldDef.size - 4).toLong())
            Float.fromBits(bits).toDouble()
        }

        // float64
        fullBaseType == 0x89 -> {
            val bytes = source.readByteArray(fieldDef.size.toLong())
            val bits = if (architecture == 0) {
                // little-endian
                (0 until 8).fold(0L) { acc, i -> acc or ((bytes[i].toLong() and 0xFF) shl (i * 8)) }
            } else {
                (0 until 8).fold(0L) { acc, i -> acc or ((bytes[i].toLong() and 0xFF) shl ((7 - i) * 8)) }
            }
            Double.fromBits(bits)
        }

        // uint64, sint64, uint64z — just read as long
        fullBaseType == 0x8E || fullBaseType == 0x8F || fullBaseType == 0x90 -> {
            val bytes = source.readByteArray(fieldDef.size.toLong())
            if (architecture == 0) {
                (0 until 8).fold(0L) { acc, i -> acc or ((bytes[i].toLong() and 0xFF) shl (i * 8)) }
            } else {
                (0 until 8).fold(0L) { acc, i -> acc or ((bytes[i].toLong() and 0xFF) shl ((7 - i) * 8)) }
            }
        }

        else -> {
            // Unknown base type — skip the bytes
            source.skip(fieldDef.size.toLong())
            null
        }
    }
}

private fun readUint16(source: BufferedSource, architecture: Int): Int {
    val b0 = source.readByte().toInt() and 0xFF
    val b1 = source.readByte().toInt() and 0xFF
    return if (architecture == 0) (b1 shl 8) or b0 else (b0 shl 8) or b1
}

private fun readSint16(source: BufferedSource, architecture: Int): Long {
    val unsigned = readUint16(source, architecture)
    return if (unsigned >= 0x8000) (unsigned - 0x10000).toLong() else unsigned.toLong()
}

private fun readUint32(source: BufferedSource, architecture: Int): Long {
    val b0 = source.readByte().toLong() and 0xFF
    val b1 = source.readByte().toLong() and 0xFF
    val b2 = source.readByte().toLong() and 0xFF
    val b3 = source.readByte().toLong() and 0xFF
    return if (architecture == 0) {
        (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    } else {
        (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}

private fun readSint32(source: BufferedSource, architecture: Int): Long {
    val unsigned = readUint32(source, architecture)
    return if (unsigned >= 0x80000000L) (unsigned - 0x100000000L) else unsigned
}
