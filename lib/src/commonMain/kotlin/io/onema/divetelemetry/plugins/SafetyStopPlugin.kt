package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.util.formatMinSec
import kotlin.math.abs

object SafetyStopPlugin : OutputPlugin {
    override val id: String = "core.safety-stop"
    override val name: String = "Safety Stop Timer"
    override val description: String = "Adds a countdown timer for the safety stop zone."

    override val parameters: List<PluginParameter<*>> = listOf(
        BooleanParameter(
            key = "enabled",
            name = "Enable Safety Stop",
            description = "Add safety stop countdown timer column.",
            defaultValue = false,
        )
    )

    override fun additionalHeaders(metadata: DiveMetadata): List<String> = listOf(
        "Safety Stop Timer (text)",
    )

    override fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>> {
        ensure(log.samples.isNotEmpty()) {
            PluginError.ExecutionError("SafetyStopPlugin requires at least one sample.")
        }

        val safetyStopZone = when (log.metadata.depthUnit) {
            DepthUnit.FT -> 10.0..20.0
            DepthUnit.M -> 3.0..6.0
        }

        val states = computeSafetyStopStates(log.samples, safetyStopZone)

        return states.map { state ->
            val timerStr = if (state.safetyStopRemaining > 0L) {
                formatMinSec(state.safetyStopRemaining)
            } else {
                ""
            }
            mapOf("Safety Stop Timer (text)" to timerStr)
        }
    }

    private data class SafetyStopState(
        val beginningPhase: Boolean,
        val inDeco: Boolean,
        val wasInDeco: Boolean,
        val safetyStopRemaining: Long,
        val previousTimeSeconds: Long,
        val hasBeenDeep: Boolean,
    )

    private fun computeSafetyStopStates(
        samples: List<DiveSample>,
        safetyStopZone: ClosedFloatingPointRange<Double>,
    ): List<SafetyStopState> {
        val initial = SafetyStopState(
            beginningPhase = true,
            inDeco = false,
            wasInDeco = false,
            safetyStopRemaining = 0L,
            previousTimeSeconds = 0L,
            hasBeenDeep = false,
        )

        return samples.runningFold(initial) { state, sample ->
            val depthAbs = abs(sample.depth)

            val newBeginningPhase = when {
                !state.beginningPhase -> false
                sample.currentNdl != null && sample.currentNdl >= 50 -> false
                else -> true
            }

            val currentInDeco = sample.currentNdl == 0L || sample.firstStopDepth > 0.0
            val newWasInDeco = state.wasInDeco || (!newBeginningPhase && currentInDeco)
            val hasBeenDeep = state.hasBeenDeep || depthAbs > safetyStopZone.endInclusive
            val timeDelta = sample.timeSeconds - state.previousTimeSeconds
            val inSafetyZone = depthAbs in safetyStopZone

            val newSafetyStopRemaining = when {
                newBeginningPhase -> 0L
                currentInDeco -> 0L
                !hasBeenDeep -> 0L
                !inSafetyZone -> 0L
                state.safetyStopRemaining > 0L -> maxOf(0L, state.safetyStopRemaining - timeDelta)
                else -> if (newWasInDeco) 300L else 180L
            }

            SafetyStopState(
                beginningPhase = newBeginningPhase,
                inDeco = currentInDeco,
                wasInDeco = newWasInDeco,
                safetyStopRemaining = newSafetyStopRemaining,
                previousTimeSeconds = sample.timeSeconds,
                hasBeenDeep = hasBeenDeep,
            )
        }.drop(1)
    }

}

