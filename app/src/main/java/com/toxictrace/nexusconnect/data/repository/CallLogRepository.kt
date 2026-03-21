package com.toxictrace.nexusconnect.data.repository

import android.content.Context
import android.provider.CallLog
import android.util.Log

class CallLogRepository(private val context: Context) {

    companion object {
        private const val TAG = "CallLogRepo"
    }

    /**
     * Returns contact IDs ordered by call frequency (most called first).
     * Matches call log numbers against contacts map.
     */
    fun getFrequentContactIds(numberToContactId: Map<String, Long>, limit: Int = 50): List<Long> {
        val freq = mutableMapOf<Long, Int>()
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()

            cursor.use {
                val numIdx  = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (it.moveToNext()) {
                    val number = it.getString(numIdx) ?: continue
                    val type   = it.getInt(typeIdx)
                    // Count outgoing + incoming (not missed)
                    if (type == CallLog.Calls.OUTGOING_TYPE || type == CallLog.Calls.INCOMING_TYPE) {
                        val norm = normalize(number)
                        val contactId = numberToContactId[norm] ?: continue
                        freq[contactId] = (freq[contactId] ?: 0) + 1
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFrequentContactIds: ${e.message}")
        }
        return freq.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /**
     * Returns contact IDs ordered by most recent call (latest first).
     */
    fun getRecentContactIds(numberToContactId: Map<String, Long>, limit: Int = 50): List<Long> {
        val seen = linkedSetOf<Long>()
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()

            cursor.use {
                val numIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                while (it.moveToNext() && seen.size < limit) {
                    val number = it.getString(numIdx) ?: continue
                    val contactId = numberToContactId[normalize(number)] ?: continue
                    seen.add(contactId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecentContactIds: ${e.message}")
        }
        return seen.toList()
    }

    /**
     * Returns recent calls from unknown numbers (not in contacts),
     * within the given number of days. Returns list of (number, date, type).
     */
    fun getUnknownRecentCalls(
        numberToContactId: Map<String, Long>,
        days: Int,
        limit: Int = 20
    ): List<Triple<String, Long, Int>> {
        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Triple<String, Long, Int>>()
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()
            cursor.use {
                val numIdx  = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (it.moveToNext() && result.size < limit) {
                    val number = it.getString(numIdx) ?: continue
                    val norm = normalize(number)
                    // Unknown = not in contacts AND not seen yet
                    if (norm !in numberToContactId && norm !in seen && number.isNotBlank()) {
                        seen.add(norm)
                        result.add(Triple(number, it.getLong(dateIdx), it.getInt(typeIdx)))
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getUnknownRecentCalls: ${e.message}")
            emptyList()
        }
    }
}
