package com.toxictrace.nexusconnect.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.toxictrace.nexusconnect.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs
import com.toxictrace.nexusconnect.ui.theme.NexusConnectTheme
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class CallStats(
    val lastCallDate: Long?,
    val totalCalls: Int,
    val totalDurationSec: Long,
    val lastCallType: Int
)

class ContactChooserActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val language = com.toxictrace.nexusconnect.util.LocaleHelper.getSavedLanguage(newBase)
        super.attachBaseContext(com.toxictrace.nexusconnect.util.LocaleHelper.applyLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactId = intent.getLongExtra("contact_id", -1L)
        val phone     = intent.getStringExtra("contact_phone") ?: ""
        val name      = intent.getStringExtra("contact_name")  ?: ""

        // Check if contact actually has a photo
        val hasPhoto = contactId > 0 && run {
            try {
                val cursor = contentResolver.query(
                    android.provider.ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.Contacts.PHOTO_URI),
                    "${android.provider.ContactsContract.Contacts._ID} = ?",
                    arrayOf(contactId.toString()), null
                )
                cursor?.use { it.moveToFirst() && !it.getString(0).isNullOrBlank() } == true
            } catch (_: Exception) { false }
        }
        val photoUri = if (hasPhoto) PhotoProvider.uriForContact(contactId).toString() else null

        val avatarIdentity = WidgetPrefs.getAvatarIdentity(this)
        val customUri = WidgetPrefs.getCustomAvatarUri(this)
        val stats     = loadCallStats(phone, contactId)

        val isDark = when (WidgetPrefs.getTheme(this)) {
            "DARK"   -> true
            "SYSTEM" -> resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            else     -> false
        }
        val dynamicColor = WidgetPrefs.getDynamicColors(this)
        val accentIndex  = WidgetPrefs.getAccentColorIndex(this)

        // Read configured messenger packages from prefs
        val whatsAppPkg  = WidgetPrefs.getMessengerWhatsApp(this)
        val viberPkg     = WidgetPrefs.getMessengerViber(this)
        val telegramPkg  = WidgetPrefs.getMessengerTelegram(this)

        val hapticEnabled = WidgetPrefs.getHapticFeedback(this)
        val actContext = this

        setContent {
            NexusConnectTheme(
                darkTheme    = isDark,
                dynamicColor = dynamicColor,
                accentIndex  = accentIndex
            ) {
                ChooserSheet(
                    name             = name,
                    phone            = phone,
                    photoUri         = photoUri,
                    avatarIdentity   = avatarIdentity,
                    customUri        = customUri,
                    stats            = stats,
                    whatsAppPkg  = whatsAppPkg,
                    viberPkg     = viberPkg,
                    telegramPkg  = telegramPkg,
                    isInstalled  = ::isInstalled,
                    haptic       = hapticEnabled,
                    ctx          = actContext,
                    onDial       = { directCall(phone) },
                    onWhatsApp   = { openWithPackage(whatsAppPkg, buildWhatsAppUri(phone)) },
                    onViber      = { openViber(phone, viberPkg) },
                    onTelegram   = { openWithPackage(telegramPkg, buildTelegramUri(phone)) },
                    onDismiss    = { finish() }
                )
            }
        }

        // Vibrate after setContent so window.decorView is ready
        if (hapticEnabled) {
            window.decorView.post {
                try {
                    val v = window.decorView
                    v.isHapticFeedbackEnabled = true
                    // LONG_PRESS gives stronger feedback than VIRTUAL_KEY
                    v.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    )
                } catch (_: Exception) {}
            }
        }
    }

    private fun loadCallStats(phone: String, contactId: Long): CallStats {
        if (phone.isBlank() && contactId <= 0) return CallStats(null, 0, 0L, 0)
        val norms = mutableSetOf<String>()
        if (phone.isNotBlank()) {
            norms.add(phone.replace(Regex("[\\s\\-().+]"), "").takeLast(7))
        }
        if (contactId > 0) {
            try {
                val cur = contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()), null
                )
                cur?.use {
                    val ni = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val n = it.getString(ni) ?: continue
                        norms.add(n.replace(Regex("[\\s\\-().+]"), "").takeLast(7))
                    }
                }
            } catch (_: Exception) {}
        }
        if (norms.isEmpty()) return CallStats(null, 0, 0L, 0)
        return try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE,
                    CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                null, null, "${CallLog.Calls.DATE} DESC"
            ) ?: return CallStats(null, 0, 0L, 0)
            var lastDate: Long? = null; var lastType = 0
            var total = 0; var dur = 0L
            cursor.use {
                val ni = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val di = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val ui = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val ti = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (it.moveToNext()) {
                    val n = it.getString(ni)?.replace(Regex("[\\s\\-().+]"), "")?.takeLast(7) ?: continue
                    if (n in norms) {
                        if (lastDate == null) { lastDate = it.getLong(di); lastType = it.getInt(ti) }
                        total++; dur += it.getLong(ui)
                    }
                }
            }
            CallStats(lastDate, total, dur, lastType)
        } catch (e: Exception) { CallStats(null, 0, 0L, 0) }
    }
    private fun isInstalled(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        return runCatching { packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    private fun directCall(phone: String) {
        val hasPermission = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:$phone")))
        finish()
    }

    private fun openWithPackage(pkg: String, uri: Uri) {
        val i = Intent(Intent.ACTION_VIEW, uri).apply {
            if (pkg.isNotBlank()) setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (i.resolveActivity(packageManager) != null) startActivity(i)
        finish()
    }

    private fun openViber(phone: String, pkg: String) {
        val effectivePkg = pkg.ifBlank { "com.viber.voip" }
        val c = phone.replace(Regex("[^+\\d]"), "").trimStart('+')
        // viber://chat?number=XXXXXXXXXXX (without +, with country code)
        val chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$c")).apply {
            setPackage(effectivePkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (chatIntent.resolveActivity(packageManager) != null) {
            startActivity(chatIntent)
        } else {
            // Fallback without package restriction
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$c"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (fallback.resolveActivity(packageManager) != null) startActivity(fallback)
        }
        finish()
    }

    private fun buildWhatsAppUri(phone: String): Uri {
        val c = phone.replace(Regex("[^+\\d]"), "")
        return Uri.parse("whatsapp://send?phone=$c")
    }

    private fun buildTelegramUri(phone: String): Uri {
        val c = phone.replace(Regex("[^+\\d]"), "")
        return Uri.parse("tg://resolve?phone=$c")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserSheet(
    name: String, phone: String, photoUri: String?,
    avatarIdentity: String, customUri: String,
    stats: CallStats,
    whatsAppPkg: String, viberPkg: String, telegramPkg: String,
    isInstalled: (String) -> Boolean,
    haptic: Boolean,
    ctx: android.content.Context,
    onDial: () -> Unit, onWhatsApp: () -> Unit,
    onViber: () -> Unit, onTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasWhatsApp = whatsAppPkg.isNotBlank() && isInstalled(whatsAppPkg)
    val hasViber    = viberPkg.isNotBlank() && isInstalled(viberPkg)
    val hasTelegram = telegramPkg.isNotBlank() && isInstalled(telegramPkg)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUri != null) {
                            AsyncImage(model = photoUri, contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else if (avatarIdentity == "CUSTOM" && customUri.isNotBlank()) {
                            AsyncImage(
                                model = android.net.Uri.parse(customUri),
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    com.toxictrace.nexusconnect.R.drawable.avatar_default),
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(phone, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Stats
            if (stats.totalCalls > 0) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(Icons.Default.Call, stats.totalCalls.toString(), stringResource(R.string.calls))
                            VerticalDivider(modifier = Modifier.height(44.dp))
                            StatItem(Icons.Default.AccessTime, formatDuration(stats.totalDurationSec), stringResource(R.string.total_time))
                            VerticalDivider(modifier = Modifier.height(44.dp))
                            StatItem(
                                icon = when (stats.lastCallType) {
                                    CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived
                                    CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade
                                    else -> Icons.Default.CallMissed
                                },
                                value = stats.lastCallDate?.let { formatDate(it, ctx.getString(R.string.today)) } ?: "—",
                                label = stringResource(R.string.last_call_label), valueLines = 2
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

            // Phone
            item { CallOption(Icons.Default.Call, stringResource(R.string.phone_call), stringResource(R.string.direct_call_action), Color(0xFF1A3CA8), haptic, ctx, onDial) }

            if (hasWhatsApp) {
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp)) }
                item { CallOption(Icons.Default.Message, "WhatsApp", stringResource(R.string.open_in_whatsapp), Color(0xFF25D366), haptic, ctx, onWhatsApp) }
            }
            if (hasViber) {
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp)) }
                item { CallOption(Icons.Default.PhoneAndroid, "Viber", stringResource(R.string.open_in_viber), Color(0xFF7360F2), haptic, ctx, onViber) }
            }
            if (hasTelegram) {
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp)) }
                item { CallOption(Icons.Default.Send, "Telegram", stringResource(R.string.open_in_telegram), Color(0xFF2AABEE), haptic, ctx, onTelegram) }
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, value: String, label: String, valueLines: Int = 1) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            maxLines = valueLines, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun CallOption(icon: ImageVector, label: String, sublabel: String, color: Color,
                       haptic: Boolean, context: android.content.Context, onClick: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            if (haptic) {
                try {
                    view.isHapticFeedbackEnabled = true
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    )
                } catch (_: Exception) {}
            }
            onClick()
        }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(46.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sublabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp))
    }
}

private fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s}s"; else -> "${s}s" }
}

private fun formatDate(ts: Long, todayLabel: String = "Today"): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    val calNow = java.util.Calendar.getInstance()
    val calTs  = java.util.Calendar.getInstance().also { it.timeInMillis = ts }
    val isToday = calNow.get(java.util.Calendar.YEAR) == calTs.get(java.util.Calendar.YEAR) &&
                  calNow.get(java.util.Calendar.DAY_OF_YEAR) == calTs.get(java.util.Calendar.DAY_OF_YEAR)
    val diff = System.currentTimeMillis() - ts
    return when {
        isToday -> "$todayLabel\n$time"
        diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE\n", Locale.getDefault()).format(Date(ts)) + time
        else -> SimpleDateFormat("dd.MM.yy\n", Locale.getDefault()).format(Date(ts)) + time
    }
}
