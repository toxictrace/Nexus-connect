package com.toxictrace.nexusconnect.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.toxictrace.nexusconnect.data.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ContactsRepository(private val context: Context) {

    fun getContacts(): Flow<List<Contact>> = flow {
        val contacts = mutableListOf<Contact>()
        val contentResolver: ContentResolver = context.contentResolver

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            val seenIds = mutableSetOf<Long>()
            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                if (id in seenIds) continue
                seenIds.add(id)

                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx)
                val photoStr = it.getString(photoIdx)
                val photoUri = photoStr?.let { s -> Uri.parse(s) }

                contacts.add(
                    Contact(
                        id = id,
                        name = name,
                        photoUri = photoUri,
                        phoneNumber = number
                    )
                )
            }
        }
        emit(contacts)
    }.flowOn(Dispatchers.IO)

    /** Returns mock contacts when READ_CONTACTS permission is not granted */
    fun getMockContacts(): List<Contact> = listOf(
        Contact(1, "Sarah Jenkins", phoneNumber = "WHATSAPP"),
        Contact(2, "Marcus Thorne", phoneNumber = "+1 202 555 0143"),
        Contact(3, "Elena Rodriguez", phoneNumber = "TELEGRAM"),
        Contact(4, "David Kim", phoneNumber = "+1 202 555 0199"),
        Contact(5, "Anna Petrova", phoneNumber = "+7 916 123 4567"),
        Contact(6, "James Wilson", phoneNumber = "VIBER"),
        Contact(7, "Li Wei", phoneNumber = "+86 131 0000 0000"),
        Contact(8, "Priya Sharma", phoneNumber = "+91 98765 43210"),
    )
}
