package com.toxictrace.nexusconnect.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import android.util.Log

/**
 * Serves contact photos to the widget via setImageViewUri.
 * URI includes a version timestamp so the launcher cache is invalidated
 * when contacts change.
 */
class PhotoProvider : ContentProvider() {

    companion object {
        private const val TAG = "PhotoProvider"
        const val AUTHORITY = "com.toxictrace.nexusconnect.photos"

        // Incremented on every widget update — forces launcher to reload photos
        @Volatile private var cacheVersion: Long = System.currentTimeMillis()

        fun invalidateCache() {
            cacheVersion = System.currentTimeMillis()
        }

        fun uriForContact(contactId: Long): Uri =
            Uri.parse("content://$AUTHORITY/$contactId?v=$cacheVersion")
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // lastPathSegment may be "123?v=..." — parse only the numeric part
        val contactId = uri.lastPathSegment?.substringBefore("?")?.toLongOrNull() ?: return null
        val ctx = context ?: return null
        val photoUri = getPhotoUri(contactId) ?: return null
        return try {
            ctx.contentResolver.openFileDescriptor(photoUri, "r")
        } catch (e: Exception) {
            try {
                val pipe = ParcelFileDescriptor.createPipe()
                val inputStream = ctx.contentResolver.openInputStream(photoUri) ?: return null
                Thread {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                            inputStream.use { it.copyTo(out) }
                        }
                    } catch (_: Exception) {}
                }.start()
                pipe[0]
            } catch (_: Exception) { null }
        }
    }

    private fun getPhotoUri(contactId: Long): Uri? {
        val ctx = context ?: return null
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
                    if (!s.isNullOrBlank()) return Uri.parse(s)
                }
            }
            Uri.withAppendedPath(
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString()),
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
        } catch (e: Exception) { null }
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
