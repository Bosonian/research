package com.bosonian.vibration128

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.bosonian.vibration128.databinding.ActivityThresholdBinding

/**
 * Vibration-perception-threshold (VPT) screening using an adaptive staircase.
 * Each trial plays a fixed-duration stimulus; the patient taps "I felt it" or
 * "I didn't feel it". After enough reversals the estimated threshold is shown.
 *
 * Frequency is whatever [VibrationController] can render on this device
 * (128 Hz in ENVELOPE/AUDIO_HAPTIC modes; native frequency otherwise) — the
 * staircase varies amplitude only.
 */
class ThresholdActivity : AppCompatActivity() {

    private companion object {
        const val STIMULUS_SECONDS = 2
    }

    private lateinit var binding: ActivityThresholdBinding
    private lateinit var controller: VibrationController
    private val staircase = StaircaseController()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = VibrationController(this)

        binding.replayButton.setOnClickListener { playStimulus() }
        binding.feltButton.setOnClickListener { respond(felt = true) }
        binding.notFeltButton.setOnClickListener { respond(felt = false) }
        binding.restartButton.setOnClickListener { recreate() }

        if (controller.mode == VibrationController.Mode.NONE) {
            binding.statusText.text = getString(R.string.status_no_vibrator)
            setResponseEnabled(false)
            binding.replayButton.isEnabled = false
        } else {
            beginTrial()
        }
    }

    private fun beginTrial() {
        if (staircase.isComplete) {
            showResult()
            return
        }
        binding.trialText.text =
            getString(R.string.trial_progress, staircase.trial + 1, staircase.reversals, 6)
        binding.statusText.text = getString(R.string.trial_prompt)
        setResponseEnabled(true)
        playStimulus()
    }

    private fun playStimulus() {
        controller.start(STIMULUS_SECONDS, staircase.currentAmplitude())
    }

    private fun respond(felt: Boolean) {
        controller.stop()
        staircase.submit(felt)
        beginTrial()
    }

    private fun showResult() {
        controller.stop()
        setResponseEnabled(false)
        binding.replayButton.isEnabled = false
        binding.restartButton.visibility = android.view.View.VISIBLE

        val percent = staircase.thresholdPercent()
        val db = staircase.thresholdDb()
        binding.trialText.text = getString(R.string.threshold_done)
        binding.statusText.text = if (percent != null && db != null) {
            getString(R.string.threshold_result, percent, db)
        } else {
            getString(R.string.threshold_no_result)
        }
    }

    private fun setResponseEnabled(enabled: Boolean) {
        binding.feltButton.isEnabled = enabled
        binding.notFeltButton.isEnabled = enabled
    }

    override fun onPause() {
        super.onPause()
        controller.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        controller.stop()
    }
}
