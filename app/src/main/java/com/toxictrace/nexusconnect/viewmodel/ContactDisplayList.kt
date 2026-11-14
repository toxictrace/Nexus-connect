package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact

/**
 * Builds the contact list shown by the selection screen.
 * Selected contacts stay at the top in their saved order; search filters only unselected contacts.
 */
internal object ContactDisplayList {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun buildOrderNative(
        allIds: LongArray,
        selectedIds: LongArray,
        queryMatches: LongArray,
        queryIsBlank: Int
    ): IntArray

    fun build(allContacts: List<Contact>, selectedIds: List<Long>, query: String): List<Contact> {
        val queryIsBlank = query.isBlank()
        val queryMatches = allContacts.map { contact ->
            queryIsBlank ||
                contact.name.contains(query, ignoreCase = true) ||
                contact.phoneNumber?.contains(query) == true
        }.map { if (it) 1L else 0L }.toLongArray()

        val order = buildOrderNative(
            allContacts.map { it.id }.toLongArray(),
            selectedIds.toLongArray(),
            queryMatches,
            if (queryIsBlank) 1 else 0
        )

        val selectedIdSet = selectedIds.toSet()
        return order.map { index ->
            val contact = allContacts[index]
            if (contact.id in selectedIdSet && !contact.isSelected) {
                contact.copy(isSelected = true)
            } else {
                contact
            }
        }
    }
}
