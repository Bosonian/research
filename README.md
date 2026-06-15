# Vibration 128

A minimal Android app that drives the phone's vibration motor at **128 Hz**, as a
digital analogue of the 128 Hz tuning fork used to screen **vibration perception**
(e.g. peripheral neuropathy). Tap **Start** and the motor pulses at 128 Hz for the
selected duration.

> ⚠️ **For research / educational use only. This is NOT a certified medical
> device and must not be used for diagnosis.**

## How it works (and an honest limitation)

The Android `Vibrator` API does **not** let an app set the actuator's mechanical
oscillation frequency. It only exposes:

- **timing** — how long the motor is on/off (whole milliseconds), and
- **amplitude** — 0–255, on devices with amplitude control (API 26+).

So a phone cannot be commanded to produce a *true, calibrated* 128 Hz tone like a
tuning fork. The hardware also matters:

- **LRA** (linear resonant actuator) motors vibrate at their own fixed resonant
  frequency, often ~150–235 Hz.
- **ERM** (eccentric rotating mass) motors change frequency with drive level.

This app produces a **128 Hz envelope**: it pulses the motor on/off **128 times per
second** (period 7.8125 ms). Because segment durations are rounded to whole
milliseconds, the app uses **error diffusion** so the long-run pulse rate is exactly
128 Hz. On devices with amplitude control the on segments use full amplitude for a
cleaner envelope; otherwise the motor is simply toggled on/off.

The result is a perceptual approximation, not a metrologically exact 128 Hz
reference. The app reports the device's haptic capabilities on screen.

## Features

- One-tap 128 Hz vibration with auto-stop.
- Adjustable **duration** (1–10 s).
- Adjustable **intensity** (shown only on devices with amplitude control).
- On-screen capability + safety disclaimer.

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
