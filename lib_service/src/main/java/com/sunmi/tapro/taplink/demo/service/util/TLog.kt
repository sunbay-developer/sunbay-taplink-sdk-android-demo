package com.sunmi.tapro.taplink.demo.service.util

import android.util.Log as AndroidLog

/**
 * Logging utility that auto-captures the caller's file name, line number, and method name
 * from the current stack trace, producing log lines in the format:
 *
 *   [ (TLog.kt:LINE)#Log ]  [ (CallerFile.kt:LINE)#callerMethod ]  message
 *
 * This mirrors the OkHttp interceptor log style used in the Taplink demo.
 */
object TLog {

    private fun buildPrefix(depth: Int): String {
        val stack = Thread.currentThread().stackTrace
        // stack[0] = VMStack / Thread native frame
        // stack[1] = Thread.getStackTrace
        // stack[2] = buildPrefix (this method)
        // stack[3] = the public TLog.Log / LogW / LogE wrapper
        // stack[depth] = the actual caller of TLog.*
        val tlogFrame = stack[3]
        val callerFrame = if (stack.size > depth) stack[depth] else null
        val tlogInfo = "[ (${tlogFrame.fileName}:${tlogFrame.lineNumber})#${tlogFrame.methodName} ]"
        val callerInfo = callerFrame?.let {
            "[ (${it.fileName}:${it.lineNumber})#${it.methodName} ]"
        } ?: ""
        return "$tlogInfo  $callerInfo  "
    }

    @JvmStatic
    fun Log(tag: String, msg: String) {
        AndroidLog.d(tag, buildPrefix(4) + msg)
    }

    @JvmStatic
    fun LogW(tag: String, msg: String) {
        AndroidLog.w(tag, buildPrefix(4) + msg)
    }

    @JvmStatic
    fun LogE(tag: String, msg: String, throwable: Throwable? = null) {
        val prefixed = buildPrefix(4) + msg
        if (throwable != null) AndroidLog.e(tag, prefixed, throwable)
        else AndroidLog.e(tag, prefixed)
    }
}
