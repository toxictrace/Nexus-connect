package com.toxictrace.nexusconnect.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * ContentProvider that serves contact photos to the widget.
 * Caches photos to disk so Nova can load them as files.
 *
 * URI: content://com.toxictrace.nexusconnect.photos/{contactId}
 */
class PhotoProvider : ContentProvider() {

    companion object {
        private const val TAG = "PhotoProvider"
        const val AUTHORITY = "com.toxictrace.nexusconnect.photos"

        fun uriForContact(contactId: Long): Uri =
            Uri.parse("content://$AUTHORITY/$contactId")
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val contactId = uri.lastPathSegment?.toLongOrNull() ?: return null
        val ctx = context ?: return null

        // Try to get photo URI from contacts database
        val photoUri = getPhotoUri(contactId) ?: return null

        return try {
            // Open directly from contacts ContentResolver
            ctx.contentResolver.openFileDescriptor(photoUri, "r")
        } catch (e: Exception) {
            Log.w(TAG, "Direct open failed, trying pipe: ${e.message}")
            // Fallback: pipe through our process
            try {
                val pipe = ParcelFileDescriptor.createPipe()
                val inputStream = ctx.contentResolver.openInputStream(photoUri)
                    ?: return null
                Thread {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                            inputStream.use { it.copyTo(out) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Pipe write failed: ${e.message}")
                    }
                }.start()
                pipe[0]
            } catch (e2: Exception) {
                Log.e(TAG, "Pipe fallback failed: ${e2.message}")
                null
            }
        }
    }

    private fun getPhotoUri(contactId: Long): Uri? {
        val ctx = context ?: return null
        return try {
            // Method 1: PHOTO_URI column from Contacts table
            val cursor = ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.PHOTO_URI),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val photoStr = it.getString(0)
                    if (!photoStr.isNullOrBlank()) return Uri.parse(photoStr)
                }
            }

            // Method 2: Photo.CONTENT_DIRECTORY
            Uri.withAppendedPath(
                Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_URI,
                    contactId.toString()
                ),
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
        } catch (e: Exception) {
            Log.e(TAG, "getPhotoUri failed: ${e.message}")
            null
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
