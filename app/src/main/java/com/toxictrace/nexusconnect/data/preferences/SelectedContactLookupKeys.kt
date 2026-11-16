package com.toxictrace.nexusconnect.data.preferences

import java.util.Base64

/** Encodes opaque Contacts Provider lookup keys for safe comma-separated persistence. */
internal object SelectedContactLookupKeys {
    fun parse(raw: String): List<String> = NativeSelectedContactLookupKeys.parse(raw).toList()

    fun serialize(keys: List<String>): String {
        val nonBlankKeys = keys.filter { it.isNotBlank() }
        if (nonBlankKeys.isEmpty()) return ""
        return NativeSelectedContactLookupKeys.serialize(nonBlankKeys.toTypedArray())
    }
}

/** Rust-backed operations with a JVM-only fallback for local unit tests. */
internal object NativeSelectedContactLookupKeys {
    private val useNative = System.getProperty("java.vm.name")?.contains("Dalvik") == true
    init { if (useNative) System.loadLibrary("nexus_phone") }

    @JvmStatic private external fun serializeNative(keys: Array<String>): String
    @JvmStatic private external fun parseNative(raw: String): Array<String>

    fun serialize(keys: Array<String>): String =
        if (useNative) serializeNative(keys) else keys.joinToString(",") {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
        }

    fun parse(raw: String): Array<String> =
        if (useNative) parseNative(raw) else raw.split(',')
            .mapNotNull { token ->
                runCatching { Base64.getUrlDecoder().decode(token) }
                    .getOrNull()
                    ?.toString(Charsets.UTF_8)
                    ?.takeUnless { it.isBlank() }
            }
            .toTypedArray()
}
