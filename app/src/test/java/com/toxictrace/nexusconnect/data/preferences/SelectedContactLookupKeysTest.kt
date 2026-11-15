package com.toxictrace.nexusconnect.data.preferences

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedContactLookupKeysTest {
    private fun encode(value: String, padded: Boolean = false): String {
        val encoder = if (padded) Base64.getUrlEncoder() else Base64.getUrlEncoder().withoutPadding()
        return encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    @Test fun parsesOpaqueKeysIncludingSeparators() {
        val keys = listOf("0r7-abc:person,one", "lookup/key_two", "another-key")
        assertEquals(keys, SelectedContactLookupKeys.parse(keys.joinToString(",") { encode(it) }))
    }

    @Test fun emptyInputReturnsEmptyList() {
        assertEquals(emptyList<String>(), SelectedContactLookupKeys.parse(""))
    }

    @Test fun ignoresInvalidEncodedTokens() {
        assertEquals(listOf("valid"), SelectedContactLookupKeys.parse("%%%, " + encode("valid")))
    }

    @Test fun acceptsPaddedAndUnpaddedTokens() {
        val raw = listOf(encode("valid"), encode("trailing", padded = true)).joinToString(",")
        assertEquals(listOf("valid", "trailing"), SelectedContactLookupKeys.parse(raw))
    }

    @Test fun ignoresDecodedBlankKeys() {
        val raw = listOf(encode(" "), encode("valid")).joinToString(",")
        assertEquals(listOf("valid"), SelectedContactLookupKeys.parse(raw))
    }

    @Test fun replacesInvalidUtf8BytesLikeJavaDecoder() {
        assertEquals(listOf("�"), SelectedContactLookupKeys.parse("_w"))
    }
}
