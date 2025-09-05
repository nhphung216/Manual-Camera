package com.ssolstice.camera.manual.utils

import android.util.Log

fun <T : Any> T.tag() = this::class.simpleName ?: "LOGGING_UNKNOWN"

object Logger {

    private var isEnable = true

    fun d(tag: String?, message: String?) {
        if (isEnable && message != null) {
            Log.d(tag, getClassNameMethodNameAndLineNumber() + message)
        }
    }

    fun e(tag: String?, message: String?) {
        if (isEnable && message != null) {
            Log.e(tag, getClassNameMethodNameAndLineNumber() + message)
        }
    }

    fun w(tag: String?, message: String?) {
        if (isEnable && message != null) {
            Log.w(tag, getClassNameMethodNameAndLineNumber() + message)
        }
    }

    fun i(tag: String?, message: String?) {
        if (isEnable && message != null) {
            Log.i(tag, getClassNameMethodNameAndLineNumber() + message)
        }
    }

    fun log(message: String?) {
        if (isEnable && message != null) {
            Log.e(tag(), getClassNameMethodNameAndLineNumber() + message)
        }
    }

    fun log(message: Any?) {
        if (isEnable && message != null) {
            Log.e(tag(), getClassNameMethodNameAndLineNumber() + message)
        }
    }

    private const val STACK_TRACE_LEVELS_UP = 5

    private fun getLineNumber(): Int {
        return Thread.currentThread().stackTrace[STACK_TRACE_LEVELS_UP].lineNumber
    }

    private fun getClassName(): String {
        val fileName = Thread.currentThread().stackTrace[STACK_TRACE_LEVELS_UP].fileName
        return fileName.substring(0, fileName.length - 5)
    }

    private fun getMethodName(): String {
        return Thread.currentThread().stackTrace[STACK_TRACE_LEVELS_UP].methodName
    }

    private fun getClassNameMethodNameAndLineNumber(): String {
        return "[" + getClassName() + "." + getMethodName() + "()-" + getLineNumber() + "]: "
    }
}