package com.toxictrace.nexusconnect.data.preferences

/** Encodes opaque Contacts Provider lookup keys for safe comma-separated persistence. */
internal object SelectedContactLookupKeys {
    fun parse(raw: String): List<String> = NativeSelectedContactLookupKeys.parse(raw).toList()

    fun serialize(keys: List<String>): String {
        val nonBlankKeys = keys.filter { it.isNotBlank() }
        if (nonBlankKeys.isEmpty()) return ""
        return NativeSelectedContactLookupKeys.serialize(nonBlankKeys.toTypedArray())
    }
}

/** Rust-backed selected-contact lookup-key storage operations. */
internal object NativeSelectedContactLookupKeys {
    init { System.loadLibrary("nexus_phone") }

    @JvmStatic private external fun serializeNative(keys: Array<String>): String
    @JvmStatic private external fun parseNative(raw: String): Array<String>

    fun serialize(keys: Array<String>): String = serializeNative(keys)
    fun parse(raw: String): Array<String> = parseNative(raw)
}
