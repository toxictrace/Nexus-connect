package com.toxictrace.nexusconnect.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs
import java.io.File
import java.io.FileOutputStream

/**
 * ContentProvider serving the default/custom avatar for the widget.
 * URI: content://com.toxictrace.nexusconnect.avatar/default
 *      content://com.toxictrace.nexusconnect.avatar/custom
 *
 * Using setImageViewUri() bypasses IPC bitmap limits → full quality.
 */
class AvatarProvider : ContentProvider() {

    companion object {
        private const val TAG = "AvatarProvider"
        const val AUTHORITY = "com.toxictrace.nexusconnect.avatar"

        fun defaultUri(): Uri = Uri.parse("content://$AUTHORITY/default")
        fun customUri(): Uri  = Uri.parse("content://$AUTHORITY/custom")
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        return try {
            when (uri.lastPathSegment) {
                "default" -> {
                    // Serve avatar_default.png from drawable via temp file cache
                    val cacheFile = File(ctx.cacheDir, "avatar_default_cache.png")
                    if (!cacheFile.exists()) {
                        ctx.resources.openRawResource(R.drawable.avatar_default).use { input ->
                            FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                        }
                    }
                    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                "custom" -> {
                    val customUri = WidgetPrefs.getCustomAvatarUri(ctx)
                    if (customUri.isBlank()) return openFile(defaultUri(), mode)
                    ctx.contentResolver.openFileDescriptor(Uri.parse(customUri), "r")
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "openFile failed: ${e.message}")
            null
        }
    }

    override fun getType(uri: Uri): String = "image/png"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
