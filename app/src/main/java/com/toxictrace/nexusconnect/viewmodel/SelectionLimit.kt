package com.toxictrace.nexusconnect.viewmodel

/** Limits selected contact IDs through the Rust JNI layer. */
internal object SelectionLimit {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun trimNative(ids: LongArray, capacity: Int): LongArray

    fun trim(ids: List<Long>, capacity: Int): List<Long> =
        trimNative(ids.toLongArray(), capacity).toList()
}
