package com.toxictrace.nexusconnect.widget

/** Selects recent known and unknown widget contacts in Rust. */
internal object WidgetRecentContactResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun resolveNative(
        mixedIds: LongArray,
        validUnknownNumbers: LongArray,
        contactIds: LongArray,
        usedContactIds: LongArray,
        filterRecents: Int,
        showUnknownNumbers: Int,
        maxTiles: Int,
        initialCount: Int
    ): IntArray

    fun resolve(
        mixed: List<Pair<Long, String?>>,
        allContactIds: LongArray,
        usedContactIds: LongArray,
        filterRecents: Boolean,
        showUnknownNumbers: Boolean,
        maxTiles: Int,
        initialCount: Int
    ): List<Int> = resolveNative(
        mixed.map { it.first }.toLongArray(),
        mixed.map { if (it.second.isNullOrBlank()) 0L else 1L }.toLongArray(),
        allContactIds,
        usedContactIds,
        if (filterRecents) 1 else 0,
        if (showUnknownNumbers) 1 else 0,
        maxTiles,
        initialCount
    ).toList()
}
