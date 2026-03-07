package io.onema.divetelemetry.util

/**
 * Splits a CSV line into fields following RFC 4180 rules:
 * - Fields may be enclosed in double quotes
 * - Quoted fields may contain commas and escaped double quotes (`""`)
 * - Unquoted fields are split on commas
 */
fun splitCsvLine(line: String): List<String> {
    if (line.isEmpty()) return listOf("")

    val fields = mutableListOf<String>()
    var i = 0

    while (i <= line.length) {
        if (i == line.length) {
            fields.add("")
            break
        }

        if (line[i] == '"') {
            val sb = StringBuilder()
            i++ // skip opening quote
            while (i < line.length) {
                if (line[i] == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i += 2
                    } else {
                        i++ // skip closing quote
                        break
                    }
                } else {
                    sb.append(line[i])
                    i++
                }
            }
            fields.add(sb.toString())
            if (i < line.length && line[i] == ',') i++ // skip comma after quoted field
        } else {
            val commaIndex = line.indexOf(',', i)
            if (commaIndex == -1) {
                fields.add(line.substring(i))
                break
            } else {
                fields.add(line.substring(i, commaIndex))
                i = commaIndex + 1
            }
        }
    }

    return fields
}

/**
 * Joins fields into a CSV line following RFC 4180 rules:
 * fields containing commas, double quotes, or newlines are wrapped in double quotes,
 * with internal double quotes escaped as `""`.
 */
fun joinCsvFields(fields: List<String>): String {
    return fields.joinToString(",") { field ->
        if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
