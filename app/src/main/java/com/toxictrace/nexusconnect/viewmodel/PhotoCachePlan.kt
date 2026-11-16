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

    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun buildNative(
        contactIds: LongArray,
        photoUris: Array<String>,
        hasPhoto: LongArray,
        cachedBitmapIds: LongArray,
        cachedPhotoIds: LongArray,
        cachedPhotoUris: Array<String>
    ): LongArray

    fun build(
        contacts: List<Contact>,
        cachedBitmapIds: Set<Long>,
        cachedPhotoUris: Map<Long, String>
    ): Plan {
        val result = buildNative(
            contactIds = contacts.map { it.id }.toLongArray(),
            photoUris = contacts.map { it.photoUri.orEmpty() }.toTypedArray(),
            hasPhoto = contacts.map { if (!it.photoUri.isNullOrBlank()) 1L else 0L }.toLongArray(),
            cachedBitmapIds = cachedBitmapIds.toLongArray(),
            cachedPhotoIds = cachedPhotoUris.keys.toLongArray(),
            cachedPhotoUris = cachedPhotoUris.values.toTypedArray()
        )

        val retainedCount = result[0].toInt()
        val reloadCount = result[1].toInt()
        val loadCount = result[2].toInt()
        val retainedStart = 3
        val reloadStart = retainedStart + retainedCount
        val loadStart = reloadStart + reloadCount

        return Plan(
            retainedIds = result.copyOfRange(retainedStart, reloadStart).toSet(),
            idsToReload = result.copyOfRange(reloadStart, loadStart).toSet(),
            contactsToLoad = result.copyOfRange(loadStart, loadStart + loadCount)
                .map { index -> contacts[index.toInt()] }
        )
    }
}
