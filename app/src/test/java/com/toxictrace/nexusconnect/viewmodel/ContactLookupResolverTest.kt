package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactLookupResolverTest {
    @Test
    fun resolvesSavedFavoriteAfterProviderChangesContactId() {
        val current = listOf(Contact(id = 4553L, name = "Contact", lookupKey = "stable-person-key"))
        assertEquals(listOf(4553L), ContactLookupResolver.resolveIds(listOf("stable-person-key"), current))
    }

    @Test
    fun preservesOrderAndSkipsUnavailableKeys() {
        val current = listOf(
            Contact(id = 4553L, name = "A", lookupKey = "key-a"),
            Contact(id = 7001L, name = "B", lookupKey = "key-b")
        )
        assertEquals(listOf(7001L, 4553L), ContactLookupResolver.resolveIds(listOf("key-b", "missing-key", "key-a"), current))
    }

    @Test
    fun extractsKeysInSelectedIdOrder() {
        val current = listOf(
            Contact(id = 4553L, name = "A", lookupKey = "key-a"),
            Contact(id = 7001L, name = "B", lookupKey = "key-b")
        )
        assertEquals(listOf("key-b", "key-a"), ContactLookupResolver.lookupKeysForIds(listOf(7001L, 4553L), current))
    }
}
