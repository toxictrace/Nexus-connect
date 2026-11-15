package com.toxictrace.nexusconnect.data.preferences

/** Converts persisted comma-separated contact IDs to an ordered list. */
internal object SelectedContactIds {
    fun parse(raw: String): List<Long> = NativeSelectedContactIds.parse(raw).toList()

    fun serialize(ids: List<Long>): String =
        if (ids.isEmpty()) "" else NativeSelectedContactIds.serialize(ids.toLongArray())
}

/** Rust-backed selected-contact ID storage operations. */
internal object NativeSelectedContactIds {
    init { System.loadLibrary("nexus_phone") }

    @JvmStatic private external fun serializeNative(ids: LongArray): String
    @JvmStatic private external fun parseNative(raw: String): LongArray

    fun serialize(ids: LongArray): String = serializeNative(ids)
    fun parse(raw: String): LongArray = parseNative(raw)
}
