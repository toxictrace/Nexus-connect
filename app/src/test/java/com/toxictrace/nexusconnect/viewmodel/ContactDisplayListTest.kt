package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDisplayListTest {
    private val contacts = listOf(
        Contact(id = 1L, name = "Alice"),
        Contact(id = 2L, name = "Bob", phoneNumber = "555-1234"),
        Contact(id = 3L, name = "Carol"),
        Contact(id = 4L, name = "David")
    )

    @Test
    fun selectedContactsAppearFirstInSavedOrder() {
        val result = ContactDisplayList.build(contacts, listOf(3L, 1L), "")

        assertEquals(listOf(3L, 1L, 2L, 4L), result.map { it.id })
        assertTrue(result[0].isSelected)
        assertTrue(result[1].isSelected)
    }

    @Test
    fun searchFiltersOnlyUnselectedContacts() {
        val result = ContactDisplayList.build(contacts, listOf(3L), "bob")

        assertEquals(listOf(3L, 2L), result.map { it.id })
    }

    @Test
    fun searchMatchesNamesIgnoringCaseAndPhoneNumbers() {
        val byName = ContactDisplayList.build(contacts, emptyList(), "ALI")
        val byPhone = ContactDisplayList.build(contacts, emptyList(), "1234")

        assertEquals(listOf(1L), byName.map { it.id })
        assertEquals(listOf(2L), byPhone.map { it.id })
    }

    @Test
    fun missingSelectedIdsAreSkipped() {
        val result = ContactDisplayList.build(contacts, listOf(99L, 2L), "")

        assertEquals(listOf(2L, 1L, 3L, 4L), result.map { it.id })
    }

    @Test
    fun blankSearchLeavesAllUnselectedContactsVisible() {
        val result = ContactDisplayList.build(contacts, emptyList(), "  ")

        assertEquals(contacts.map { it.id }, result.map { it.id })
    }
}
