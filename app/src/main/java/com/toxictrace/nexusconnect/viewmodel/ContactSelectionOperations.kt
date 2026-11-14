package com.toxictrace.nexusconnect.viewmodel

/**
 * Selection ordering operations implemented in Rust through JNI.
 * Null retains the original meaning: the requested operation cannot be applied.
 */
internal object ContactSelectionOperations {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun toggleNative(
        selectedIds: LongArray,
        contactId: Long,
        maxTiles: Int
    ): LongArray?

    @JvmStatic
    private external fun moveNative(
        selectedIds: LongArray,
        contactId: Long,
        direction: Int
    ): LongArray?

    fun toggle(selectedIds: List<Long>, contactId: Long, maxTiles: Int): List<Long>? =
        toggleNative(selectedIds.toLongArray(), contactId, maxTiles)?.toList()

    fun move(selectedIds: List<Long>, contactId: Long, direction: Int): List<Long>? =
        moveNative(selectedIds.toLongArray(), contactId, direction)?.toList()
}
