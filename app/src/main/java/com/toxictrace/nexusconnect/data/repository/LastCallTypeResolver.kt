package com.toxictrace.nexusconnect.data.repository

/**
 * Resolves the newest call-log row for each requested normalized number.
 * Android call-log access and result mapping remain in Kotlin.
 */
internal object LastCallTypeResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    fun resolve(
        normalizedNumbers: List<String>,
        callTypes: List<Int>,
        requestedNorms: List<String>
    ): List<Int> = resolveNative(
        normalizedNumbers.toTypedArray(),
        callTypes.toIntArray(),
        requestedNorms.toTypedArray()
    ).toList()

    private external fun resolveNative(
        normalizedNumbers: Array<String>,
        callTypes: IntArray,
        requestedNorms: Array<String>
    ): IntArray
}
