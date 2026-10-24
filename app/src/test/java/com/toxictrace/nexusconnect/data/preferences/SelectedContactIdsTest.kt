package com.toxictrace.nexusconnect.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedContactIdsTest {
    @Test
    fun parsesOrderedIds() {
        assertEquals(listOf(42L, 7L, 99L), SelectedContactIds.parse("42,7,99"))
    }

    @Test
    fun emptyStringProducesEmptyList() {
        assertEquals(emptyList<Long>(), SelectedContactIds.parse(""))
    }

    @Test
    fun ignoresInvalidTokensAndKeepsValidOrder() {
        assertEquals(listOf(42L, -7L, 99L), SelectedContactIds.parse("42,nope,-7,,99"))
    }

    @Test
    fun serializationPreservesOrderAndDuplicates() {
        assertEquals("42,7,42", SelectedContactIds.serialize(listOf(42L, 7L, 42L)))
    }

    @Test
    fun emptyListSerializesToEmptyString() {
        assertEquals("", SelectedContactIds.serialize(emptyList()))
    }

    @Test
    fun serializedIdsCanBeParsedBackWithoutChangingOrder() {
        val ids = listOf(15L, 3L, 81L)
        assertEquals(ids, SelectedContactIds.parse(SelectedContactIds.serialize(ids)))
    }
}
