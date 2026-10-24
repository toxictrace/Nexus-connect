package com.toxictrace.nexusconnect.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionLimitTest {
    @Test
    fun keepsSelectionWhenWithinCapacity() {
        assertEquals(listOf(4L, 8L), SelectionLimit.trim(listOf(4L, 8L), 3))
    }

    @Test
    fun trimsExcessIdsAndPreservesOrder() {
        assertEquals(listOf(4L, 8L), SelectionLimit.trim(listOf(4L, 8L, 2L), 2))
    }

    @Test
    fun zeroCapacityReturnsNoIds() {
        assertEquals(emptyList<Long>(), SelectionLimit.trim(listOf(4L, 8L), 0))
    }

    @Test
    fun emptySelectionRemainsEmpty() {
        assertEquals(emptyList<Long>(), SelectionLimit.trim(emptyList(), 12))
    }
}
