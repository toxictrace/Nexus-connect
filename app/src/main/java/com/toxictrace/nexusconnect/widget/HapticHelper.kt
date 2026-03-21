package com.toxictrace.nexusconnect.widget

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    private const val TAG = "HapticHelper"

    fun vibrate(context: Context) {
        Log.d(TAG, "vibrate() called")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(100, 255)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(100, 255))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrator failed: ${e.message}")
        }
    }

    /**
     * MIUI-compatible haptic — must be called from a View.
     * Uses FLAG_IGNORE_GLOBAL_SETTING to bypass MIUI restrictions.
     */
    @Suppress("DEPRECATION")
    fun vibrateView(view: View) {
        try {
            view.isHapticFeedbackEnabled = true
            val result = view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
            Log.d(TAG, "vibrateView result=$result")
            if (!result) {
                val fallbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.CONFIRM
                else
                    HapticFeedbackConstants.VIRTUAL_KEY
                view.performHapticFeedback(
                    fallbackConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibrateView failed: ${e.message}")
            vibrate(view.context)
        }
    }
}
