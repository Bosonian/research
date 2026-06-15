package com.bosonian.vibration128

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * Produces a 128 Hz vibration stimulus, intended as a digital analogue of the
 * 128 Hz tuning fork used to screen vibration perception (e.g. peripheral
 * neuropathy).
 *
 * ## Why this is hard, and how we get the closest honest result
 *
 * The standard [Vibrator] API cannot set the actuator's oscillation frequency —
 * it only controls timing and amplitude. Pulsing the motor on/off at ~4 ms (an
 * earlier approach) does NOT create a 128 Hz oscillation: actuators have rise
 * and ring-down times of tens of milliseconds, so they cannot start/stop that
 * fast and instead emit a mushy buzz at their own resonant frequency.
 *
 * The genuinely correct way to render an arbitrary frequency is to drive the
 * actuator with a sampled waveform. The controller picks the best path the
 * device supports, in priority order:
 *
 *  0. [Mode.ENVELOPE] — on Android 16+ (API 36), request 128 Hz directly via
 *     `VibrationEffect.WaveformEnvelopeBuilder` (calibrated, frequency-specified
 *     haptics). Accessed by reflection so the project still builds against
 *     compileSdk 34; falls through if unavailable.
 *
 *  1. [Mode.AUDIO_HAPTIC] — on devices that support audio-coupled haptics
 *     (API 29+ and `isHapticPlaybackSupported`), we synthesise a 128 Hz sine
 *     and write it to a dedicated haptic audio channel ([AudioFormat.CHANNEL_OUT_HAPTIC_A]).
 *     The audio HAL routes that waveform straight to the actuator, producing a
 *     true 128 Hz drive signal. This is the best widely-available method.
 *
 *  2. [Mode.NATIVE] — fallback for devices without haptic playback. We drive a
 *     continuous, amplitude-controlled vibration at the device's *own* resonant
 *     frequency (NOT 128 Hz). This is honest about its limitation and is still
 *     usable for amplitude-based vibration-perception-threshold testing.
 *
 * Note on hardware: most phone LRAs resonate around 150–235 Hz, so 128 Hz sits
 * off-resonance and is reproduced at reduced amplitude even in AUDIO_HAPTIC
 * mode. On Android 16 (API 36) the platform adds `VibrationEffect`
 * `WaveformEnvelopeBuilder`/`VibratorFrequencyProfile` for calibrated,
 * frequency-specified haptics; when this app raises its compileSdk to 36 that
 * becomes the preferred path. See README.
 *
 * This is NOT a certified medical device.
 */
class VibrationController(context: Context) {

    enum class Mode { ENVELOPE, AUDIO_HAPTIC, NATIVE, NONE }

    companion object {
        const val TARGET_FREQUENCY_HZ = 128.0
        private const val SAMPLE_RATE = 48_000

        /** Android 16; the frequency-envelope API (API 36). Literal so the app
         *  still builds against compileSdk 34. */
        private const val ENVELOPE_API = 36
    }

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val hapticPlaybackSupported: Boolean = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.isHapticPlaybackSupported == true
        } else {
            false
        }
    }

    /**
     * Whether the actuator supports API 36 envelope effects. Checked by
     * reflection: if the method is absent (older platform) or returns false,
     * we treat envelopes as unsupported and fall back — so an incorrect guess
     * degrades safely rather than playing nothing.
     */
    private val envelopeSupported: Boolean = run {
        if (Build.VERSION.SDK_INT < ENVELOPE_API) return@run false
        val vib = vibrator ?: return@run false
        try {
            val method = Vibrator::class.java.getMethod("areEnvelopeEffectsSupported")
            method.invoke(vib) as? Boolean ?: false
        } catch (t: Throwable) {
            false
        }
    }

    private var audioTrack: AudioTrack? = null

    val hasVibrator: Boolean
        get() = vibrator?.hasVibrator() == true

    val hasAmplitudeControl: Boolean
        get() = vibrator?.hasAmplitudeControl() == true

    /** The rendering strategy this device is expected to use (best first). */
    val mode: Mode
        get() = when {
            envelopeSupported -> Mode.ENVELOPE
            hapticPlaybackSupported -> Mode.AUDIO_HAPTIC
            hasVibrator -> Mode.NATIVE
            else -> Mode.NONE
        }

    /**
     * Starts the stimulus for [durationSeconds].
     *
     * @param intensity 1..255 (mapped to 0..1 for the audio-haptic path).
     * @return the [Mode] actually used, or [Mode.NONE] if nothing could play.
     */
    fun start(durationSeconds: Int, intensity: Int): Mode {
        stop()
        if (durationSeconds <= 0) return Mode.NONE
        val amplitude = intensity.coerceIn(1, 255)
        val amplitude01 = amplitude / 255f

        if (envelopeSupported && startEnvelope(durationSeconds, amplitude01)) {
            return Mode.ENVELOPE
        }
        if (hapticPlaybackSupported && startAudioHaptic(durationSeconds, amplitude01)) {
            return Mode.AUDIO_HAPTIC
        }
        if (hasVibrator && startNative(durationSeconds, amplitude)) {
            return Mode.NATIVE
        }
        return Mode.NONE
    }

    /**
     * Android 16 (API 36) calibrated, frequency-specified haptics via
     * `VibrationEffect.WaveformEnvelopeBuilder`. Accessed by reflection so the
     * project keeps building against compileSdk 34 (compileSdk 36 would force
     * AGP 9 / Gradle 9). Ramps up, holds 128 Hz, ramps down to avoid abrupt
     * ring-down. Returns false (so callers fall back) if anything is missing.
     */
    private fun startEnvelope(durationSeconds: Int, amplitude01: Float): Boolean {
        val vib = vibrator ?: return false
        return try {
            val builderCls = Class.forName("android.os.VibrationEffect\$WaveformEnvelopeBuilder")
            val builder = builderCls.getDeclaredConstructor().newInstance()
            val addControlPoint = builderCls.methods.first {
                it.name == "addControlPoint" && it.parameterTypes.size == 3
            }
            // The duration parameter is int on some builds and long on others;
            // box it to whatever the platform actually declares.
            val durType = addControlPoint.parameterTypes[2]
            fun dur(ms: Long): Any = if (durType == java.lang.Long.TYPE) ms else ms.toInt()

            val freq = TARGET_FREQUENCY_HZ.toFloat()
            val holdMs = durationSeconds * 1000L
            val amp = amplitude01.coerceIn(0f, 1f)
            addControlPoint.invoke(builder, amp, freq, dur(30L))     // ramp up
            addControlPoint.invoke(builder, amp, freq, dur(holdMs))  // hold 128 Hz
            addControlPoint.invoke(builder, 0f, freq, dur(30L))      // ramp down

            val effect = builderCls.getMethod("build").invoke(builder) as VibrationEffect
            vib.vibrate(effect)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Drives the actuator with a true 128 Hz sine via the haptic audio channel.
     * The frame layout is interleaved [audio, haptic]; the audio sample is held
     * at silence so only the actuator is driven.
     */
    private fun startAudioHaptic(durationSeconds: Int, amplitude01: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val numFrames = SAMPLE_RATE * durationSeconds
            val data = FloatArray(numFrames * 2) // 2 channels: audio + haptic
            val w = 2.0 * PI * TARGET_FREQUENCY_HZ / SAMPLE_RATE
            for (n in 0 until numFrames) {
                data[n * 2] = 0f                                  // audio: silent
                data[n * 2 + 1] = (sin(w * n) * amplitude01).toFloat() // haptic: 128 Hz
            }

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setHapticChannelsMuted(false)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO or AudioFormat.CHANNEL_OUT_HAPTIC_A)
                .build()

            val sizeBytes = data.size * Float.SIZE_BYTES
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(sizeBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            val written = track.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                track.release()
                return false
            }
            track.play()
            audioTrack = track
            true
        } catch (t: Throwable) {
            audioTrack?.release()
            audioTrack = null
            false
        }
    }

    /** Continuous vibration at the device's native resonant frequency. */
    private fun startNative(durationSeconds: Int, amplitude: Int): Boolean {
        val vib = vibrator ?: return false
        return try {
            val durationMs = durationSeconds * 1000L
            val amp = if (hasAmplitudeControl) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            vib.vibrate(VibrationEffect.createOneShot(durationMs, amp))
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Immediately stops any ongoing stimulus. */
    fun stop() {
        try {
            audioTrack?.let {
                it.pause()
                it.flush()
                it.release()
            }
        } catch (_: Throwable) {
        } finally {
            audioTrack = null
        }
        vibrator?.cancel()
    }
}
