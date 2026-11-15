package com.toxictrace.nexusconnect.data.preferences

/** Converts persisted comma-separated contact IDs to an ordered list. */
internal object SelectedContactIds {
    fun parse(raw: String): List<Long> = NativeSelectedContactIds.parse(raw).toList()
    fun serialize(ids: List<Long>): String =
        if (ids.isEmpty()) "" else NativeSelectedContactIds.serialize(ids.toLongArray())
}

/** Rust-backed operations with a JVM-only fallback for local unit tests. */
internal object NativeSelectedContactIds {
    private val useNative = System.getProperty("java.vm.name")?.contains("Dalvik") == true
    init { if (useNative) System.loadLibrary("nexus_phone") }

    @JvmStatic private external fun serializeNative(ids: LongArray): String
    @JvmStatic private external fun parseNative(raw: String): LongArray

    fun serialize(ids: LongArray): String =
        if (useNative) serializeNative(ids) else ids.joinToString(",")

    fun parse(raw: String): LongArray =
        if (useNative) parseNative(raw) else
            raw.split(',').mapNotNull { it.toLongOrNull() }.toLongArray()
}
