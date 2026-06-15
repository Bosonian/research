package com.bosonian.vibration128

import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * A simple adaptive (transformed up/down) staircase for estimating a vibration
 * perception threshold (VPT).
 *
 * Each trial presents a stimulus at the [currentAmplitude]. The patient reports
 * whether they felt it:
 *  - **felt**     → the stimulus is lowered (search downward for the threshold),
 *  - **not felt** → the stimulus is raised.
 *
 * A *reversal* is any change of direction. The step size halves at each reversal
 * (floored at [minStep]) to home in on the threshold. The run ends after
 * [maxReversals] reversals; the threshold is the mean amplitude of the last
 * [averagedReversals] reversal points.
 *
 * Amplitude is tracked as a normalised level in [0,1]; [currentAmplitude] maps it
 * to the 1..255 range the platform and [VibrationController] expect. This is a
 * screening aid, not a calibrated measurement.
 */
class StaircaseController(
    private val startLevel: Float = 0.80f,
    private val initialStep: Float = 0.15f,
    private val minStep: Float = 0.03f,
    private val minLevel: Float = 0.02f,
    private val maxReversals: Int = 6,
    private val averagedReversals: Int = 4,
) {
    var level: Float = startLevel.coerceIn(minLevel, 1f)
        private set

    var trial: Int = 0
        private set

    private var step: Float = initialStep
    private var lastDirection: Int = 0 // -1 = down, +1 = up, 0 = none yet
    private val reversalLevels = mutableListOf<Float>()

    val reversals: Int get() = reversalLevels.size

    val isComplete: Boolean get() = reversalLevels.size >= maxReversals

    /** Amplitude (1..255) for the current trial. */
    fun currentAmplitude(): Int =
        (level.coerceIn(minLevel, 1f) * 255f).roundToInt().coerceIn(1, 255)

    /**
     * Records the patient's response and advances the staircase.
     * @param felt true if the patient perceived the stimulus.
     */
    fun submit(felt: Boolean) {
        if (isComplete) return
        trial++
        val direction = if (felt) -1 else +1 // felt → quieter
        if (lastDirection != 0 && direction != lastDirection) {
            reversalLevels.add(level) // record the turning point
            step = (step / 2f).coerceAtLeast(minStep)
        }
        lastDirection = direction
        level = (level + direction * step).coerceIn(minLevel, 1f)
    }

    /** Estimated threshold level in [0,1], or null if no reversals yet. */
    fun thresholdLevel(): Float? {
        if (reversalLevels.isEmpty()) return null
        return reversalLevels.takeLast(averagedReversals).average().toFloat()
    }

    /** Threshold as a percentage of full scale, or null. */
    fun thresholdPercent(): Int? =
        thresholdLevel()?.let { (it * 100f).roundToInt() }

    /**
     * Threshold relative to full scale in dB (always ≤ 0; 0 dB = full scale).
     * Useful as a relative, device-specific severity indicator.
     */
    fun thresholdDb(): Double? =
        thresholdLevel()?.let { 20.0 * log10(it.coerceAtLeast(minLevel).toDouble()) }
}
