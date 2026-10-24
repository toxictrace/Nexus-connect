package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact

/**
 * Describes which cached contact photos can be reused and which must be loaded again.
 * Contacts without a photo URI are excluded from the bitmap cache.
 */
internal object PhotoCachePlan {
    data class Plan(
        val retainedIds: Set<Long>,
        val idsToReload: Set<Long>,
        val contactsToLoad: List<Contact>
    )

    fun build(
        contacts: List<Contact>,
        cachedBitmapIds: Set<Long>,
        cachedPhotoUris: Map<Long, String>
    ): Plan {
        val photoContacts = contacts.filter { !it.photoUri.isNullOrBlank() }
        val retainedIds = photoContacts.mapTo(mutableSetOf()) { it.id }
        val idsToReload = photoContacts
            .filter { it.id !in cachedBitmapIds || cachedPhotoUris[it.id] != it.photoUri }
            .mapTo(mutableSetOf()) { it.id }

        return Plan(
            retainedIds = retainedIds,
            idsToReload = idsToReload,
            contactsToLoad = photoContacts.filter { it.id in idsToReload }
        )
    }
}
