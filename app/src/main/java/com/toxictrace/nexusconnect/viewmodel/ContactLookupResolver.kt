package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact

/** Maps persistent Contacts Provider lookup keys to current row IDs through Rust JNI. */
internal object ContactLookupResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun resolveIdsNative(
        lookupKeys: Array<String>,
        contactIds: LongArray,
        contactLookupKeys: Array<String>
    ): LongArray

    @JvmStatic
    private external fun lookupKeysForIdsNative(
        ids: LongArray,
        contactIds: LongArray,
        contactLookupKeys: Array<String>
    ): Array<String>

    fun resolveIds(lookupKeys: List<String>, contacts: List<Contact>): List<Long> {
        val validContacts = contacts.filter { it.lookupKey.isNotBlank() }
        return resolveIdsNative(
            lookupKeys.toTypedArray(),
            validContacts.map { it.id }.toLongArray(),
            validContacts.map { it.lookupKey }.toTypedArray()
        ).toList()
    }

    fun lookupKeysForIds(ids: List<Long>, contacts: List<Contact>): List<String> =
        lookupKeysForIdsNative(
            ids.toLongArray(),
            contacts.map { it.id }.toLongArray(),
            contacts.map { it.lookupKey }.toTypedArray()
        ).filter { it.isNotBlank() }.distinct()
}
