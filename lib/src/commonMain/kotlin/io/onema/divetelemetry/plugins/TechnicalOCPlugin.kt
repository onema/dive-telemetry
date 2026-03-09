package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.util.formatMinSec
import io.onema.divetelemetry.util.formatTwoDecimals
import io.onema.divetelemetry.util.gasMixture

object TechnicalOCPlugin : OutputPlugin {
    override val id: String = "core.technical-oc"
    override val name: String = "Technical Open Circuit"
    override val description: String = "Adds NDL state machine, decompression tracking, and deco clear columns."

    override val parameters: List<PluginParameter<*>> = listOf(
        BooleanParameter(
            key = "enabled",
            name = name,
            description = description,
            defaultValue = true,
        ),
    )

    override fun additionalHeaders(metadata: DiveMetadata): List<String> {
        val depthSuffix = when (metadata.depthUnit) {
            DepthUnit.FT -> "ft"
            DepthUnit.M -> "m"
        }
        return listOf(
            "White NDL (text)",
            "Yellow NDL (text)",
            "Red NDL (text)",
            "NDL Label (text)",
            "Decompression Ceiling",
            "First Stop Depth Label (text)",
            "Stop Time Label (text)",
            "Deco Label (text)",
            "Deco Measurement (text)",
            "Deco Minute Indicator (text)",
            "Clear Label (text)",
            "Clear Time (text)",
            "Cleared Gas Mix Label (text)",
            "Cleared TTS Label (text)",
            "NDL Before Clear (text)",
            "NDL Before Clear Time (text)",
        )
    }

    override fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>> {
        ensure(log.samples.isNotEmpty()) {
            PluginError.ExecutionError("TechnicalOCPlugin requires at least one sample.")
        }

        val depthSuffix = when (log.metadata.depthUnit) {
            DepthUnit.FT -> "ft"
            DepthUnit.M -> "m"
        }

        val states = computeOCStates(log.samples)

        return log.samples.zip(states) { sample, state ->
            val ndl = computeNdlColumns(sample.currentNdl, state.beginningPhase)
            val deco = computeDecoColumns(sample, state, depthSuffix)
            val clear = computeClearColumns(state, sample, log.metadata.depthUnit)

            mapOf(
                "White NDL (text)" to ndl.white,
                "Yellow NDL (text)" to ndl.yellow,
                "Red NDL (text)" to ndl.red,
                "NDL Label (text)" to ndl.label,
                "Decompression Ceiling" to deco.ceiling,
                "First Stop Depth Label (text)" to deco.firstStopLabel,
                "Stop Time Label (text)" to deco.stopTimeLabel,
                "Deco Label (text)" to deco.decoLabel,
                "Deco Measurement (text)" to deco.decoMeasurement,
                "Deco Minute Indicator (text)" to deco.decoMinuteIndicator,
                "Clear Label (text)" to clear.clearLabel,
                "Clear Time (text)" to clear.clearTime,
                "Cleared Gas Mix Label (text)" to clear.clearedGasMixLabel,
                "Cleared TTS Label (text)" to clear.clearedTtsLabel,
                "NDL Before Clear (text)" to clear.ndlBeforeClear,
                "NDL Before Clear Time (text)" to clear.ndlBeforeClearTime,
            )
        }
    }

    private data class OCState(
        val beginningPhase: Boolean,
        val inDeco: Boolean,
        val wasInDeco: Boolean,
        val decoClearedAtTime: Long?,
        val lastNdlValue: Long?,
        val ndlBeforeDeco: Long?,
        val ndlBeforeDecoTime: Long?,
    )

    private fun computeOCStates(samples: List<DiveSample>): List<OCState> {
        val initial = OCState(
            beginningPhase = true,
            inDeco = false,
            wasInDeco = false,
            decoClearedAtTime = null,
            lastNdlValue = null,
            ndlBeforeDeco = null,
            ndlBeforeDecoTime = null,
        )

        return samples
            .runningFold(initial) { state, sample ->
                val newBeginningPhase = when {
                    !state.beginningPhase -> false
                    sample.currentNdl != null && sample.currentNdl >= 50 -> false
                    else -> true
                }

                val currentInDeco = sample.currentNdl == 0L || sample.firstStopDepth > 0.0

                val newWasInDeco = state.wasInDeco || (!newBeginningPhase && currentInDeco)

                val newDecoClearedAtTime = when {
                    currentInDeco -> null
                    newBeginningPhase -> null
                    !state.beginningPhase && state.inDeco && !currentInDeco -> sample.timeSeconds
                    else -> state.decoClearedAtTime
                }

                val newLastNdlValue = when {
                    newBeginningPhase -> null
                    !currentInDeco -> sample.currentNdl ?: state.lastNdlValue
                    else -> state.lastNdlValue
                }

                val enteringDeco = !state.beginningPhase && !state.inDeco && currentInDeco
                val newNdlBeforeDeco = when {
                    state.ndlBeforeDeco != null -> state.ndlBeforeDeco
                    enteringDeco -> state.lastNdlValue
                    else -> null
                }
                val newNdlBeforeDecoTime = when {
                    state.ndlBeforeDecoTime != null -> state.ndlBeforeDecoTime
                    enteringDeco -> sample.timeSeconds
                    else -> null
                }

                OCState(
                    beginningPhase = newBeginningPhase,
                    inDeco = currentInDeco,
                    wasInDeco = newWasInDeco,
                    decoClearedAtTime = newDecoClearedAtTime,
                    lastNdlValue = newLastNdlValue,
                    ndlBeforeDeco = newNdlBeforeDeco,
                    ndlBeforeDecoTime = newNdlBeforeDecoTime,
                )
            }.drop(1)
    }

    private data class NdlColumns(
        val white: String,
        val yellow: String,
        val red: String,
        val label: String,
    )

    private val EMPTY_NDL_COLUMNS = NdlColumns("", "", "", "")

    private fun computeNdlColumns(currentNdl: Long?, beginningPhase: Boolean): NdlColumns {
        if (beginningPhase) return EMPTY_NDL_COLUMNS
        if (currentNdl == null) return EMPTY_NDL_COLUMNS

        return when {
            currentNdl > 5 -> NdlColumns(
                white = currentNdl.toString(),
                yellow = "",
                red = "",
                label = "NDL",
            )

            currentNdl in 1..5 -> NdlColumns(
                white = "",
                yellow = currentNdl.toString(),
                red = "",
                label = "NDL",
            )

            else -> NdlColumns(
                white = "",
                yellow = "",
                red = currentNdl.toString(),
                label = "",
            )
        }
    }

    private data class DecoColumns(
        val ceiling: String,
        val firstStopLabel: String,
        val stopTimeLabel: String,
        val decoLabel: String,
        val decoMeasurement: String,
        val decoMinuteIndicator: String,
    )

    private val EMPTY_DECO_COLUMNS = DecoColumns("", "", "", "", "", "")

    private fun computeDecoColumns(
        sample: DiveSample,
        state: OCState,
        depthSuffix: String,
    ): DecoColumns {
        if (state.beginningPhase) return EMPTY_DECO_COLUMNS
        val inDeco = sample.currentNdl == 0L || sample.firstStopDepth > 0.0
        if (!inDeco) return EMPTY_DECO_COLUMNS
        return DecoColumns(
            ceiling = formatTwoDecimals(sample.firstStopDepth),
            firstStopLabel = "STOP",
            stopTimeLabel = "TIME",
            decoLabel = "DECO",
            decoMeasurement = depthSuffix,
            decoMinuteIndicator = "min",
        )
    }

    private data class ClearColumns(
        val clearLabel: String,
        val clearTime: String,
        val clearedGasMixLabel: String,
        val clearedTtsLabel: String,
        val ndlBeforeClear: String,
        val ndlBeforeClearTime: String,
    )

    private val EMPTY_CLEAR_COLUMNS = ClearColumns("", "", "", "", "", "")

    private fun computeClearColumns(
        state: OCState,
        sample: DiveSample,
        depthUnit: DepthUnit,
    ): ClearColumns {
        val clearedAt = state.decoClearedAtTime ?: return EMPTY_CLEAR_COLUMNS
        if (state.beginningPhase) return EMPTY_CLEAR_COLUMNS
        val elapsed = sample.timeSeconds - clearedAt
        return ClearColumns(
            clearLabel = "CLEAR",
            clearTime = formatMinSec(elapsed),
            clearedGasMixLabel = gasMixture(sample.fractionO2, sample.fractionHe),
            clearedTtsLabel = "TTS",
            ndlBeforeClear = state.ndlBeforeDeco?.toString() ?: "",
            ndlBeforeClearTime = state.ndlBeforeDecoTime?.let { formatMinSec(it) } ?: "",
        )
    }
}
