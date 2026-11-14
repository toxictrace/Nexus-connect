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
}
