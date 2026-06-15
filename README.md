# Vibration 128

A minimal Android app that drives the phone's vibration motor at **128 Hz**, as a
digital analogue of the 128 Hz tuning fork used to screen **vibration perception**
(e.g. peripheral neuropathy). Tap **Start** and the motor pulses at 128 Hz for the
selected duration.

> ⚠️ **For research / educational use only. This is NOT a certified medical
> device and must not be used for diagnosis.**

## How it works (and an honest limitation)

The standard Android `Vibrator` API does **not** let an app set the actuator's
mechanical oscillation frequency — it only controls *timing* and *amplitude*.
Pulsing the motor on/off at ~4 ms to "fake" 128 Hz does **not** work: actuators
have rise/ring-down times of tens of milliseconds, so they cannot start and stop
that fast and instead emit a mushy buzz at their own resonant frequency.

The correct way to render an arbitrary frequency is to drive the actuator with a
**sampled waveform**. This app picks the best available method per device, in
priority order:

0. **`ENVELOPE` (best, Android 16 / API 36).** Requests 128 Hz directly via
   `VibrationEffect.WaveformEnvelopeBuilder` — calibrated, frequency-specified
   haptics. It is invoked through a small **reflection bridge** guarded by
   `Build.VERSION.SDK_INT >= 36`, so the project keeps building against
   `compileSdk 34` (a direct reference would force AGP 9 / Gradle 9). If the API
   or device support is missing, it falls through to the next method.
1. **`AUDIO_HAPTIC` (Android 10+ where supported).** When
   `AudioManager.isHapticPlaybackSupported()` is true, the app synthesises a
   128 Hz sine and writes it to a dedicated **haptic audio channel**
   (`AudioFormat.CHANNEL_OUT_HAPTIC_A`) via `AudioTrack`, with the audio channel
   held silent and `setHapticChannelsMuted(false)`. The audio HAL routes that
   waveform straight to the actuator — a **true 128 Hz drive signal**.
2. **`NATIVE` (fallback).** On devices without haptic playback, the app drives a
   continuous, amplitude-controlled vibration at the motor's **own resonant
   frequency** (not 128 Hz). It says so on screen and is still usable for
   amplitude-based threshold testing.

### Hardware reality

- Most phone **LRA** actuators resonate around **150–235 Hz**, so 128 Hz sits
  *off-resonance* and is reproduced at reduced amplitude even in `AUDIO_HAPTIC`
  mode. **ERM** motors cannot do it at all.
- Audio-coupled haptics are device-specific (higher-end Pixels and several
  flagships); many phones fall back to `NATIVE`.

## Vibration perception threshold (VPT) screening

Beyond playing a fixed stimulus, the **threshold test** (button on the main
screen → `ThresholdActivity`) estimates a vibration perception threshold using an
**adaptive up/down staircase** (`StaircaseController`):

- Each trial plays a 2 s stimulus; the patient taps *I felt it* / *I didn't feel
  it*.
- "Felt" lowers the amplitude, "not felt" raises it; the step halves at each
  **reversal** and the run ends after 6 reversals.
- The threshold is the mean amplitude of the last 4 reversals, reported as
  percent of full scale and dB relative to full scale.

Frequency is whatever the device can render (128 Hz in `ENVELOPE`/`AUDIO_HAPTIC`
modes, native otherwise); the staircase varies **amplitude** only. This mirrors how
published smartphone-VPT studies work and is the clinically meaningful mode — but
it is a **relative, device-specific screening aid, not a calibrated measurement**.

## Features

- One-tap 128 Hz stimulus with auto-stop, using the best method the device supports.
- **VPT staircase** screening mode.
- Adjustable **duration** (1–10 s) and **intensity**.
- On-screen **mode/capability** readout + safety disclaimer.

## Status / verification

This environment has no Android SDK (and Google's SDK host is network-blocked),
so the project has **not been compiled or run on a device here**. The code is
written against `compileSdk 34` and reviewed by hand; the `AUDIO_HAPTIC` path in
particular should be validated on real hardware (haptic-channel routing varies by
OEM, and it is wrapped in a runtime fallback to `NATIVE`).

## Build

Requires Android Studio (or the Android SDK + command-line tools).

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # build and install on a connected device
```

The APK is written to `app/build/outputs/apk/debug/`.

- **minSdk** 26 (Android 8.0) — required for `VibrationEffect` amplitude control.
- **targetSdk / compileSdk** 34.
- Language: Kotlin, View Binding, Material 3.

## Project layout

```
app/src/main/java/com/bosonian/vibration128/
  MainActivity.kt           # UI wiring (start/stop, sliders, capability display)
  VibrationController.kt     # 128 Hz waveform generation + playback
  SimpleSeekBarListener.kt   # SeekBar listener helper
app/src/main/res/            # layout, strings, colours, theme, launcher icon
```
