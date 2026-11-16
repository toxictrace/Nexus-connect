package com.toxictrace.nexusconnect.widget

/**
 * Aggregates call-log rows already matched against the contact's normalized numbers.
 * Cursor access and number matching remain in Android Kotlin code.
 */
internal object CallStatsResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    fun resolve(
        dates: List<Long>,
        durations: List<Long>,
        callTypes: List<Int>
    ): LongArray = resolveNative(
        dates.toLongArray(),
        durations.toLongArray(),
        callTypes.toIntArray()
    )

    private external fun resolveNative(
        dates: LongArray,
        durations: LongArray,
        callTypes: IntArray
    ): LongArray
}
