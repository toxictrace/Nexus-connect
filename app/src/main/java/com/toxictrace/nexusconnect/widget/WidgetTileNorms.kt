package com.toxictrace.nexusconnect.widget

import com.toxictrace.nexusconnect.data.model.Contact

/** Rust-backed preparation of normalized phone numbers used by widget tiles. */
internal object WidgetTileNorms {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun extractNative(
        numberKeys: Array<String>,
        numberIds: LongArray,
        tileIds: LongArray,
        tilePhoneNumbers: Array<String>
    ): Array<String>

    fun build(tileContacts: List<Contact>, numberMap: Map<String, Long>): List<String> =
        extractNative(
            numberKeys = numberMap.keys.toTypedArray(),
            numberIds = numberMap.values.toLongArray(),
            tileIds = tileContacts.map { it.id }.toLongArray(),
            tilePhoneNumbers = tileContacts.map { it.phoneNumber.orEmpty() }.toTypedArray()
        ).toList()
}
