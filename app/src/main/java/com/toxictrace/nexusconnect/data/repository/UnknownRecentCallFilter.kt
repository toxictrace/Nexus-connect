package com.toxictrace.nexusconnect.data.repository

/**
 * Rust-backed filtering and de-duplication for recent unknown calls.
 * Android call-log access and result materialization remain in Kotlin.
 */
internal object UnknownRecentCallFilter {
    init {
        System.loadLibrary("nexus_phone")
    }

    fun filter(
        numbers: List<String>,
        norms: List<String>,
        dates: List<Long>,
        types: List<Int>,
        nonBlank: List<Boolean>,
        knownNorms: List<String>,
        limit: Int
    ): List<Int> = filterNative(
        numbers.toTypedArray(),
        norms.toTypedArray(),
        dates.toLongArray(),
        types.toIntArray(),
        nonBlank.map { if (it) 1 else 0 }.toIntArray(),
        knownNorms.toTypedArray(),
        limit
    ).toList()

    private external fun filterNative(
        numbers: Array<String>,
        norms: Array<String>,
        dates: LongArray,
        types: IntArray,
        nonBlank: IntArray,
        knownNorms: Array<String>,
        limit: Int
    ): IntArray
}
