package com.toxictrace.nexusconnect.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.toxictrace.nexusconnect.data.model.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class ContactsRepository(private val context: Context) {

    /**
     * Flow that emits the full contacts list and re-emits whenever
     * the system contacts database changes (ContentObserver).
     */
    fun observeContacts(): Flow<List<Contact>> = callbackFlow {
        // Send initial list
        trySend(loadContacts())

        // Watch for changes in the contacts database
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { trySend(loadContacts()) }
            }
        }

        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    fun loadContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
        ) ?: return emptyList()

        val seenIds = mutableSetOf<Long>()

        cursor.use {
            val idIdx     = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx   = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numIdx    = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val thumbIdx  = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val photoIdx  = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val starIdx   = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)

            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                if (id in seenIds) continue
                seenIds.add(id)

                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numIdx)
                val thumbStr = it.getString(thumbIdx)
                val photoStr = it.getString(photoIdx)
                val starred = it.getInt(starIdx) != 0

                contacts.add(
                    Contact(
                        id = id,
                        name = name,
                        phoneNumber = number,
                        photoUri = (photoStr ?: thumbStr)?.let { s -> Uri.parse(s) },
                        isStarred = starred
                    )
                )
            }
        }

        return contacts
    }
}
