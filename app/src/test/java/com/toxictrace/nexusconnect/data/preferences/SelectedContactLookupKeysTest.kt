package com.toxictrace.nexusconnect.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedContactLookupKeysTest {
    @Test
    fun roundTripsOpaqueKeysIncludingSeparators() {
        val keys = listOf("0r7-abc:person,one", "lookup/key_two", "another-key")
        assertEquals(keys, SelectedContactLookupKeys.parse(SelectedContactLookupKeys.serialize(keys)))
    }

    @Test
    fun emptyListRoundTrips() {
        assertEquals("", SelectedContactLookupKeys.serialize(emptyList()))
        assertEquals(emptyList<String>(), SelectedContactLookupKeys.parse(""))
    }

    @Test
    fun ignoresInvalidEncodedTokens() {
        assertEquals(listOf("valid"), SelectedContactLookupKeys.parse("%%%," + SelectedContactLookupKeys.serialize(listOf("valid"))))
    }
}
