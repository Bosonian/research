package com.bosonian.vibration128

import android.widget.SeekBar

/** Convenience base so callers only override [onProgressChanged]. */
abstract class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}
