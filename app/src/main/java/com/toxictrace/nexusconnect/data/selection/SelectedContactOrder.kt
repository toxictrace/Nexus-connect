package com.toxictrace.nexusconnect.data.selection

/**
 * Pure operations for the ordered list of selected contact IDs.
 *
 * Keeping this logic independent from Android makes ordering behavior easy to test.
 */
object SelectedContactOrder {

    /**
     * Moves [contactId] one position in [direction] (-1 up, +1 down).
     * Returns the original order when the direction is invalid, the ID is absent,
     * or the contact is already at the requested boundary.
     */
    fun move(ids: List<Long>, contactId: Long, direction: Int): List<Long> {
        if (direction != -1 && direction != 1) return ids

        val fromIndex = ids.indexOf(contactId)
        if (fromIndex == -1) return ids

        val toIndex = fromIndex + direction
        if (toIndex !in ids.indices) return ids

        return ids.toMutableList().also { moved ->
            val neighbor = moved[toIndex]
            moved[toIndex] = moved[fromIndex]
            moved[fromIndex] = neighbor
        }
    }
}
