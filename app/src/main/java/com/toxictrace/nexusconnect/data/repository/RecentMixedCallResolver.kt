package com.toxictrace.nexusconnect.data.repository

/** Selects recent known and unknown call-log rows after Android reads the cursor. */
internal object RecentMixedCallResolver {
    init { System.loadLibrary("nexus_phone") }

    fun resolve(
        norms: List<String>, dates: List<Long>, contactIds: List<Long>, validUnknown: List<Int>,
        contactCutoff: Long, unknownCutoff: Long,
        contactsDisabled: Boolean, unknownDisabled: Boolean, limit: Int
    ): List<Int> = resolveNative(
        norms.toTypedArray(), dates.toLongArray(), contactIds.toLongArray(), validUnknown.toIntArray(),
        contactCutoff, unknownCutoff, if (contactsDisabled) 1 else 0,
        if (unknownDisabled) 1 else 0, limit
    ).toList()

    private external fun resolveNative(
        norms: Array<String>, dates: LongArray, contactIds: LongArray, validUnknown: IntArray,
        contactCutoff: Long, unknownCutoff: Long, contactsDisabled: Int, unknownDisabled: Int, limit: Int
    ): IntArray
}
