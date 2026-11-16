package com.toxictrace.nexusconnect.widget

/** Rust-backed phone filtering for existing messenger URI formats. */
internal object MessengerPhoneResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun sanitizeNative(phone: String): String

    fun sanitize(phone: String): String = sanitizeNative(phone)
}
