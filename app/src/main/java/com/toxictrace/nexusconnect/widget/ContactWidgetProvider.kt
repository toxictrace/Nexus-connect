package com.toxictrace.nexusconnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs
import com.toxictrace.nexusconnect.util.AppLogger
import com.toxictrace.nexusconnect.data.repository.ContactsRepository

class ContactWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "NexusWidget"
        const val ACTION_CONTACT_CLICK  = "com.toxictrace.nexusconnect.ACTION_CONTACT_CLICK"
        const val ACTION_OPEN_CALL_LOG  = "com.toxictrace.nexusconnect.ACTION_OPEN_CALL_LOG"
        const val EXTRA_CONTACT_ID    = "extra_contact_id"
        const val EXTRA_CONTACT_PHONE = "extra_contact_phone"
        const val EXTRA_CONTACT_NAME  = "extra_contact_name"

        // IPC limit ~900KB total. RGB_565 = 2 bytes/pixel (vs ARGB_8888 = 4)
        private const val MAX_TOTAL_BYTES = 900_000

        private fun bitmapSize(cols: Int, rows: Int): Int {
            val tiles = cols * rows
            val bytesPerTile = MAX_TOTAL_BYTES / tiles
            // RGB_565: 2 bytes per pixel
            val size = Math.sqrt(bytesPerTile / 2.0).toInt()
            return size.coerceIn(100, 500)
        }

        fun updateAllWidgets(context: Context) {
            PhotoProvider.invalidateCache()
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, ContactWidgetProvider::class.java)
            )
            Log.d(TAG, "updateAllWidgets: ${ids.size} widgets")
            ids.forEach { buildAndPush(context, mgr, it) }
        }

        /**
         * Main entry point - now much cleaner
         */
        fun buildAndPush(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            Log.d(TAG, "buildAndPush id=$widgetId")

            val cols = WidgetPrefs.getColumns(context).coerceIn(3, 6)
            val rows = WidgetPrefs.getRows(context).coerceIn(3, 6)
            val maxTiles = cols * rows

            AppLogger.i(TAG, "buildAndPush: cols=$cols rows=$rows maxTiles=$maxTiles")

            val allContacts = loadAllContacts(context)
            val selectedIds = WidgetPrefs.getSelectedContactIds(context)

            val widgetContacts = buildWidgetContacts(
                context = context,
                allContacts = allContacts,
                selectedIds = selectedIds,
                maxTiles = maxTiles
            )

            val allTileContacts = fillWithFrequentContacts(context, widgetContacts, allContacts, maxTiles)

            val views = buildRemoteViews(context, cols, rows, allTileContacts, allContacts)

            try {
                mgr.updateAppWidget(widgetId, views)
                Log.d(TAG, "buildAndPush done for widget $widgetId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget $widgetId", e)
            }
        }

        private fun loadAllContacts(context: Context): List<Contact> {
            return try {
                ContactsRepository(context).loadContacts()
            } catch (e: Exception) {
                Log.e(TAG, "loadContacts failed", e)
                emptyList()
            }
        }

        private fun fillWithFrequentContacts(
            context: Context,
            baseContacts: List<Contact>,
            allContacts: List<Contact>,
            maxTiles: Int
        ): List<Contact> {
            val remaining = maxTiles - baseContacts.size
            if (remaining <= 0 || !WidgetPrefs.getFilterFrequent(context)) return baseContacts

            val frequentContacts = mutableListOf<Contact>()
            val usedIds = baseContacts.map { it.id }.toMutableSet()

            val callLogRepo = com.toxictrace.nexusconnect.data.repository.CallLogRepository(context)
            val numberMap = buildNumberToIdMap(context, allContacts)

            callLogRepo.getFrequentContactIds(numberMap, 50).forEach { id ->
                if (frequentContacts.size >= remaining) return@forEach
                val contact = allContacts.firstOrNull { it.id == id } ?: return@forEach
                if (usedIds.add(contact.id)) {
                    frequentContacts.add(contact)
                }
            }

            return (baseContacts + frequentContacts).take(maxTiles)
        }

        private fun buildRemoteViews(
            context: Context,
            cols: Int,
            rows: Int,
            tileContacts: List<Contact>,
            allContacts: List<Contact>
        ): RemoteViews {
            val layoutRes = getWidgetLayoutRes(context, cols, rows)
            val views = RemoteViews(context.packageName, layoutRes)
            val pkg = context.packageName
            val maxTiles = cols * rows

            val showCallIcon = WidgetPrefs.getShowCallTypeIcon(context)
            val callIconStyle = WidgetPrefs.getCallIconStyle(context)
            val numberMap = buildNumberToIdMap(context, allContacts)
            val tileNorms = extractTileNorms(tileContacts, numberMap)
            val callTypeMap = if (showCallIcon) {
                com.toxictrace.nexusconnect.data.repository.CallLogRepository(context)
                    .getLastCallTypesByNorm(tileNorms)
            } else emptyMap()

            for (idx in 0 until maxTiles) {
                setupTile(views, context, cols, rows, idx, tileContacts.getOrNull(idx), callTypeMap, numberMap)
            }

            setupCallLogButton(views, context)

            return views
        }

        private fun getWidgetLayoutRes(context: Context, cols: Int, rows: Int): Int {
            val layoutName = "widget_grid_${cols}c${rows}r"
            val resId = context.resources.getIdentifier(layoutName, "layout", context.packageName)
            return if (resId != 0) resId else {
                Log.e(TAG, "Layout $layoutName not found, fallback to 4c3r")
                context.resources.getIdentifier("widget_grid_4c3r", "layout", context.packageName)
            }
        }

        private fun setupTile(
            views: RemoteViews,
            context: Context,
            cols: Int,
            rows: Int,
            idx: Int,
            contact: Contact?,
            callTypeMap: Map<String, Int>,
            numberMap: Map<String, Long>
        ) {
            val tileId = context.resources.getIdentifier("tile_${cols}r${rows}_$idx", "id", context.packageName)
            val photoId = context.resources.getIdentifier("photo_${cols}r${rows}_$idx", "id", context.packageName)
            val nameId = context.resources.getIdentifier("name_${cols}r${rows}_$idx", "id", context.packageName)
            val callIconId = context.resources.getIdentifier("call_icon_${cols}r${rows}_$idx", "id", context.packageName)

            if (contact == null) {
                views.setViewVisibility(tileId, View.INVISIBLE)
                return
            }

            views.setViewVisibility(tileId, View.VISIBLE)

            // Photo
            if (contact.photoUri != null) {
                views.setImageViewUri(photoId, PhotoProvider.uriForContact(contact.id))
            } else {
                val avatarUri = getAvatarUri(context)
                views.setImageViewUri(photoId, avatarUri)
            }

            views.setTextViewText(nameId, contact.name)

            // Call icon
            setupCallIcon(views, context, contact, callIconId, callTypeMap, numberMap)

            // Click handler
            setupClickHandler(views, context, tileId, contact)
        }

        private fun getAvatarUri(context: Context): Uri {
            val avatarIdentity = WidgetPrefs.getAvatarIdentity(context)
            return if (avatarIdentity == "CUSTOM" && WidgetPrefs.getCustomAvatarUri(context).isNotBlank()) {
                AvatarProvider.customUri()
            } else {
                AvatarProvider.defaultUri()
            }
        }

        private fun setupCallIcon(
            views: RemoteViews,
            context: Context,
            contact: Contact,
            callIconId: Int,
            callTypeMap: Map<String, Int>,
            numberMap: Map<String, Long>
        ) {
            if (callIconId == 0 || !WidgetPrefs.getShowCallTypeIcon(context)) {
                views.setViewVisibility(callIconId, View.GONE)
                return
            }

            val callIconStyle = WidgetPrefs.getCallIconStyle(context)
            val glass = callIconStyle == "GLASS"

            val callType = getCallTypeForContact(contact, callTypeMap, numberMap)

            val iconRes = when (callType) {
                android.provider.CallLog.Calls.INCOMING_TYPE -> if (glass) R.drawable.call_incoming_glass else R.drawable.call_incoming
                android.provider.CallLog.Calls.OUTGOING_TYPE -> if (glass) R.drawable.call_outgoing_glass else R.drawable.call_outgoing
                android.provider.CallLog.Calls.MISSED_TYPE -> if (glass) R.drawable.call_missed_glass else R.drawable.call_missed
                5 -> if (glass) R.drawable.call_rejected_glass else R.drawable.call_rejected
                else -> if (callType != null) if (glass) R.drawable.call_unknown_glass else R.drawable.call_unknown else 0
            }

            if (iconRes != 0) {
                views.setViewVisibility(callIconId, View.VISIBLE)
                views.setImageViewResource(callIconId, iconRes)
            } else {
                views.setViewVisibility(callIconId, View.GONE)
            }
        }

        private fun getCallTypeForContact(
            contact: Contact,
            callTypeMap: Map<String, Int>,
            numberMap: Map<String, Long>
        ): Int? {
            return if (contact.id < 0 && contact.phoneNumber != null) {
                val norm = contact.phoneNumber.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                callTypeMap[norm]
            } else {
                val contactNorms = numberMap.entries.filter { it.value == contact.id }.map { it.key }
                contactNorms.firstNotNullOfOrNull { callTypeMap[it] }
            }
        }

        private fun setupClickHandler(views: RemoteViews, context: Context, tileId: Int, contact: Contact) {
            val intent = Intent(context, ContactWidgetProvider::class.java).apply {
                action = ACTION_CONTACT_CLICK
                putExtra(EXTRA_CONTACT_ID, contact.id)
                putExtra(EXTRA_CONTACT_PHONE, contact.phoneNumber ?: "")
                putExtra(EXTRA_CONTACT_NAME, contact.name)
                data = Uri.parse("nexus://contact/${contact.id}")
            }
            val pi = PendingIntent.getBroadcast(
                context, contact.id.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(tileId, pi)
        }

        private fun setupCallLogButton(views: RemoteViews, context: Context) {
            val showCallLogBtn = WidgetPrefs.getShowCallLogButton(context)
            val callLogBtnId = context.resources.getIdentifier("btn_call_log", "id", context.packageName)

            if (callLogBtnId != 0) {
                if (showCallLogBtn) {
                    views.setViewVisibility(callLogBtnId, View.VISIBLE)
                    val callLogIntent = Intent(context, ContactWidgetProvider::class.java).apply {
                        action = ACTION_OPEN_CALL_LOG
                    }
                    val callLogPi = PendingIntent.getBroadcast(
                        context, 9999, callLogIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(callLogBtnId, callLogPi)
                } else {
                    views.setViewVisibility(callLogBtnId, View.GONE)
                }
            }
        }

        private fun buildNumberToIdMap(context: Context, allContacts: List<Contact>): Map<String, Long> {
            val numberMap = mutableMapOf<String, Long>()
            try {
                val cursor = context.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )
                cursor?.use {
                    val idIdx = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val numIdx = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val id = it.getLong(idIdx)
                        val num = it.getString(numIdx) ?: continue
                        val norm = num.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                        if (norm.isNotBlank()) numberMap[norm] = id
                    }
                }
            } catch (e: Exception) {
                allContacts.filter { it.phoneNumber != null }.forEach {
                    val norm = it.phoneNumber!!.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                    numberMap[norm] = it.id
                }
            }
            return numberMap
        }

        private fun extractTileNorms(tileContacts: List<Contact>, numberMap: Map<String, Long>): List<String> {
            val norms = mutableListOf<String>()
            val tileContactIds = tileContacts.map { it.id }.toSet()

            numberMap.entries
                .filter { it.value in tileContactIds }
                .map { it.key }
                .forEach { norms.add(it) }

            tileContacts.filter { it.id < 0 && it.phoneNumber != null }.forEach {
                val norm = it.phoneNumber!!.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                if (norm.isNotBlank()) norms.add(norm)
            }
            return norms
        }

        // Keep the original buildWidgetContacts for now (can be refactored later)
        private fun buildWidgetContacts(
            context: Context,
            allContacts: List<Contact>,
            selectedIds: List<Long>,
            maxTiles: Int
        ): List<Contact> {
            // ... (original implementation remains the same for now)
            val (filterFavorites, filterFrequent, filterRecents) = WidgetPrefs.run {
                Triple(
                    getFilterFavorites(context),
                    getFilterFrequent(context),
                    getFilterRecents(context)
                )
            }
            // [Original logic from previous version - kept for compatibility]
            val result = mutableListOf<Contact>()
            val usedIds = mutableSetOf<Long>()

            if (filterFavorites && selectedIds.isNotEmpty()) {
                selectedIds.mapNotNull { id -> allContacts.firstOrNull { it.id == id } }
                    .forEach { c -> if (usedIds.add(c.id)) result.add(c) }
            }

            if (result.size >= maxTiles) return result.take(maxTiles)

            val numberMap = buildNumberToIdMap(context, allContacts)
            val callLogRepo = com.toxictrace.nexusconnect.data.repository.CallLogRepository(context)

            if (filterRecents || WidgetPrefs.getShowUnknownNumbers(context)) {
                val recentsDays = WidgetPrefs.getRecentsDays(context)
                val unknownDays = WidgetPrefs.getUnknownNumbersDays(context)
                val mixed = callLogRepo.getRecentMixed(
                    numberMap,
                    contactDays = if (filterRecents) recentsDays else -1,
                    unknownDays = if (WidgetPrefs.getShowUnknownNumbers(context)) unknownDays else -1,
                    limit = maxTiles
                )
                var unknownCount = 1
                mixed.forEach { (id, phone) ->
                    if (result.size >= maxTiles) return result.take(maxTiles)
                    if (id > 0) {
                        if (!filterRecents) return@forEach
                        val c = allContacts.firstOrNull { it.id == id } ?: return@forEach
                        if (usedIds.add(c.id)) result.add(c)
                    } else {
                        if (!WidgetPrefs.getShowUnknownNumbers(context)) return@forEach
                        if (phone.isNullOrBlank()) return@forEach
                        val unknownContact = Contact(
                            id = -(unknownCount++).toLong(),
                            name = phone,
                            phoneNumber = phone
                        )
                        result.add(unknownContact)
                    }
                }
            }
            return result.take(maxTiles)
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Log.d(TAG, "onUpdate ${ids.toList()}")
        ids.forEach { buildAndPush(context, mgr, it) }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled")
        ContactsObserverService.start(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled")
        ContactsObserverService.stop(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive action=${intent.action}")
        if (intent.action == ACTION_OPEN_CALL_LOG) {
            AppLogger.i(TAG, "open call log")
            val preferredPkg = WidgetPrefs.getCallLogAppPackage(context)
            if (preferredPkg.isNotBlank()) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(preferredPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    AppLogger.i(TAG, "open call log: launching preferred package $preferredPkg directly")
                    context.startActivity(launchIntent)
                    return
                } else {
                    AppLogger.w(TAG, "open call log: preferred package $preferredPkg not launchable, falling back")
                }
            }
            val i = Intent(Intent.ACTION_VIEW).apply {
                type = "vnd.android.cursor.dir/calls"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (i.resolveActivity(context.packageManager) != null) {
                context.startActivity(i)
            } else {
                val fallback = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            }
        }
        if (intent.action == ACTION_CONTACT_CLICK) {
            val id = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
            val phone = intent.getStringExtra(EXTRA_CONTACT_PHONE)
            val name = intent.getStringExtra(EXTRA_CONTACT_NAME)
            Log.d(TAG, "Click: $name id=$id")
            ContactActionHandler.handle(context, id, phone, name)
        }
    }
}
