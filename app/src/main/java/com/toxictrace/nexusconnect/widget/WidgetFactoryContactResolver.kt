package com.toxictrace.nexusconnect.widget

import com.toxictrace.nexusconnect.data.model.Contact

/** Resolves the legacy collection-widget contact list in Rust. */
internal object WidgetFactoryContactResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    private external fun resolveNative(
        contactIds: LongArray,
        lookupKeys: Array<String>,
        starred: LongArray,
        selectedIds: LongArray,
        selectedLookupKeys: Array<String>,
        filterFavorites: Int,
        maxContacts: Int
    ): IntArray

    fun resolve(
        allContacts: List<Contact>,
        selectedIds: List<Long>,
        selectedLookupKeys: List<String>,
        filterFavorites: Boolean,
        maxContacts: Int
    ): List<Contact> {
        val indices = resolveNative(
            allContacts.map { it.id }.toLongArray(),
            allContacts.map { it.lookupKey }.toTypedArray(),
            allContacts.map { if (it.isStarred) 1L else 0L }.toLongArray(),
            selectedIds.toLongArray(),
            selectedLookupKeys.toTypedArray(),
            if (filterFavorites) 1 else 0,
            maxContacts
        )
        return indices.map { allContacts.getOrNull(it) }.filterNotNull()
    }
}
