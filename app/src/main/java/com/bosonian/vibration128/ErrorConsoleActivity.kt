package com.bosonian.vibration128

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * A visible, in-app error panel — the native analogue of the requested in-page
 * JavaScript error console. Auto-launched by [App]'s uncaught-exception handler
 * (and reusable for reviewing recorded non-fatal errors). Shows each captured
 * error's message, type, source location and stack trace, and offers
 * Copy Errors, Clear, and Close.
 *
 * The UI is built programmatically so the panel is fully self-contained and has
 * no dependency on view-binding or layout resources (which keeps it working
 * even in the bare `:crash` process).
 */
class ErrorConsoleActivity : AppCompatActivity() {

    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refresh()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        refresh()
    }

    private fun refresh() {
        val text = ErrorLog.readAll(this)
        logView.text = if (text.isBlank()) "No errors recorded." else text
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.error_console_title)
            setTextColor(Color.parseColor("#FF6B6B"))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        buttons.addView(actionButton(getString(R.string.error_copy)) { copy() })
        buttons.addView(actionButton(getString(R.string.error_clear)) {
            ErrorLog.clear(this)
            refresh()
        })
        buttons.addView(actionButton(getString(R.string.error_close)) { close() })
        root.addView(buttons)

        logView = TextView(this).apply {
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            addView(logView)
        })

        return root
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            setOnClickListener { onClick() }
        }

    private fun copy() {
        val text = ErrorLog.readAll(this)
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.error_nothing_to_copy), Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("errors", text))
        Toast.makeText(this, getString(R.string.error_copied), Toast.LENGTH_SHORT).show()
    }

    private fun close() {
        // The crash that opened this panel killed the original task, so relaunch
        // the app fresh to land the user somewhere usable, then dismiss.
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
