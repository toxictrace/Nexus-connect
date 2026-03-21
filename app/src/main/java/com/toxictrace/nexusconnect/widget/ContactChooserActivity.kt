package com.toxictrace.nexusconnect.widget

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toxictrace.nexusconnect.ui.theme.NexusConnectTheme

class ContactChooserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phone = intent.getStringExtra("contact_phone") ?: ""
        val name  = intent.getStringExtra("contact_name")  ?: ""

        setContent {
            NexusConnectTheme {
                ChooserSheet(
                    name  = name,
                    phone = phone,
                    isInstalled = ::isInstalled,
                    onDial      = { dial(phone) },
                    onWhatsApp  = { openWhatsApp(phone) },
                    onViber     = { openViber(phone) },
                    onTelegram  = { openTelegram(phone) },
                    onDismiss   = { finish() }
                )
            }
        }
    }

    private fun isInstalled(pkg: String): Boolean =
        runCatching {
            packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)

    private fun dial(phone: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        finish()
    }

    private fun openWhatsApp(phone: String) {
        val cleaned = phone.replace(Regex("[^+\\d]"), "")
        // Try native WhatsApp call intent first
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("whatsapp://send?phone=$cleaned"))
        intent.setPackage("com.whatsapp")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback: web
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$cleaned")))
        }
        finish()
    }

    private fun openViber(phone: String) {
        val cleaned = phone.replace(Regex("[^+\\d]"), "")
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("viber://contact?number=$cleaned"))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://viber.com/0/")))
        }
        finish()
    }

    private fun openTelegram(phone: String) {
        val cleaned = phone.replace(Regex("[^+\\d]"), "")
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("tg://resolve?phone=$cleaned"))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://t.me/+$cleaned")))
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserSheet(
    name: String,
    phone: String,
    isInstalled: (String) -> Boolean,
    onDial: () -> Unit,
    onWhatsApp: () -> Unit,
    onViber: () -> Unit,
    onTelegram: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(phone, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(20.dp))

            // Phone call — always shown
            CallOption(
                icon        = Icons.Default.Call,
                label       = "Phone call",
                sublabel    = "Open dialer",
                color       = Color(0xFF1A3CA8),
                onClick     = onDial
            )
            HorizontalDivider()

            // WhatsApp
            if (isInstalled("com.whatsapp")) {
                CallOption(
                    icon     = Icons.Default.Message,
                    label    = "WhatsApp",
                    sublabel = "Open chat / call",
                    color    = Color(0xFF25D366),
                    onClick  = onWhatsApp
                )
                HorizontalDivider()
            }

            // Viber
            if (isInstalled("com.viber.voip")) {
                CallOption(
                    icon     = Icons.Default.PhoneAndroid,
                    label    = "Viber",
                    sublabel = "Open contact",
                    color    = Color(0xFF7360F2),
                    onClick  = onViber
                )
                HorizontalDivider()
            }

            // Telegram
            if (isInstalled("org.telegram.messenger") ||
                isInstalled("org.telegram.messenger.web")) {
                CallOption(
                    icon     = Icons.Default.Send,
                    label    = "Telegram",
                    sublabel = "Open contact",
                    color    = Color(0xFF2AABEE),
                    onClick  = onTelegram
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CallOption(
    icon: ImageVector,
    label: String,
    sublabel: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = color,
                    modifier = Modifier.size(22.dp))
            }
        }
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(sublabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
