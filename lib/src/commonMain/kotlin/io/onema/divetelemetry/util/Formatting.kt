package io.onema.divetelemetry.util

import kotlin.math.abs
import kotlin.math.round

/**
 * Formats [value] to at most two decimal places using integer arithmetic to avoid floating-point artifacts.
 *
 * @param value The value to format.
 * @return Formatted string with trailing zeros stripped (e.g. `1.50` → `"1.5"`).
 */
fun formatTwoDecimals(value: Double): String {
    val hundredths = round(value * 100).toLong()
    val whole = hundredths / 100
    val frac = abs(hundredths % 100)
    return when {
        frac == 0L -> whole.toString()
        frac % 10 == 0L -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
}

/**
 * Negates [depth] for display (Telemetry  expects negative values below the surface).
 *
 * @param depth Positive depth value in source units.
 * @return Negated formatted string; zero is returned as-is.
 */
fun formatNegatedDepth(depth: Double): String {
    if (depth == 0.0) return formatTwoDecimals(depth)
    val formatted = formatTwoDecimals(depth)
    return if (formatted.startsWith("-")) formatted.substring(1) else "-$formatted"
}

fun fractionToPercentStr(fraction: Double): String =
    round(fraction * 100).toLong().toString()

fun formatBoolean(value: Boolean): String =
    if (value) "TRUE" else "FALSE"

fun formatWaterTemp(temp: Double): String =
    round(temp).toLong().toString()

fun formatMinSec(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * @param fractionO2 Oxygen fraction (0.0–1.0).
 * @param fractionHe Helium fraction (0.0–1.0).
 * @return `"Air"` for 21% O2/0% He, `"Nx{pct}"` for nitrox, or `""` for trimix.
 */
fun gasMixture(fractionO2: Double, fractionHe: Double): String {
    val o2Pct = round(fractionO2 * 100).toLong()
    val hePct = round(fractionHe * 100).toLong()
    return when {
        hePct > 0 -> ""
        o2Pct == 21L -> "Air"
        else -> "Nx$o2Pct"
    }
}
