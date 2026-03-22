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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.toxictrace.nexusconnect.R

data class PermissionInfo(
    val permission: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val required: Boolean = true
)

val REQUIRED_PERMISSIONS = listOf(
    PermissionInfo(Manifest.permission.READ_CONTACTS,  R.string.permission_contacts,    R.string.permission_contacts_desc,    Icons.Default.Contacts,      true),
    PermissionInfo(Manifest.permission.READ_CALL_LOG,  R.string.permission_call_log,    R.string.permission_call_log_desc,    Icons.Default.Call,          true),
    PermissionInfo(Manifest.permission.CALL_PHONE,     R.string.permission_call_phone,  R.string.permission_call_phone_desc,  Icons.Default.Phone,         false),
    PermissionInfo(Manifest.permission.READ_PHONE_STATE, R.string.permission_phone_state, R.string.permission_phone_state_desc, Icons.Default.PhoneCallback, true),
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
        if (allGranted) onAllGranted()
        else launcher.launch(REQUIRED_PERMISSIONS.map { it.permission }.toTypedArray())
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
        Text(stringResource(R.string.permissions_required),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.permissions_subtitle),
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
                    Surface(shape = CircleShape,
                        color = if (granted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (granted) Icons.Default.Check else perm.icon, null,
                                tint = if (granted) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(perm.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            if (!perm.required) {
                                Surface(shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(stringResource(R.string.optional),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text(stringResource(perm.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { launcher.launch(REQUIRED_PERMISSIONS.map { it.permission }.toTypedArray()) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.grant_permissions), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun BatteryOptimizationBanner() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nexus_ui_prefs", android.content.Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean("battery_banner_dismissed", false)) }

    val isIgnoringBatteryOpt = remember {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    if (dismissed || isIgnoringBatteryOpt) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
        )
    ) {
        Row(modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.BatteryAlert, null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.battery_optimization),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.battery_optimization_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                dismissed = true
                prefs.edit().putBoolean("battery_banner_dismissed", true).apply()
            }) { Text(stringResource(R.string.dismiss)) }
            TextButton(onClick = {
                try {
                    context.startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")))
                } catch (_: Exception) {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                }
            }) {
                Text(stringResource(R.string.open_settings),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
