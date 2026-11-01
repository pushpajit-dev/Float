package com.noishixzrn.floatapp

import android.app.Application
import android.content.Intent
import android.util.Log

class FloatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("FloatApp", "Uncaught exception on thread [${thread.name}]", throwable)
                val stackTrace = Log.getStackTraceString(throwable)
                val reason = "${throwable.javaClass.name}: ${throwable.message ?: "No error message"}"

                val intent = Intent(this, CrashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTrace)
                    putExtra(CrashActivity.EXTRA_REASON, reason)
                }
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (e: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
