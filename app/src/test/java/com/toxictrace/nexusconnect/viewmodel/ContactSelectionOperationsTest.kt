package com.toxictrace.nexusconnect.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactSelectionOperationsTest {

    @Test
    fun toggleAddsContactAtEnd() {
        assertEquals(listOf(1L, 2L, 3L), ContactSelectionOperations.toggle(listOf(1L, 2L), 3L, 4))
    }

    @Test
    fun toggleRemovesSelectedContactWithoutChangingOtherOrder() {
        assertEquals(listOf(1L, 3L), ContactSelectionOperations.toggle(listOf(1L, 2L, 3L), 2L, 3))
    }

    @Test
    fun toggleRejectsAdditionWhenAtCapacity() {
        assertNull(ContactSelectionOperations.toggle(listOf(1L, 2L), 3L, 2))
    }

    @Test
    fun toggleAllowsRemovalWhenAtCapacity() {
        assertEquals(listOf(1L), ContactSelectionOperations.toggle(listOf(1L, 2L), 2L, 2))
    }

    @Test
    fun moveSwapsContactUpOnePosition() {
        assertEquals(listOf(2L, 1L, 3L), ContactSelectionOperations.move(listOf(1L, 2L, 3L), 2L, -1))
    }

    @Test
    fun moveSwapsContactDownOnePosition() {
        assertEquals(listOf(1L, 3L, 2L), ContactSelectionOperations.move(listOf(1L, 2L, 3L), 2L, 1))
    }

    @Test
    fun moveRejectsInvalidDirection() {
        assertNull(ContactSelectionOperations.move(listOf(1L, 2L), 1L, 0))
        assertNull(ContactSelectionOperations.move(listOf(1L, 2L), 1L, 2))
    }

    @Test
    fun moveRejectsMissingIdAndBoundaryMoves() {
        assertNull(ContactSelectionOperations.move(listOf(1L, 2L), 9L, -1))
        assertNull(ContactSelectionOperations.move(listOf(1L, 2L), 1L, -1))
        assertNull(ContactSelectionOperations.move(listOf(1L, 2L), 2L, 1))
    }

    @Test
    fun moveOnEmptySelectionIsRejected() {
        assertNull(ContactSelectionOperations.move(emptyList(), 1L, 1))
    }
}
