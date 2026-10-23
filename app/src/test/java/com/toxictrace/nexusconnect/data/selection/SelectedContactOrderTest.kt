package com.toxictrace.nexusconnect.data.selection

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedContactOrderTest {

    private val ids = listOf(10L, 20L, 30L)

    @Test
    fun movesContactUpByStableId() {
        assertEquals(listOf(20L, 10L, 30L), SelectedContactOrder.move(ids, 20L, -1))
    }

    @Test
    fun movesContactDownByStableId() {
        assertEquals(listOf(10L, 30L, 20L), SelectedContactOrder.move(ids, 20L, 1))
    }

    @Test
    fun keepsOrderWhenFirstContactMovesUp() {
        assertEquals(ids, SelectedContactOrder.move(ids, 10L, -1))
    }

    @Test
    fun keepsOrderWhenLastContactMovesDown() {
        assertEquals(ids, SelectedContactOrder.move(ids, 30L, 1))
    }

    @Test
    fun keepsOrderWhenContactIsNotSelected() {
        assertEquals(ids, SelectedContactOrder.move(ids, 99L, 1))
    }

    @Test
    fun keepsOrderForInvalidDirection() {
        assertEquals(ids, SelectedContactOrder.move(ids, 20L, 0))
    }

    @Test
    fun doesNotMutateOriginalList() {
        val original = ids.toList()

        SelectedContactOrder.move(ids, 20L, -1)

        assertEquals(original, ids)
    }
}
