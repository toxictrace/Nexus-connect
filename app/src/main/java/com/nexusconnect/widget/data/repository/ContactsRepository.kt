package com.nexusconnect.widget.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.nexusconnect.widget.data.models.ContactModel

class ContactsRepository(private val context: Context) {

    fun getAllContacts(): List<ContactModel> {
        val contacts = mutableListOf<ContactModel>()
        val cr: ContentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        val cursor = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        ) ?: return contacts

        cursor.use {
            val idCol = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameCol = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

            while (it.moveToNext()) {
                val id = it.getString(idCol) ?: continue
                val name = it.getString(nameCol) ?: continue
                val photoUri = it.getString(photoCol)
                val phone = getPhoneNumber(cr, id)

                contacts.add(
                    ContactModel(
                        id = id,
                        name = name,
                        phone = phone,
                        photoUri = photoUri
                    )
                )
            }
        }

        return contacts
    }

    private fun getPhoneNumber(cr: ContentResolver, contactId: String): String {
        val phoneCursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        ) ?: return ""

        phoneCursor.use {
            if (it.moveToFirst()) {
                val col = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return it.getString(col) ?: ""
            }
        }
        return ""
    }
}
