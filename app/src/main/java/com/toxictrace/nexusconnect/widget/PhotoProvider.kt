package com.toxictrace.nexusconnect.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import android.util.Log
import com.toxictrace.nexusconnect.util.AppLogger

/**
 * Serves contact photos to the widget via setImageViewUri.
 * URI includes a version timestamp so the launcher cache is invalidated
 * when contacts change.
 */
class PhotoProvider : ContentProvider() {

    companion object {
        private const val TAG = "PhotoProvider"
        const val AUTHORITY = "com.toxictrace.nexusconnect.photos"

        @Volatile private var cacheVersion: Long = System.currentTimeMillis()

        fun invalidateCache() {
            cacheVersion = System.currentTimeMillis()
            AppLogger.i(TAG, "invalidateCache: cacheVersion=$cacheVersion")
        }

        fun uriForContact(contactId: Long): Uri {
            val uri = Uri.parse("content://$AUTHORITY/$contactId?v=$cacheVersion")
            AppLogger.i(TAG, "uriForContact: contactId=$contactId uri=$uri")
            return uri
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val contactId = uri.lastPathSegment?.substringBefore("?")?.toLongOrNull()
        AppLogger.i(TAG, "openFile: requestedUri=$uri mode=$mode contactId=$contactId")
        if (contactId == null) {
            AppLogger.w(TAG, "openFile: cannot parse contactId from uri=$uri")
            return null
        }
        val ctx = context
        if (ctx == null) {
            AppLogger.e(TAG, "openFile: provider context is null for contactId=$contactId")
            return null
        }
        val photoUri = getPhotoUri(contactId)
        if (photoUri == null) {
            AppLogger.w(TAG, "openFile: no photoUri resolved for contactId=$contactId")
            return null
        }
        return try {
            val descriptor = ctx.contentResolver.openFileDescriptor(photoUri, "r")
            AppLogger.i(TAG, "openFile: openFileDescriptor contactId=$contactId photoUri=$photoUri success=${descriptor != null}")
            descriptor
        } catch (e: Exception) {
            AppLogger.w(TAG, "openFile: openFileDescriptor failed contactId=$contactId photoUri=$photoUri error=${e.javaClass.simpleName}:${e.message}; trying pipe")
            try {
                val pipe = ParcelFileDescriptor.createPipe()
                val inputStream = ctx.contentResolver.openInputStream(photoUri)
                if (inputStream == null) {
                    AppLogger.w(TAG, "openFile: openInputStream returned null contactId=$contactId photoUri=$photoUri")
                    pipe[0].close()
                    pipe[1].close()
                    return null
                }
                Thread {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                            inputStream.use { it.copyTo(out) }
                        }
                    } catch (copyError: Exception) {
                        AppLogger.w(TAG, "openFile: pipe copy failed contactId=$contactId error=${copyError.javaClass.simpleName}:${copyError.message}")
                    }
                }.start()
                AppLogger.i(TAG, "openFile: pipe returned contactId=$contactId photoUri=$photoUri")
                pipe[0]
            } catch (pipeError: Exception) {
                AppLogger.e(TAG, "openFile: pipe fallback failed contactId=$contactId", pipeError)
                null
            }
        }
    }

    private fun getPhotoUri(contactId: Long): Uri? {
        val ctx = context
        if (ctx == null) {
            AppLogger.w(TAG, "getPhotoUri: provider context is null contactId=$contactId")
            return null
        }
        return try {
            val cursor = ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.PHOTO_URI),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()), null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val s = it.getString(0)
                    if (!s.isNullOrBlank()) {
                        val resolved = Uri.parse(s)
                        AppLogger.i(TAG, "getPhotoUri: contactId=$contactId PHOTO_URI=$resolved")
                        return resolved
                    }
                    AppLogger.i(TAG, "getPhotoUri: contactId=$contactId PHOTO_URI is blank")
                } else {
                    AppLogger.w(TAG, "getPhotoUri: no Contacts row for contactId=$contactId")
                }
            } ?: AppLogger.w(TAG, "getPhotoUri: query returned null cursor contactId=$contactId")

            val fallback = Uri.withAppendedPath(
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString()),
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
            AppLogger.i(TAG, "getPhotoUri: using fallback contactId=$contactId uri=$fallback")
            fallback
        } catch (e: Exception) {
            AppLogger.e(TAG, "getPhotoUri: query failed contactId=$contactId", e)
            null
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
