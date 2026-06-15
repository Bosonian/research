package com.bosonian.vibration128

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app error store — the native analogue of a browser's `window.onerror` /
 * `unhandledrejection` log.
 *
 * It captures crashes (uncaught exceptions on any thread, which on the JVM
 * includes failed Kotlin coroutines, since those surface through the thread's
 * default uncaught-exception handler) plus any caught [Throwable] a caller
 * chooses to [record]. Entries are persisted to a file in [Context.getFilesDir]
 * so they survive the process death an uncaught exception causes — the panel
 * ([ErrorConsoleActivity]) then reads them back from a separate process.
 *
 * Every method is defensive: logging must never itself throw.
 */
object ErrorLog {

    private const val FILE_NAME = "error_console.log"
    private const val SEPARATOR = "\n========================================\n"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Append a throwable to the persisted log. Safe to call from any thread. */
    @Synchronized
    fun record(context: Context, throwable: Throwable, thread: Thread = Thread.currentThread()) {
        try {
            file(context).appendText(format(throwable, thread) + SEPARATOR)
        } catch (_: Throwable) {
            // Never let error logging throw.
        }
    }

    /** The full log text (all entries concatenated), or empty if none. */
    @Synchronized
    fun readAll(context: Context): String =
        try {
            file(context).takeIf { it.exists() }?.readText().orEmpty()
        } catch (_: Throwable) {
            ""
        }

    @Synchronized
    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun hasEntries(context: Context): Boolean =
        try {
            file(context).let { it.exists() && it.length() > 0 }
        } catch (_: Throwable) {
            false
        }

    private fun format(t: Throwable, thread: Thread): String {
        val top = t.stackTrace.firstOrNull()
        // JVM stack frames carry a file name and line number but NOT a column
        // (unlike JavaScript), so column is reported as n/a.
        val location = if (top != null) {
            "${top.className}.${top.methodName}(${top.fileName ?: "Unknown"}:${top.lineNumber}) col n/a"
        } else {
            "unknown"
        }
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return buildString {
            append("time:     ").append(timestamp()).append('\n')
            append("thread:   ").append(thread.name).append('\n')
            append("type:     ").append(t.javaClass.name).append('\n')
            append("message:  ").append(t.message ?: "(no message)").append('\n')
            append("location: ").append(location).append('\n')
            append("stack:\n").append(sw.toString().trimEnd())
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
