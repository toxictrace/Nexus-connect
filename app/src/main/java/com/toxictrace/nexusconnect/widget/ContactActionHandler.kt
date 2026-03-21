package com.toxictrace.nexusconnect.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.toxictrace.nexusconnect.data.model.ClickAction
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs

object ContactActionHandler {

    fun handle(context: Context, contactId: Long, phone: String?, name: String?) {
        if (WidgetPrefs.getHapticFeedback(context)) vibrate(context)

        when (WidgetPrefs.getClickAction(context)) {
            ClickAction.SHOW_DIALOG -> showChooser(context, contactId, phone, name)
            ClickAction.DIRECT_CALL -> directCall(context, phone)
        }
    }

    private fun vibrate(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(40)
                }
            }
        } catch (_: Exception) {}
    }

    private fun directCall(context: Context, phone: String?) {
        if (phone.isNullOrBlank()) return
        val hasPermission = context.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        context.startActivity(
            Intent(action, Uri.parse("tel:$phone"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun showChooser(context: Context, contactId: Long, phone: String?, name: String?) {
        context.startActivity(
            Intent(context, ContactChooserActivity::class.java).apply {
                putExtra("contact_id",    contactId)
                putExtra("contact_phone", phone ?: "")
                putExtra("contact_name",  name ?: "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                         Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        )
    }
}
