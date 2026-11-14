package com.toxictrace.nexusconnect.widget

import com.toxictrace.nexusconnect.data.model.Contact

/** Rust-backed lookup of the call type displayed on a widget contact tile. */
internal object WidgetCallTypeResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    private external fun resolveNative(
        contactId: Long,
        isUnknownWithPhone: Int,
        phoneNorm: String,
        numberKeys: Array<String>,
        numberIds: LongArray,
        callTypeKeys: Array<String>,
        callTypeValues: IntArray
    ): Int

    fun resolve(
        contact: Contact,
        callTypeMap: Map<String, Int>,
        numberMap: Map<String, Long>
    ): Int? {
        val phoneNorm = if (contact.id < 0 && contact.phoneNumber != null) {
            contact.phoneNumber.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
        } else {
            ""
        }
        val entries = numberMap.entries.toList()
        val callEntries = callTypeMap.entries.toList()
        val result = resolveNative(
            contact.id,
            if (contact.id < 0 && contact.phoneNumber != null) 1 else 0,
            phoneNorm,
            entries.map { it.key }.toTypedArray(),
            entries.map { it.value }.toLongArray(),
            callEntries.map { it.key }.toTypedArray(),
            callEntries.map { it.value }.toIntArray()
        )
        return result.takeUnless { it == Int.MIN_VALUE }
    }
}
