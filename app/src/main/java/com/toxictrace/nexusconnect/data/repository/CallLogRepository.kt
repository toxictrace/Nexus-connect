package com.toxictrace.nexusconnect.data.repository

import android.content.Context
import android.provider.CallLog
import android.util.Log

data class CallRecord(
    val contactId: Long,       // matched contact ID (-1 if unknown)
    val number: String,
    val name: String?,
    val date: Long,            // timestamp ms
    val duration: Long,        // seconds
    val type: Int,             // INCOMING=1, OUTGOING=2, MISSED=3
    val cachedName: String?    // system cached name
)

class CallLogRepository(private val context: Context) {

    companion object {
        private const val TAG = "CallLogRepo"
    }

    /**
     * Returns recent calls, sorted by date desc.
     * Requires READ_CALL_LOG permission.
     */
    fun getRecentCalls(limit: Int = 200): List<CallRecord> {
        val records = mutableListOf<CallRecord>()
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_LOOKUP_URI
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()

            cursor.use {
                val numIdx  = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durIdx  = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val number = it.getString(numIdx) ?: continue
                    records.add(CallRecord(
                        contactId  = -1L,
                        number     = number,
                        name       = null,
                        date       = it.getLong(dateIdx),
                        duration   = it.getLong(durIdx),
                        type       = it.getInt(typeIdx),
                        cachedName = it.getString(nameIdx)
                    ))
                    count++
                }
            }
            records
        } catch (e: Exception) {
            Log.e(TAG, "getRecentCalls: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns set of phone numbers that appear in call log recently.
     * Used to filter contacts by recency.
     */
    fun getRecentNumbers(limit: Int = 100): Set<String> {
        return getRecentCalls(limit)
            .map { normalizeNumber(it.number) }
            .toSet()
    }

    /**
     * Returns ordered list of contact IDs sorted by most recent call.
     * Matches call log numbers against provided contacts.
     */
    fun getRecentContactIds(
        contacts: List<android.net.Uri?>,
        numberToContactId: Map<String, Long>,
        limit: Int = 50
    ): List<Long> {
        val seen = linkedSetOf<Long>()
        getRecentCalls(300).forEach { record ->
            val normalized = normalizeNumber(record.number)
            val contactId = numberToContactId[normalized]
            if (contactId != null && contactId !in seen) {
                seen.add(contactId)
                if (seen.size >= limit) return@forEach
            }
        }
        return seen.toList()
    }

    private fun normalizeNumber(number: String): String =
        number.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
}
