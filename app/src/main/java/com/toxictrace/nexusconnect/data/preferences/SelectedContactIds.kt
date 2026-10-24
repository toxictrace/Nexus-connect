package com.toxictrace.nexusconnect.data.preferences

/**
 * Converts the persisted comma-separated contact IDs to and from an ordered list.
 * Invalid tokens are ignored to preserve the repository's existing recovery behavior.
 */
internal object SelectedContactIds {
    fun parse(raw: String): List<Long> =
        raw.split(",").mapNotNull { it.toLongOrNull() }

    fun serialize(ids: List<Long>): String = ids.joinToString(",")
}
