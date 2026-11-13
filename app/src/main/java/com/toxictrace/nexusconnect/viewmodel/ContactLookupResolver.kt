package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact

/** Maps persistent Contacts Provider lookup keys to current row IDs. */
internal object ContactLookupResolver {
    fun resolveIds(lookupKeys: List<String>, contacts: List<Contact>): List<Long> {
        val byLookupKey = contacts.filter { it.lookupKey.isNotBlank() }.associateBy { it.lookupKey }
        return lookupKeys.mapNotNull { key -> byLookupKey[key]?.id }.distinct()
    }

    fun lookupKeysForIds(ids: List<Long>, contacts: List<Contact>): List<String> {
        val byId = contacts.associateBy { it.id }
        return ids.mapNotNull { id -> byId[id]?.lookupKey?.takeIf { it.isNotBlank() } }.distinct()
    }
}
