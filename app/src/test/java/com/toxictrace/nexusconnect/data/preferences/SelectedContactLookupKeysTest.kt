package com.toxictrace.nexusconnect.data.preferences

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedContactLookupKeysTest {
    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    @Test
    fun parsesOpaqueKeysIncludingSeparators() {
        val keys = listOf("0r7-abc:person,one", "lookup/key_two", "another-key")
        assertEquals(keys, SelectedContactLookupKeys.parse(keys.joinToString(",") { encode(it) }))
    }

    @Test
    fun emptyInputReturnsEmptyList() {
        assertEquals(emptyList<String>(), SelectedContactLookupKeys.parse(""))
    }

    @Test
    fun ignoresInvalidEncodedTokens() {
        val raw = "%%%," + encode("valid")
        assertEquals(listOf("valid"), SelectedContactLookupKeys.parse(raw))
    }
}
