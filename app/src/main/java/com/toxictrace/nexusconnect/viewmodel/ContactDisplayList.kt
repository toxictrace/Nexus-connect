package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact

/**
 * Builds the contact list shown by the selection screen.
 * Selected contacts stay at the top in their saved order; search filters only unselected contacts.
 */
internal object ContactDisplayList {
    fun build(allContacts: List<Contact>, selectedIds: List<Long>, query: String): List<Contact> {
        val selectedIdSet = selectedIds.toSet()
        val selectedById = allContacts
            .filter { it.id in selectedIdSet }
            .associateBy { it.id }

        val selectedOrdered = selectedIds.mapNotNull { id ->
            selectedById[id]?.let { if (it.isSelected) it else it.copy(isSelected = true) }
        }
        val unselected = allContacts.filter { it.id !in selectedIdSet }.let { contacts ->
            if (query.isBlank()) contacts
            else contacts.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.phoneNumber?.contains(query) == true
            }
        }
        return selectedOrdered + unselected
    }
}
