package com.toxictrace.nexusconnect.viewmodel

/** Merges current visible lookup keys with saved keys that no longer resolve. */
internal object UpdatedSelectionLookupKeys {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun mergeNative(
        visibleKeys: Array<String>,
        oldVisibleKeys: Array<String>,
        savedKeys: Array<String>
    ): Array<String>

    fun merge(
        visibleKeys: List<String>,
        oldVisibleKeys: Set<String>,
        savedKeys: List<String>
    ): List<String> = mergeNative(
        visibleKeys.toTypedArray(),
        oldVisibleKeys.toTypedArray(),
        savedKeys.toTypedArray()
    ).toList()
}
