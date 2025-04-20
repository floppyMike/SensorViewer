package com.example.sensorviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ThresholdAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val x = intent.getFloatExtra("x", 0f)
        val y = intent.getFloatExtra("y", 0f)
        val z = intent.getFloatExtra("z", 0f)

        val message = "$type Reached! X=$x, Y=$y, Z=$z"
        ToastHelper.showThrottledToast(context, message)
    }
}