package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCachePlanTest {

    @Test
    fun reusesBitmapWhenPhotoUriHasNotChanged() {
        val contacts = listOf(Contact(id = 1L, name = "A", photoUri = "content://photo/1"))

        val plan = PhotoCachePlan.build(
            contacts = contacts,
            cachedBitmapIds = setOf(1L),
            cachedPhotoUris = mapOf(1L to "content://photo/1")
        )

        assertEquals(setOf(1L), plan.retainedIds)
        assertEquals(emptySet<Long>(), plan.idsToReload)
        assertEquals(emptyList<Contact>(), plan.contactsToLoad)
    }

    @Test
    fun reloadsBitmapWhenPhotoUriChanges() {
        val contacts = listOf(Contact(id = 1L, name = "A", photoUri = "content://photo/new"))

        val plan = PhotoCachePlan.build(
            contacts = contacts,
            cachedBitmapIds = setOf(1L),
            cachedPhotoUris = mapOf(1L to "content://photo/old")
        )

        assertEquals(setOf(1L), plan.idsToReload)
        assertEquals(contacts, plan.contactsToLoad)
    }

    @Test
    fun loadsPhotoWhenBitmapIsMissing() {
        val contacts = listOf(Contact(id = 1L, name = "A", photoUri = "content://photo/1"))

        val plan = PhotoCachePlan.build(
            contacts = contacts,
            cachedBitmapIds = emptySet(),
            cachedPhotoUris = mapOf(1L to "content://photo/1")
        )

        assertEquals(setOf(1L), plan.idsToReload)
        assertEquals(contacts, plan.contactsToLoad)
    }

    @Test
    fun removesDeletedContactsAndContactsWithoutPhotosFromCachePlan() {
        val contacts = listOf(
            Contact(id = 1L, name = "A", photoUri = "content://photo/1"),
            Contact(id = 2L, name = "B", photoUri = null)
        )

        val plan = PhotoCachePlan.build(
            contacts = contacts,
            cachedBitmapIds = setOf(1L, 2L, 3L),
            cachedPhotoUris = mapOf(
                1L to "content://photo/1",
                2L to "content://photo/2",
                3L to "content://photo/3"
            )
        )

        assertEquals(setOf(1L), plan.retainedIds)
        assertEquals(emptySet<Long>(), plan.idsToReload)
    }
}
