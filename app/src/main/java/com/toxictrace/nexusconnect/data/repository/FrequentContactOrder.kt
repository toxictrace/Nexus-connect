package com.toxictrace.nexusconnect.data.repository

/** Rust-backed ordering of contacts by incoming and outgoing call frequency. */
internal object FrequentContactOrder {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun orderNative(contactIds: LongArray, callTypes: IntArray): LongArray

    fun order(contactIds: LongArray, callTypes: IntArray): List<Long> =
        orderNative(contactIds, callTypes).toList()
}
