package com.toxictrace.nexusconnect.viewmodel

internal object SelectionLimit {
    fun trim(ids: List<Long>, capacity: Int): List<Long> =
        if (ids.size <= capacity) ids else ids.take(capacity.coerceAtLeast(0))
}
