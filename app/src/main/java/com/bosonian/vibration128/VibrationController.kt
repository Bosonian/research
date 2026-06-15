package com.bosonian.vibration128

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.roundToLong

/**
 * Drives the phone's vibration motor with a 128 Hz amplitude-modulated waveform,
 * intended as a digital analogue of the 128 Hz tuning fork used to screen
 * vibration perception (e.g. peripheral neuropathy).
 *
 * IMPORTANT — hardware limitation:
 * The Android [Vibrator] API does NOT let an app set the actuator's mechanical
 * oscillation frequency. It only controls *timing* (on/off durations) and, on
 * API 26+, *amplitude* (0..255). A linear resonant actuator (LRA) vibrates at
 * its own fixed resonant frequency (often ~150-235 Hz); an eccentric rotating
 * mass (ERM) motor's frequency varies with drive level. This controller
 * therefore produces a 128 Hz *envelope*: the motor is pulsed on/off 128 times
 * per second (period 7.8125 ms). The pulse rate is held at exactly 128 Hz on
 * average using error diffusion, because the API rounds segment durations to
 * whole milliseconds. This is a perceptual approximation, not a calibrated
 * 128 Hz reference, and it is not a certified medical device.
 */
class VibrationController(context: Context) {

    companion object {
        const val TARGET_FREQUENCY_HZ = 128.0

        /** One full on+off cycle at 128 Hz, in nanoseconds (7,812,500 ns). */
        private const val PERIOD_NS = 1_000_000_000L / 128

        /** Half a cycle (the "on" or the "off" segment), in nanoseconds. */
        private const val HALF_PERIOD_NS = PERIOD_NS / 2
    }

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** True if the device actually has a vibration motor. */
    val hasVibrator: Boolean
        get() = vibrator?.hasVibrator() == true

    /** True if the motor supports variable amplitude (cleaner 128 Hz envelope). */
    val hasAmplitudeControl: Boolean
        get() = vibrator?.hasAmplitudeControl() == true

    /**
     * Builds and plays a [durationSeconds]-long 128 Hz waveform.
     *
     * Each half-period alternates between full-amplitude ("on") and zero
     * ("off"). Because [VibrationEffect] timings are integer milliseconds while
     * the true half-period is 3.90625 ms, we accumulate the rounding error and
     * spill it into the next segment, keeping the long-run rate at exactly
     * 128 Hz.
     *
     * @return true if playback started, false if there is no usable vibrator.
     */
    fun start(durationSeconds: Int, intensity: Int): Boolean {
        val vib = vibrator ?: return false
        if (!vib.hasVibrator()) return false

        val totalNs = durationSeconds.toLong() * 1_000_000_000L
        val halfCycles = (totalNs / HALF_PERIOD_NS).toInt()
        if (halfCycles <= 0) return false

        val timings = LongArray(halfCycles)
        val amplitudes = IntArray(halfCycles)

        val amplitude = intensity.coerceIn(1, 255)

        // Error-diffusion so segment durations average exactly 3.90625 ms.
        var idealAccumulatedNs = 0L
        var emittedAccumulatedNs = 0L
        for (i in 0 until halfCycles) {
            idealAccumulatedNs += HALF_PERIOD_NS
            val targetMs = (idealAccumulatedNs - emittedAccumulatedNs) / 1_000_000.0
            // At least 1 ms — the motor cannot meaningfully act on shorter pulses.
            val ms = targetMs.roundToLong().coerceAtLeast(1L)
            timings[i] = ms
            emittedAccumulatedNs += ms * 1_000_000L
            // Even segments are "on" (full amplitude), odd segments are "off".
            amplitudes[i] = if (i % 2 == 0) amplitude else 0
        }

        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            // Without amplitude control the motor is either on or off; the
            // alternating on/off timing array still produces a 128 Hz envelope.
            VibrationEffect.createWaveform(timings, -1)
        }

        // defaultVibrator (API 31+) and the legacy Vibrator service both accept
        // a VibrationEffect directly, so a single call path works on all API 26+.
        vib.cancel()
        vib.vibrate(effect)
        return true
    }

    /** Immediately stops any ongoing vibration. */
    fun stop() {
        vibrator?.cancel()
    }
}
