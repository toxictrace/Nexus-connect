package com.toxictrace.nexusconnect.viewmodel

/**
 * Pure selection-order operations, separated from Android and persistence code
 * so their behavior can be covered by fast unit tests.
 */
internal object ContactSelectionOperations {

    /**
     * Returns the updated selection, or null when adding would exceed maxTiles.
     * Removing an already-selected ID is always allowed.
     */
    fun toggle(selectedIds: List<Long>, contactId: Long, maxTiles: Int): List<Long>? {
        val updated = selectedIds.toMutableList()
        if (contactId in updated) {
            updated.remove(contactId)
            return updated
        }
        if (updated.size >= maxTiles) return null
        updated.add(contactId)
        return updated
    }

    /**
     * Moves an ID one position in the requested direction.
     * Returns null for invalid directions, absent IDs, or list boundaries.
     */
    fun move(selectedIds: List<Long>, contactId: Long, direction: Int): List<Long>? {
        if (direction != -1 && direction != 1) return null

        val fromIndex = selectedIds.indexOf(contactId)
        if (fromIndex == -1) return null

        val toIndex = fromIndex + direction
        if (toIndex !in selectedIds.indices) return null

        val updated = selectedIds.toMutableList()
        val neighbor = updated[toIndex]
        updated[toIndex] = contactId
        updated[fromIndex] = neighbor
        return updated
    }
}
