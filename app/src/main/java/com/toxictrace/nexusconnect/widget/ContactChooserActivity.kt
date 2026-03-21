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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactId = intent.getLongExtra("contact_id", -1L)
        val phone     = intent.getStringExtra("contact_phone") ?: ""
        val name      = intent.getStringExtra("contact_name")  ?: ""
        val photoUri  = if (contactId > 0) PhotoProvider.uriForContact(contactId).toString() else null
        val stats     = loadCallStats(phone)

        // Read configured messenger packages from prefs
        val whatsAppPkg  = WidgetPrefs.getMessengerWhatsApp(this)
        val viberPkg     = WidgetPrefs.getMessengerViber(this)
        val telegramPkg  = WidgetPrefs.getMessengerTelegram(this)

        setContent {
            NexusConnectTheme {
                ChooserSheet(
                    name         = name,
                    phone        = phone,
                    photoUri     = photoUri,
                    stats        = stats,
                    whatsAppPkg  = whatsAppPkg,
                    viberPkg     = viberPkg,
                    telegramPkg  = telegramPkg,
                    isInstalled  = ::isInstalled,
                    onDial       = { directCall(phone) },
                    onWhatsApp   = { openWithPackage(whatsAppPkg, buildWhatsAppUri(phone)) },
                    onViber      = { openViber(phone, viberPkg) },
                    onTelegram   = { openWithPackage(telegramPkg, buildTelegramUri(phone)) },
                    onDismiss    = { finish() }
                )
            }
        }
    }

    private fun loadCallStats(phone: String): CallStats {
        if (phone.isBlank()) return CallStats(null, 0, 0L, 0)
        val norm = phone.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
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
                    if (n == norm) {
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
        val c = phone.replace(Regex("[^+\\d]"), "")
        // viber://chat opens chat directly (not profile)
        val chatUri = Uri.parse("viber://chat?number=%2B$c")
        val i = Intent(Intent.ACTION_VIEW, chatUri).apply {
            setPackage(effectivePkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (i.resolveActivity(packageManager) != null) {
            startActivity(i)
        } else {
            // Fallback: tel: with package (opens Viber dialer)
            val telIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:$phone")).apply {
                setPackage(effectivePkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (telIntent.resolveActivity(packageManager) != null) startActivity(telIntent)
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
    stats: CallStats,
    whatsAppPkg: String, viberPkg: String, telegramPkg: String,
    isInstalled: (String) -> Boolean,
    onDial: () -> Unit, onWhatsApp: () -> Unit,
    onViber: () -> Unit, onTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasWhatsApp = isInstalled(whatsAppPkg.ifBlank { "com.whatsapp" }) ||
            isInstalled("com.whatsapp") || isInstalled("com.whatsapp.w4b")
    val hasViber    = isInstalled(viberPkg.ifBlank { "com.viber.voip" }) ||
            isInstalled("com.viber.voip") || isInstalled("air.WL.android.viber")
    val hasTelegram = isInstalled(telegramPkg) ||
            isInstalled("org.telegram.messenger") ||
            isInstalled("org.telegram.messenger.web") ||
            isInstalled("org.thunderdog.challegram")

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
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(
                                name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString(""),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
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
                            StatItem(Icons.Default.Call, stats.totalCalls.toString(), "Calls")
                            VerticalDivider(modifier = Modifier.height(44.dp))
                            StatItem(Icons.Default.AccessTime, formatDuration(stats.totalDurationSec), "Total time")
                            VerticalDivider(modifier = Modifier.height(44.dp))
                            StatItem(
                                icon = when (stats.lastCallType) {
                                    CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived
                                    CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade
                                    else -> Icons.Default.CallMissed
                                },
                                value = stats.lastCallDate?.let { formatDate(it) } ?: "—",
                                label = "Last call", valueLines = 2
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

            // Phone
            item { CallOption(Icons.Default.Call, "Phone call", "Direct call", Color(0xFF1A3CA8), onDial) }

            if (hasWhatsApp) {
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp)) }
                item { CallOption(Icons.Default.Message, "WhatsApp", "Open in WhatsApp", Color(0xFF25D366), onWhatsApp) }
            }
            if (hasViber) {
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp)) }
                item { CallOption(Icons.Default.PhoneAndroid, "Viber", "Open in Viber", Color(0xFF7360F2), onViber) }
            }
            if (hasTelegram) {
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp)) }
                item { CallOption(Icons.Default.Send, "Telegram", "Open in Telegram", Color(0xFF2AABEE), onTelegram) }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CallOption(icon: ImageVector, label: String, sublabel: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
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

private fun formatDate(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    return when {
        diff < TimeUnit.DAYS.toMillis(1) -> "Today\n$time"
        diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE\n", Locale.getDefault()).format(Date(ts)) + time
        else -> SimpleDateFormat("dd.MM.yy\n", Locale.getDefault()).format(Date(ts)) + time
    }
}
