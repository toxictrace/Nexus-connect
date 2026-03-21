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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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

        setContent {
            NexusConnectTheme {
                ChooserSheet(
                    name        = name,
                    phone       = phone,
                    photoUri    = photoUri,
                    stats       = stats,
                    isInstalled = ::isInstalled,
                    onDial      = { directCall(phone) },
                    onWhatsApp  = { openWhatsApp(phone) },
                    onViber     = { openViber(phone) },
                    onTelegram  = { openTelegram(phone) },
                    onDismiss   = { finish() }
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

            var lastDate: Long? = null
            var lastType = 0
            var total = 0
            var dur = 0L

            cursor.use {
                val ni = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val di = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val ui = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val ti = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (it.moveToNext()) {
                    val n = it.getString(ni)?.replace(Regex("[\\s\\-().+]"), "")?.takeLast(7) ?: continue
                    if (n == norm) {
                        if (lastDate == null) { lastDate = it.getLong(di); lastType = it.getInt(ti) }
                        total++
                        dur += it.getLong(ui)
                    }
                }
            }
            CallStats(lastDate, total, dur, lastType)
        } catch (e: Exception) { CallStats(null, 0, 0L, 0) }
    }

    private fun isInstalled(pkg: String) = runCatching {
        packageManager.getPackageInfo(pkg, 0); true
    }.getOrDefault(false)

    /** Direct call — uses ACTION_CALL if permission granted, else ACTION_DIAL */
    private fun directCall(phone: String) {
        val hasPermission = checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:$phone")))
        finish()
    }

    private fun openWhatsApp(phone: String) {
        val c = phone.replace(Regex("[^+\\d]"), "")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$c")).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (i.resolveActivity(packageManager) != null) startActivity(i)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$c"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openViber(phone: String) {
        val c = phone.replace(Regex("[^+\\d]"), "")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("viber://contact?number=$c"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (i.resolveActivity(packageManager) != null) startActivity(i)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://viber.com/0/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openTelegram(phone: String) {
        val c = phone.replace(Regex("[^+\\d]"), "")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$c"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (i.resolveActivity(packageManager) != null) startActivity(i)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$c"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserSheet(
    name: String,
    phone: String,
    photoUri: String?,
    stats: CallStats,
    isInstalled: (String) -> Boolean,
    onDial: () -> Unit,
    onWhatsApp: () -> Unit,
    onViber: () -> Unit,
    onTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // ── Header: avatar + name + phone ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            name.split(" ").take(2)
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .joinToString(""),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column {
                    Text(name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(phone, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Call stats ────────────────────────────────────────────────
            if (stats.totalCalls > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            icon  = Icons.Default.Call,
                            value = stats.totalCalls.toString(),
                            label = "Calls"
                        )
                        VerticalDivider(modifier = Modifier.height(44.dp))
                        StatItem(
                            icon  = Icons.Default.AccessTime,
                            value = formatDuration(stats.totalDurationSec),
                            label = "Total time"
                        )
                        VerticalDivider(modifier = Modifier.height(44.dp))
                        StatItem(
                            icon = when (stats.lastCallType) {
                                CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived
                                CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade
                                else -> Icons.Default.CallMissed
                            },
                            value = stats.lastCallDate?.let { formatDate(it) } ?: "—",
                            label = "Last call",
                            valueLines = 2
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // ── Phone call — always first ─────────────────────────────────
            CallOption(
                icon = Icons.Default.Call,
                label = "Phone call",
                sublabel = "Direct call",
                color = Color(0xFF1A3CA8),
                onClick = onDial
            )

            // ── Messengers — only if installed ────────────────────────────
            if (isInstalled("com.whatsapp") || isInstalled("com.whatsapp.w4b")) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                CallOption(
                    icon = Icons.Default.Message,
                    label = "WhatsApp",
                    sublabel = "Open in WhatsApp",
                    color = Color(0xFF25D366),
                    onClick = onWhatsApp
                )
            }
            if (isInstalled("com.viber.voip") ||
                isInstalled("com.viber.calls") ||
                isInstalled("air.WL.android.viber")) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                CallOption(
                    icon = Icons.Default.PhoneAndroid,
                    label = "Viber",
                    sublabel = "Open in Viber",
                    color = Color(0xFF7360F2),
                    onClick = onViber
                )
            }
            if (isInstalled("org.telegram.messenger") ||
                isInstalled("org.telegram.messenger.web") ||
                isInstalled("org.thunderdog.challegram") ||
                isInstalled("im.molly.app") ||
                isInstalled("org.telegram.plus")) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                CallOption(
                    icon = Icons.Default.Send,
                    label = "Telegram",
                    sublabel = "Open in Telegram",
                    color = Color(0xFF2AABEE),
                    onClick = onTelegram
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    valueLines: Int = 1
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Icon(icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = valueLines,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CallOption(
    icon: ImageVector, label: String, sublabel: String,
    color: Color, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(sublabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp))
    }
}

private fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else  -> "${s}s"
    }
}

private fun formatDate(timestamp: Long): String {
    val now  = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < TimeUnit.DAYS.toMillis(1) ->
            "Today\n" + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < TimeUnit.DAYS.toMillis(7) ->
            SimpleDateFormat("EEE\nHH:mm", Locale.getDefault()).format(Date(timestamp))
        else ->
            SimpleDateFormat("dd.MM.yy\nHH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
