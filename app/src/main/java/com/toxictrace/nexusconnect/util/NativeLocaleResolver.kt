package com.toxictrace.nexusconnect.util

/** Resolves the app's English/Russian locale fallback in Rust. */
internal object NativeLocaleResolver {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    private external fun resolveNative(language: String, systemLanguage: String): String

    fun resolve(language: String, systemLanguage: String): String =
        resolveNative(language, systemLanguage)
}
