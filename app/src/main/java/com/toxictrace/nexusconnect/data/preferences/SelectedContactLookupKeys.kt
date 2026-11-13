package com.toxictrace.nexusconnect.data.preferences

import java.util.Base64

/** Encodes opaque Contacts Provider lookup keys for safe comma-separated persistence. */
internal object SelectedContactLookupKeys {
    fun parse(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { token ->
            runCatching { String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8) }
                .getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    fun serialize(keys: List<String>): String =
        keys.filter { it.isNotBlank() }.joinToString(",") {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
        }
}
