package com.toxictrace.nexusconnect.widget

import com.toxictrace.nexusconnect.data.model.Contact

/** Rust-backed resolution of saved widget favorites. */
internal object WidgetFavoriteResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    private external fun resolveNative(
        allIds: LongArray,
        allLookupKeys: Array<String>,
        selectedIds: LongArray,
        selectedLookupKeys: Array<String>,
        maxTiles: Int
    ): IntArray

    fun resolve(
        allContacts: List<Contact>,
        selectedIds: List<Long>,
        selectedLookupKeys: List<String>,
        maxTiles: Int
    ): List<Contact> {
        val indices = resolveNative(
            allContacts.map { it.id }.toLongArray(),
            allContacts.map { it.lookupKey.orEmpty() }.toTypedArray(),
            selectedIds.toLongArray(),
            selectedLookupKeys.toTypedArray(),
            maxTiles
        )
        return indices.map { allContacts.getOrNull(it) }.filterNotNull()
    }
}
