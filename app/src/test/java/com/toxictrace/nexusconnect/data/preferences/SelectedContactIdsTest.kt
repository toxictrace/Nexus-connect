package com.toxictrace.nexusconnect.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedContactIdsTest {
    @Test fun parsesOrderedIds() {
        assertEquals(listOf(42L, 7L, 99L), SelectedContactIds.parse("42,7,99"))
    }

    @Test fun emptyStringProducesEmptyList() {
        assertEquals(emptyList<Long>(), SelectedContactIds.parse(""))
    }

    @Test fun ignoresInvalidTokensAndKeepsValidOrder() {
        assertEquals(listOf(42L, -7L, 99L), SelectedContactIds.parse("42,nope,-7,,99"))
    }

    @Test fun preservesDuplicatesAndAcceptsExplicitPositiveSign() {
        assertEquals(listOf(7L, 7L, -2L), SelectedContactIds.parse("+7,7,-2"))
    }

    @Test fun serializesIdsInExistingCommaSeparatedFormat() {
        assertEquals("4,7,-2", SelectedContactIds.serialize(listOf(4L, 7L, -2L)))
    }

    @Test fun ignoresOverflowAndWhitespaceTokens() {
        assertEquals(listOf(1L), SelectedContactIds.parse("9223372036854775808, 1,1"))
    }
}
