package com.toxictrace.nexusconnect.util

/**
 * Rust-backed phone number normalization.
 * The native library is packaged for Android ABIs by the CI workflow.
 */
object NativePhoneNumber {
    init {
        System.loadLibrary("nexus_phone")
    }

    @JvmStatic
    external fun normalize(number: String): String
}
