package com.toxictrace.nexusconnect.data.preferences

import java.util.Base64

/** Encodes opaque Contacts Provider lookup keys for safe comma-separated persistence. */
internal object SelectedContactLookupKeys {
    fun parse(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { token ->
            runCatching { String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8) }
                .getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    fun serialize(keys: List<String>): String =
        NativeSelectedContactLookupKeys.serialize(keys.filter { it.isNotBlank() }.toTypedArray())
}

/** Rust-backed serializer for the existing URL-safe Base64 storage format. */
internal object NativeSelectedContactLookupKeys {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun serializeNative(keys: Array<String>): String

    fun serialize(keys: Array<String>): String = serializeNative(keys)
}
