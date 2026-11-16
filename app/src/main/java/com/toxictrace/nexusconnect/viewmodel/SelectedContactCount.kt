package com.toxictrace.nexusconnect.viewmodel

import com.toxictrace.nexusconnect.data.model.Contact

/** Counts selected IDs that still exist in the current contact list through Rust JNI. */
internal object SelectedContactCount {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun countNative(selectedIds: LongArray, contactIds: LongArray): Int

    fun count(selectedIds: List<Long>, contacts: List<Contact>): Int =
        countNative(selectedIds.toLongArray(), contacts.map { it.id }.toLongArray())
}
