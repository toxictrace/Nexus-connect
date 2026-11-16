package com.toxictrace.nexusconnect.widget

/** Selects matching call-log rows in Rust while preserving their original order. */
internal object CallStatsCallFilter {
    init {
        System.loadLibrary("nexus_phone")
    }

    private external fun filterNative(
        callNorms: Array<String>,
        targetNorms: Array<String>
    ): IntArray

    fun filter(callNorms: List<String>, targetNorms: List<String>): List<Int> =
        filterNative(callNorms.toTypedArray(), targetNorms.toTypedArray()).toList()
}
