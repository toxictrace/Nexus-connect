package com.toxictrace.nexusconnect.widget

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object HapticHelper {

    private const val TAG = "HapticHelper"

    fun vibrate(context: Context) {
        Log.d(TAG, "vibrate() called, SDK=${Build.VERSION.SDK_INT}")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                val v = vm?.defaultVibrator
                Log.d(TAG, "VibratorManager=$vm, defaultVibrator=$v, hasVibrator=${v?.hasVibrator()}")
                v?.vibrate(VibrationEffect.createOneShot(100, 255))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                Log.d(TAG, "Vibrator=$v, hasVibrator=${v?.hasVibrator()}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(100, 255))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(100)
                }
            }
            Log.d(TAG, "vibrate() completed without exception")
        } catch (e: Exception) {
            Log.e(TAG, "vibrate() failed: ${e.message}", e)
        }
    }
}
