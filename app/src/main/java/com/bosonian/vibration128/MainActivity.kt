package com.bosonian.vibration128

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.bosonian.vibration128.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: VibrationController
    private val handler = Handler(Looper.getMainLooper())

    private var isRunning = false
    private var durationSeconds = 5
    private var intensity = 255

    private val stopRunnable = Runnable { stopVibration() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = VibrationController(this)

        binding.frequencyValue.text = getString(R.string.frequency_value)
        updateDurationLabel()
        updateIntensityLabel()
        showCapabilities()

        binding.durationSeekBar.max = 9 // 1..10 seconds
        binding.durationSeekBar.progress = durationSeconds - 1
        binding.durationSeekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                durationSeconds = progress + 1
                updateDurationLabel()
            }
        })

        binding.intensitySeekBar.max = 254 // maps to amplitude 1..255
        binding.intensitySeekBar.progress = intensity - 1
        binding.intensitySeekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intensity = progress + 1
                updateIntensityLabel()
            }
        })

        // Intensity is meaningful in audio-haptic mode (sine amplitude) and in
        // native mode only when the motor supports amplitude control.
        val intensityUsable = controller.mode == VibrationController.Mode.AUDIO_HAPTIC ||
            controller.hasAmplitudeControl
        binding.intensityRow.visibility =
            if (intensityUsable) android.view.View.VISIBLE else android.view.View.GONE

        binding.startStopButton.setOnClickListener {
            if (isRunning) stopVibration() else startVibration()
        }

        binding.startStopButton.isEnabled = controller.mode != VibrationController.Mode.NONE
    }

    private fun startVibration() {
        val usedMode = controller.start(durationSeconds, intensity)
        if (usedMode == VibrationController.Mode.NONE) {
            binding.statusText.text = getString(R.string.status_no_vibrator)
            return
        }
        isRunning = true
        binding.startStopButton.text = getString(R.string.stop)
        val statusRes = if (usedMode == VibrationController.Mode.NATIVE) {
            R.string.status_running_native
        } else {
            R.string.status_running
        }
        binding.statusText.text = getString(statusRes, durationSeconds)
        // Auto-reset the UI when the stimulus finishes.
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, durationSeconds * 1000L)
    }

    private fun stopVibration() {
        handler.removeCallbacks(stopRunnable)
        controller.stop()
        isRunning = false
        binding.startStopButton.text = getString(R.string.start)
        binding.statusText.text = getString(R.string.status_idle)
    }

    private fun updateDurationLabel() {
        binding.durationLabel.text = getString(R.string.duration_label, durationSeconds)
    }

    private fun updateIntensityLabel() {
        val percent = (intensity * 100) / 255
        binding.intensityLabel.text = getString(R.string.intensity_label, percent)
    }

    private fun showCapabilities() {
        binding.capabilityText.text = when (controller.mode) {
            VibrationController.Mode.AUDIO_HAPTIC -> getString(R.string.cap_audio_haptic)
            VibrationController.Mode.NATIVE -> getString(R.string.cap_native)
            VibrationController.Mode.NONE -> getString(R.string.cap_no_vibrator)
        }
    }

    override fun onPause() {
        super.onPause()
        // Never leave the motor running in the background.
        if (isRunning) stopVibration()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(stopRunnable)
        controller.stop()
    }
}
