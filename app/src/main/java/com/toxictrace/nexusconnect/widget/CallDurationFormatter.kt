package com.toxictrace.nexusconnect.widget

/** Rust-backed formatting for call durations. */
internal object CallDurationFormatter {
    init {
        System.loadLibrary("nexus_phone")
    }

    private external fun formatNative(seconds: Long): String

    fun format(seconds: Long): String = formatNative(seconds)
}
