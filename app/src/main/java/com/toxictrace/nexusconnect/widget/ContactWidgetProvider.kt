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
        }        fun updateAllWidgets(context: Context) {
            PhotoProvider.invalidateCache()
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, ContactWidgetProvider::class.java)
            )
            Log.d(TAG, "updateAllWidgets: ${ids.size} widgets")
            ids.forEach { buildAndPush(context, mgr, it) }
        }

        fun buildAndPush(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            Log.d(TAG, "buildAndPush id=$widgetId")

            val cols        = WidgetPrefs.getColumns(context).coerceIn(3, 6)
            val rows        = WidgetPrefs.getRows(context).coerceIn(3, 6)
            val selectedIds = WidgetPrefs.getSelectedContactIds(context)
            val maxTiles    = cols * rows
            val filterFavDbg  = WidgetPrefs.getFilterFavorites(context)
            val filterRecDbg  = WidgetPrefs.getFilterRecents(context)
            val filterFreqDbg = WidgetPrefs.getFilterFrequent(context)
            Log.d(TAG, "cols=$cols rows=$rows maxTiles=$maxTiles")
            AppLogger.i(TAG, "buildAndPush: cols=$cols rows=$rows maxTiles=$maxTiles selectedIds=${selectedIds.size} ids=$selectedIds")
            AppLogger.i(TAG, "buildAndPush: filterFavorites=$filterFavDbg filterRecents=$filterRecDbg filterFrequent=$filterFreqDbg")
            AppLogger.i(TAG, "buildAndPush: sharedPrefs_raw filterFrequent=${context.getSharedPreferences("nexus_widget_prefs", android.content.Context.MODE_PRIVATE).getBoolean("filter_frequent", false)}")

            val allContacts = try {
                ContactsRepository(context).loadContacts()
            } catch (e: Exception) {
                Log.e(TAG, "loadContacts: ${e.message}")
                emptyList()
            }

            val contacts = buildWidgetContacts(
                context      = context,
                allContacts  = allContacts,
                selectedIds  = selectedIds,
                settings     = WidgetPrefs.run {
                    Triple(
                        getFilterFavorites(context),
                        getFilterFrequent(context),
                        getFilterRecents(context)
                    )
                },
                maxTiles     = maxTiles
            )
            Log.d(TAG, "contacts=${contacts.size}")

            // Fill remaining tiles with unknown numbers from call log
            // Build numberMap using ALL phone numbers for each contact
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
                    val idIdx  = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val numIdx = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val id  = it.getLong(idIdx)
                        val num = it.getString(numIdx) ?: continue
                        val norm = num.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                        if (norm.isNotBlank()) numberMap[norm] = id
                    }
                }
            } catch (e: Exception) {
                // fallback to single-number map
                allContacts.filter { it.phoneNumber != null }.forEach {
                    val norm = it.phoneNumber!!.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                    numberMap[norm] = it.id
                }
            }
            val unknowns = buildUnknownContacts(context, numberMap, maxTiles - contacts.size)
            val allTileContacts = (contacts + unknowns).take(maxTiles)
            Log.d(TAG, "total tiles=${allTileContacts.size} (${contacts.size} known + ${unknowns.size} unknown)")

            val layoutRes = context.resources.getIdentifier(
                "widget_grid_${cols}c${rows}r", "layout", context.packageName
            ).takeIf { it != 0 } ?: run {
                Log.e(TAG, "Layout not found for ${cols}c${rows}r, using 4c3r")
                context.resources.getIdentifier("widget_grid_4c3r", "layout", context.packageName)
            }
            // Load last call types for all contacts if icon display is enabled
            val showCallIcon = WidgetPrefs.getShowCallTypeIcon(context)
            val callIconStyle = WidgetPrefs.getCallIconStyle(context) // NONE, MATERIAL, GLASS
            // Use all known norms from numberMap for accurate matching
            val tileContactIds = allTileContacts.map { it.id }.toSet()
            val tileNorms = numberMap.entries
                .filter { it.value in tileContactIds }
                .map { it.key }
            val callTypeMap: Map<String, Int> = if (showCallIcon) {
                com.toxictrace.nexusconnect.data.repository.CallLogRepository(context)
                    .getLastCallTypesByNorm(tileNorms)
            } else emptyMap()

            val views = RemoteViews(context.packageName, layoutRes)
            val pkg = context.packageName

            for (idx in 0 until maxTiles) {
                val tileId     = context.resources.getIdentifier("tile_${cols}r${rows}_$idx",      "id", pkg)
                val photoId    = context.resources.getIdentifier("photo_${cols}r${rows}_$idx",     "id", pkg)
                val nameId     = context.resources.getIdentifier("name_${cols}r${rows}_$idx",      "id", pkg)
                val callIconId = context.resources.getIdentifier("call_icon_${cols}r${rows}_$idx", "id", pkg)

                val contact = allTileContacts.getOrNull(idx)
                if (contact != null) {
                    views.setViewVisibility(tileId, View.VISIBLE)

                    if (contact.photoUri != null) {
                        views.setImageViewUri(photoId, PhotoProvider.uriForContact(contact.id))
                    } else {
                        val avatarIdentity = WidgetPrefs.getAvatarIdentity(context)
                        val avatarUri = if (avatarIdentity == "CUSTOM" &&
                            WidgetPrefs.getCustomAvatarUri(context).isNotBlank())
                            AvatarProvider.customUri()
                        else
                            AvatarProvider.defaultUri()
                        views.setImageViewUri(photoId, avatarUri)
                    }

                    views.setTextViewText(nameId, contact.name)

                    // Call type icon
                    if (showCallIcon && callIconStyle != "NONE" && callIconId != 0 && contact.phoneNumber != null) {
                        val norm = contact.phoneNumber.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                        val callType = callTypeMap[norm]
                        val glass = callIconStyle == "GLASS"
                        val iconRes = when (callType) {
                            android.provider.CallLog.Calls.INCOMING_TYPE ->
                                if (glass) R.drawable.call_incoming_glass else R.drawable.call_incoming
                            android.provider.CallLog.Calls.OUTGOING_TYPE ->
                                if (glass) R.drawable.call_outgoing_glass else R.drawable.call_outgoing
                            android.provider.CallLog.Calls.MISSED_TYPE ->
                                if (glass) R.drawable.call_missed_glass else R.drawable.call_missed
                            5 -> if (glass) R.drawable.call_rejected_glass else R.drawable.call_rejected
                            else -> if (callType != null)
                                if (glass) R.drawable.call_unknown_glass else R.drawable.call_unknown
                            else 0
                        }
                        if (iconRes != 0) {
                            views.setViewVisibility(callIconId, View.VISIBLE)
                            views.setImageViewResource(callIconId, iconRes)
                        } else {
                            views.setViewVisibility(callIconId, View.GONE)
                        }
                    } else if (callIconId != 0) {
                        views.setViewVisibility(callIconId, View.GONE)
                    }

                    val intent = Intent(context, ContactWidgetProvider::class.java).apply {
                        action = ACTION_CONTACT_CLICK
                        putExtra(EXTRA_CONTACT_ID,    contact.id)
                        putExtra(EXTRA_CONTACT_PHONE, contact.phoneNumber ?: "")
                        putExtra(EXTRA_CONTACT_NAME,  contact.name)
                        data = Uri.parse("nexus://contact/${contact.id}")
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, contact.id.toInt(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(tileId, pi)
                } else {
                    views.setViewVisibility(tileId, View.INVISIBLE)
                }
            }

            // Call log button
            val showCallLogBtn = WidgetPrefs.getShowCallLogButton(context)
            val callLogBtnId = context.resources.getIdentifier("btn_call_log", "id", context.packageName)
            if (callLogBtnId != 0) {
                if (showCallLogBtn) {
                    views.setViewVisibility(callLogBtnId, android.view.View.VISIBLE)
                    val callLogIntent = Intent(context, ContactWidgetProvider::class.java).apply {
                        action = ACTION_OPEN_CALL_LOG
                    }
                    val callLogPi = PendingIntent.getBroadcast(
                        context, 9999, callLogIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(callLogBtnId, callLogPi)
                } else {
                    views.setViewVisibility(callLogBtnId, android.view.View.GONE)
                }
            }

            try {
                mgr.updateAppWidget(widgetId, views)
                Log.d(TAG, "buildAndPush done")
            } catch (e: Exception) {
                Log.e(TAG, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        private fun buildWidgetContacts(
            context: Context,
            allContacts: List<Contact>,
            selectedIds: List<Long>,
            settings: Triple<Boolean, Boolean, Boolean>, // favorites, frequent, recents
            maxTiles: Int
        ): List<Contact> {
            val (filterFavorites, filterFrequent, filterRecents) = settings
            val result = mutableListOf<Contact>()
            val usedIds = mutableSetOf<Long>()

            // 1. Favorites (selected by user) — highest priority
            if (filterFavorites && selectedIds.isNotEmpty()) {
                selectedIds
                    .mapNotNull { id -> allContacts.firstOrNull { it.id == id } }
                    .forEach { c -> if (usedIds.add(c.id)) result.add(c) }
            }

            if (result.size >= maxTiles) return result.take(maxTiles)

            // Build number→id map for call log lookups
            // Build numberMap using ALL phone numbers for each contact
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
                    val idIdx  = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val numIdx = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val id  = it.getLong(idIdx)
                        val num = it.getString(numIdx) ?: continue
                        val norm = num.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                        if (norm.isNotBlank()) numberMap[norm] = id
                    }
                }
            } catch (e: Exception) {
                // fallback to single-number map
                allContacts.filter { it.phoneNumber != null }.forEach {
                    val norm = it.phoneNumber!!.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
                    numberMap[norm] = it.id
                }
            }
            val callLogRepo = com.toxictrace.nexusconnect.data.repository.CallLogRepository(context)

            // 2. Recents — higher priority than frequent
            if (filterRecents) {
                val recentsDays = WidgetPrefs.getRecentsDays(context)
                val recentIds = callLogRepo.getRecentContactIds(numberMap, 50, recentsDays)
                val recentNames = recentIds.mapNotNull { id ->
                    allContacts.firstOrNull { it.id == id }?.let { "$id(${it.name})" }
                }
                AppLogger.i(TAG, "recentIds: ${recentIds.size} contacts=$recentNames")
                recentIds.forEach { id ->
                    val c = allContacts.firstOrNull { it.id == id } ?: return@forEach
                    if (usedIds.add(c.id)) {
                        result.add(c)
                        AppLogger.i(TAG, "recent added: ${c.name} id=${c.id}")
                    }
                    if (result.size >= maxTiles) return result.take(maxTiles)
                }
            }
            // 3. Frequent — lowest priority
            if (filterFrequent) {
                val frequentIds = callLogRepo.getFrequentContactIds(numberMap, 50)
                AppLogger.i(TAG, "frequentIds: ${frequentIds.size} ids=$frequentIds")
                frequentIds.forEach { id ->
                    val c = allContacts.firstOrNull { it.id == id } ?: return@forEach
                    if (usedIds.add(c.id)) result.add(c)
                    if (result.size >= maxTiles) return result.take(maxTiles)
                }
                AppLogger.i(TAG, "after frequent: result.size=${result.size} maxTiles=$maxTiles")
            }


            return result.take(maxTiles)
        }

        private fun buildUnknownContacts(
            context: Context,
            numberMap: Map<String, Long>,
            maxCount: Int
        ): List<Contact> {
            if (!WidgetPrefs.getShowUnknownNumbers(context)) return emptyList()
            val days = WidgetPrefs.getUnknownNumbersDays(context) // 0 = unlimited
            val callLogRepo = com.toxictrace.nexusconnect.data.repository.CallLogRepository(context)
            return callLogRepo.getUnknownRecentCalls(numberMap, days, maxCount)
                .mapIndexed { idx, (number, _, type) ->
                    val label = when {
                        number.isBlank() || number == "-1" -> "Unknown"
                        number == "-2" -> "Private"
                        else -> number
                    }
                    Contact(
                        id          = -(idx + 1L), // negative ID = unknown
                        name        = label,
                        phoneNumber = number.takeIf { it.isNotBlank() && it != "-1" && it != "-2" }
                    )
                }
        }

        private fun makeDefaultAvatar(context: Context, contact: Contact, size: Int): Bitmap {
            val avatarIdentity = WidgetPrefs.getAvatarIdentity(context)
            val customUri      = WidgetPrefs.getCustomAvatarUri(context)

            // Custom image — centerCrop to fill tile without distortion
            if (avatarIdentity == "CUSTOM" && customUri.isNotBlank()) {
                try {
                    val uri = android.net.Uri.parse(customUri)
                    // Decode bounds first
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, boundsOpts)
                    }
                    val sampleSize = maxOf(1,
                        minOf(boundsOpts.outWidth, boundsOpts.outHeight) / size)
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val src = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    if (src != null) {
                        // CenterCrop: scale so shorter side = size, then crop center
                        val scale = size.toFloat() / minOf(src.width, src.height)
                        val scaledW = (src.width * scale).toInt()
                        val scaledH = (src.height * scale).toInt()
                        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
                        val x = (scaledW - size) / 2
                        val y = (scaledH - size) / 2
                        return Bitmap.createBitmap(scaled, x, y, size, size)
                    }
                } catch (_: Exception) {}
            }

            // Default: dark gradient background + programmatic silhouette (no PNG needed)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val f = size.toFloat()

            // Background gradient: dark grey
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            bgPaint.shader = android.graphics.RadialGradient(
                f * 0.5f, f * 0.4f, f * 0.7f,
                intArrayOf(0xFF555555.toInt(), 0xFF1A1A1A.toInt()),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, f, f, bgPaint)

            // Silhouette: head + shoulders in white
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFDDDDDD.toInt()
                style = Paint.Style.FILL
            }
            // Head
            val headR = f * 0.20f
            val headCX = f * 0.50f
            val headCY = f * 0.36f
            canvas.drawCircle(headCX, headCY, headR, sp)

            // Shoulders/body — trapezoid
            val path = android.graphics.Path()
            path.moveTo(f * 0.10f, f * 1.05f)         // bottom-left (off screen)
            path.lineTo(f * 0.20f, f * 0.62f)          // left shoulder
            path.cubicTo(
                f * 0.28f, f * 0.56f,
                f * 0.38f, f * 0.53f,
                headCX, f * 0.53f
            )
            path.cubicTo(
                f * 0.62f, f * 0.53f,
                f * 0.72f, f * 0.56f,
                f * 0.80f, f * 0.62f
            )
            path.lineTo(f * 0.90f, f * 1.05f)
            path.close()
            canvas.drawPath(path, sp)

            return bmp
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
            val id    = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
            val phone = intent.getStringExtra(EXTRA_CONTACT_PHONE)
            val name  = intent.getStringExtra(EXTRA_CONTACT_NAME)
            Log.d(TAG, "Click: $name id=$id")
            ContactActionHandler.handle(context, id, phone, name)
        }
    }
}
