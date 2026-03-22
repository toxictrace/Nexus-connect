package com.toxictrace.nexusconnect.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class PermissionInfo(
    val permission: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val required: Boolean = true
)

val REQUIRED_PERMISSIONS = listOf(
    PermissionInfo(
        Manifest.permission.READ_CONTACTS,
        "Contacts",
        "Access your contacts to display them on the widget",
        Icons.Default.Contacts,
        required = true
    ),
    PermissionInfo(
        Manifest.permission.READ_CALL_LOG,
        "Call Log",
        "Read call history for recent/frequent contacts and call type icons",
        Icons.Default.Call,
        required = true
    ),
    PermissionInfo(
        Manifest.permission.CALL_PHONE,
        "Make Calls",
        "Call directly from the widget without opening the dialer",
        Icons.Default.Phone,
        required = false
    ),
    PermissionInfo(
        Manifest.permission.READ_PHONE_STATE,
        "Phone State",
        "Detect when calls end to update the widget automatically",
        Icons.Default.PhoneCallback,
        required = true
    ),
)

@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    fun checkAll() = REQUIRED_PERMISSIONS.filter { it.required }.all {
        ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED
    }

    var allGranted by remember { mutableStateOf(checkAll()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        allGranted = checkAll()
        if (allGranted) onAllGranted()
    }

    LaunchedEffect(Unit) {
        if (allGranted) {
            onAllGranted()
        } else {
            launcher.launch(REQUIRED_PERMISSIONS.map { it.permission }.toTypedArray())
        }
    }

    if (allGranted) return

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Security, null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Nexus Connect needs the following permissions to work properly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

        REQUIRED_PERMISSIONS.forEach { perm ->
            val granted = ContextCompat.checkSelfPermission(
                context, perm.permission) == PackageManager.PERMISSION_GRANTED
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (granted)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (granted)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (granted) Icons.Default.Check else perm.icon,
                                null,
                                tint = if (granted) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(perm.title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            if (!perm.required) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text("Optional",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text(perm.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                launcher.launch(REQUIRED_PERMISSIONS.map { it.permission }.toTypedArray())
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Grant Permissions", style = MaterialTheme.typography.titleMedium)
        }
    }
}
