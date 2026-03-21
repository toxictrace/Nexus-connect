package com.toxictrace.nexusconnect.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import android.util.Log

/**
 * ContentProvider that proxies contact photos to the widget.
 * The widget calls setImageViewUri() with our URI — Nova launcher
 * fetches the photo directly without going through IPC bitmap transfer.
 *
 * URI format: content://com.toxictrace.nexusconnect.photos/{contactId}
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

        return try {
            // Get photo URI from contacts
            val contactUri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_URI,
                contactId.toString()
            )
            val photoUri = Uri.withAppendedPath(
                contactUri,
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
            val fd = ctx.contentResolver.openFileDescriptor(photoUri, "r")
            fd
        } catch (e: Exception) {
            Log.w(TAG, "openFile failed for contactId=$contactId: ${e.message}")
            null
        }
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
