package com.toxictrace.nexusconnect.data.repository

import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.toxictrace.nexusconnect.util.NativePhoneNumber

class CallLogRepository(private val context: Context) {

    companion object {
        private const val TAG = "CallLogRepo"
    }

    /**
     * Returns contact IDs ordered by call frequency (most called first).
     * Matches call log numbers against contacts map.
     */
    fun getFrequentContactIds(numberToContactId: Map<String, Long>, limit: Int = 50): List<Long> {
        val matchedContactIds = mutableListOf<Long>()
        val matchedCallTypes = mutableListOf<Int>()
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
                    val type = it.getInt(typeIdx)
                    // Count outgoing + incoming (not missed)
                    if (type == CallLog.Calls.OUTGOING_TYPE || type == CallLog.Calls.INCOMING_TYPE) {
                        val norm = normalizeNum(number)
                        val contactId = numberToContactId[norm] ?: continue
                        matchedContactIds.add(contactId)
                        matchedCallTypes.add(type)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFrequentContactIds: ${e.message}")
        }
        return FrequentContactOrder.order(
            matchedContactIds.toLongArray(),
            matchedCallTypes.toIntArray()
        ).take(limit)
    }

    /**
     * Returns contact IDs ordered by most recent call (latest first).
     */
    fun getRecentContactIds(numberToContactId: Map<String, Long>, limit: Int = 50, days: Int = 0): List<Long> {
        val seen = linkedSetOf<Long>()
        val missedNums = mutableListOf<String>()
        val cutoff = if (days > 0) System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L else 0L
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                if (cutoff > 0) "${CallLog.Calls.DATE} >= ?" else null,
                if (cutoff > 0) arrayOf(cutoff.toString()) else null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()

            cursor.use {
                val numIdx  = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                while (it.moveToNext() && seen.size < limit) {
                    val number = it.getString(numIdx) ?: continue
                    val norm = normalizeNum(number)
                    val contactId = numberToContactId[norm]
                    if (contactId != null) {
                        seen.add(contactId)
                    } else {
                        val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                        if (!name.isNullOrBlank() && missedNums.size < 10) {
                            missedNums.add("$name($number->$norm)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecentContactIds: ${e.message}")
        }
        if (missedNums.isNotEmpty()) {
            Log.d(TAG, "recent unmatched contacts: $missedNums")
            android.util.Log.d("CallLogRepo", "recent unmatched: $missedNums")
            com.toxictrace.nexusconnect.util.AppLogger.i(TAG, "recent unmatched: $missedNums")
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
        // days=0 means unlimited (no time filter)
        val cutoff = if (days > 0)
            System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        else 0L
        val numbers = mutableListOf<String>()
        val norms = mutableListOf<String>()
        val dates = mutableListOf<Long>()
        val types = mutableListOf<Int>()
        val nonBlank = mutableListOf<Boolean>()
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                if (cutoff > 0) "${CallLog.Calls.DATE} >= ?" else null,
                if (cutoff > 0) arrayOf(cutoff.toString()) else null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()
            cursor.use {
                val numIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (it.moveToNext()) {
                    val number = it.getString(numIdx) ?: continue
                    numbers.add(number)
                    norms.add(normalizeNum(number))
                    dates.add(it.getLong(dateIdx))
                    types.add(it.getInt(typeIdx))
                    nonBlank.add(number.isNotBlank())
                }
            }
            UnknownRecentCallFilter.filter(
                numbers,
                norms,
                dates,
                types,
                nonBlank,
                numberToContactId.keys.toList(),
                limit
            ).map { index ->
                Triple(numbers[index], dates[index], types[index])
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUnknownRecentCalls: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns map of normalized_number → last call type for given contacts.
     * Call types: INCOMING=1, OUTGOING=2, MISSED=3, REJECTED=5 (BLOCKED on some)
     */
    fun getLastCallTypes(phoneNumbers: List<String>): Map<String, Int> {
        if (phoneNumbers.isEmpty()) return emptyMap()
        val normSet = phoneNumbers.map { normalizeNum(it) }.toSet()
        val result = mutableMapOf<String, Int>()
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyMap()
            cursor.use {
                val ni = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val ti = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (it.moveToNext() && result.size < normSet.size) {
                    val norm = normalizeNum(it.getString(ni) ?: continue)
                    if (norm in normSet && norm !in result) {
                        result[norm] = it.getInt(ti)
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getLastCallTypes: ${e.message}")
            emptyMap()
        }
    }


    fun getLastCallTypesByNorm(norms: List<String>): Map<String, Int> {
        if (norms.isEmpty()) return emptyMap()
        val normalizedNumbers = mutableListOf<String>()
        val callTypes = mutableListOf<Int>()
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyMap()
            cursor.use {
                val ni = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val ti = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (it.moveToNext()) {
                    val number = it.getString(ni) ?: continue
                    normalizedNumbers.add(normalizeNum(number))
                    callTypes.add(it.getInt(ti))
                }
            }
            val result = linkedMapOf<String, Int>()
            LastCallTypeResolver.resolve(normalizedNumbers, callTypes, norms.distinct()).forEach { index ->
                result[normalizedNumbers[index]] = callTypes[index]
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getLastCallTypesByNorm: ${e.message}")
            emptyMap()
        }
    }


    /**
     * Returns recent calls (known + unknown) sorted by date DESC.
     * Known contacts returned as positive IDs, unknown as negative IDs.
     * This ensures unknown numbers with newer calls appear before older known contacts.
     */
    /**
     * contactDays/unknownDays: 0 = unlimited, -1 = disabled (don't include this type)
     */
    fun getRecentMixed(
        numberToContactId: Map<String, Long>,
        contactDays: Int = 0,
        unknownDays: Int = 0,
        limit: Int = 50
    ): List<Pair<Long, String?>> {
        val now = System.currentTimeMillis()
        val contactCutoff = if (contactDays > 0) now - contactDays * 24 * 60 * 60 * 1000L else 0L
        val unknownCutoff = if (unknownDays > 0) now - unknownDays * 24 * 60 * 60 * 1000L else 0L
        val queryCutoff = when {
            contactDays == -1 && unknownDays == -1 -> return emptyList()
            contactDays == -1 -> unknownCutoff
            unknownDays == -1 -> contactCutoff
            contactDays == 0 || unknownDays == 0 -> 0L
            else -> minOf(contactCutoff, unknownCutoff)
        }
        val norms = mutableListOf<String>()
        val dates = mutableListOf<Long>()
        val contactIds = mutableListOf<Long>()
        val validUnknown = mutableListOf<Int>()
        val numbers = mutableListOf<String>()
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                if (queryCutoff > 0) "${CallLog.Calls.DATE} >= ?" else null,
                if (queryCutoff > 0) arrayOf(queryCutoff.toString()) else null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return emptyList()
            cursor.use {
                val numIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                while (it.moveToNext()) {
                    val number = it.getString(numIdx) ?: continue
                    val norm = normalizeNum(number)
                    norms.add(norm)
                    dates.add(it.getLong(dateIdx))
                    contactIds.add(numberToContactId[norm] ?: Long.MIN_VALUE)
                    numbers.add(number)
                    validUnknown.add(if (number.isNotBlank() && number != "-1" && number != "-2") 1 else 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecentMixed: ${e.message}")
            return emptyList()
        }
        val selectedIndices = RecentMixedCallResolver.resolve(
            norms, dates, contactIds, validUnknown,
            contactCutoff, unknownCutoff,
            contactDays == -1, unknownDays == -1, limit
        )
        var unknownIdx = 1L
        return selectedIndices.mapNotNull { index ->
            if (index !in numbers.indices) return@mapNotNull null
            val contactId = contactIds[index]
            if (contactId != Long.MIN_VALUE) Pair(contactId, null)
            else Pair(-unknownIdx++, numbers[index])
        }
    }

    private fun normalizeNum(number: String): String =
        NativePhoneNumber.normalize(number)
}
