package com.toxictrace.nexusconnect.data.preferences

/**
 * Converts persisted comma-separated contact IDs to an ordered list.
 * Invalid tokens are ignored to preserve the existing recovery behavior.
 */
internal object SelectedContactIds {
    fun parse(raw: String): List<Long> =
        raw.split(",").mapNotNull { it.toLongOrNull() }

    fun serialize(ids: List<Long>): String =
        NativeSelectedContactIds.serialize(ids.toLongArray())
}

/** Rust-backed serializer for the existing selected-contact ID storage format. */
internal object NativeSelectedContactIds {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun serializeNative(ids: LongArray): String

    fun serialize(ids: LongArray): String = serializeNative(ids)
}
