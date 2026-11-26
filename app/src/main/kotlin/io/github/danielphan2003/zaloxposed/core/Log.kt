package io.github.danielphan2003.zaloxposed.core

import android.util.Log as AndroidLog
import io.github.libxposed.api.XposedInterface

/** Writes through the framework's logger, so lines land in logcat under one tag. */
internal class Log(private val xposed: XposedInterface) {
    fun i(msg: String) = runCatching { xposed.log(AndroidLog.INFO, TAG, msg) }.let { }
    fun w(msg: String, t: Throwable? = null) = runCatching { xposed.log(AndroidLog.WARN, TAG, msg, t) }.let { }
    fun e(msg: String, t: Throwable? = null) = runCatching { xposed.log(AndroidLog.ERROR, TAG, msg, t) }.let { }

    companion object {
        const val TAG = "ZaloXposed"
    }
}
