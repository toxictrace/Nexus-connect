package com.toxictrace.nexusconnect.widget

import com.toxictrace.nexusconnect.data.model.Contact

/** Resolves frequent contact IDs to contact-list indices in Rust. */
internal object WidgetFrequentContactResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun resolveNative(
        frequentIds: LongArray,
        contactIds: LongArray,
        baseContactIds: LongArray,
        maxTiles: Int
    ): IntArray

    fun resolve(
        frequentIds: List<Long>,
        allContacts: List<Contact>,
        baseContacts: List<Contact>,
        maxTiles: Int
    ): List<Int> = resolveNative(
        frequentIds.toLongArray(),
        allContacts.map { it.id }.toLongArray(),
        baseContacts.map { it.id }.toLongArray(),
        maxTiles
    ).toList()
}
