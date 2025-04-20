package com.example.sensorviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

object ToastHelper {
    private var lastToastTime = 0L
    private const val TOAST_INTERVAL = 2000L

    fun showThrottledToast(context: Context, message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime >= TOAST_INTERVAL) {
            val appContext = context.applicationContext
            showToastOnMainThread(appContext, message)
            lastToastTime = currentTime
        }
    }

    private fun showToastOnMainThread(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}