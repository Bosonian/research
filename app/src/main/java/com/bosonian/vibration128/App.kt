package com.bosonian.vibration128

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

/**
 * Installs a global uncaught-exception handler that records the crash to
 * [ErrorLog] and pops up [ErrorConsoleActivity] in a separate `:crash` process,
 * so the panel survives the dying app process.
 *
 * This is the native equivalent of a browser auto-opening an error console on
 * `window.onerror` / an unhandled promise rejection: on the JVM, uncaught
 * coroutine failures funnel through this same default handler, so both fatal
 * crashes and "rejected" async work are surfaced.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application.onCreate runs once *per process*. Only the main process
        // installs the handler — the :crash process that shows the console must
        // keep the system default, otherwise a crash inside the console would
        // relaunch the console forever.
        if (isCrashProcess()) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                ErrorLog.record(this, throwable, thread)
                startActivity(
                    Intent(this, ErrorConsoleActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            } catch (_: Throwable) {
                // If we can't show the console, defer to the platform default
                // so the crash is still reported normally.
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            // Tear down the crashed process so it doesn't linger half-dead while
            // the console comes up in the :crash process.
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun isCrashProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            try {
                File("/proc/self/cmdline").readText().trim { it <= ' ' }
            } catch (_: Throwable) {
                null
            }
        }
        return name?.endsWith(":crash") == true
    }
}
