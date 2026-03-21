package com.toxictrace.nexusconnect.widget

import android.content.Intent
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toxictrace.nexusconnect.ui.theme.NexusConnectTheme

class ContactChooserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contactId = intent.getLongExtra("contact_id", -1L)
        val phone     = intent.getStringExtra("contact_phone") ?: ""
        val name      = intent.getStringExtra("contact_name") ?: ""

        setContent {
            NexusConnectTheme {
                ChooserBottomSheet(
                    name = name,
                    phone = phone,
                    onCall = {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        finish()
                    },
                    onWhatsApp = {
                        val cleaned = phone.replace(Regex("[^+\\d]"), "")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleaned")))
                        finish()
                    },
                    onTelegram = {
                        val cleaned = phone.replace(Regex("[^+\\d]"), "")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$cleaned")))
                        finish()
                    },
                    onViber = {
                        val cleaned = phone.replace(Regex("[^+\\d]"), "")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=$cleaned")))
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserBottomSheet(
    name: String,
    phone: String,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onTelegram: () -> Unit,
    onViber: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(phone, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))

            val options = listOf(
                Triple("Phone call",  Icons.Default.Call,        onCall),
                Triple("WhatsApp",    Icons.Default.Message,     onWhatsApp),
                Triple("Telegram",    Icons.Default.Send,        onTelegram),
                Triple("Viber",       Icons.Default.PhoneAndroid, onViber),
            )
            options.forEach { (label, icon, action) ->
                ChooserItem(label = label, icon = icon, onClick = action)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChooserItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = label,
            tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
