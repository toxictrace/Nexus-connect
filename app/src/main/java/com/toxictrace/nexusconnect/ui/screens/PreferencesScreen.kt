package com.toxictrace.nexusconnect.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.toxictrace.nexusconnect.data.model.*
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.ui.theme.AccentColors
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

data class AppInfo(val packageName: String, val label: String, val icon: ImageBitmap?)

@Composable
fun PreferencesScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ClickActionSection(
            settings = settings,
            onUpdate = { updated -> viewModel.updateSettings { updated } }
        )
        MessengerSection(
            settings = settings,
            onUpdate = { updated -> viewModel.updateSettings { updated } }
        )
        FeedbackSection(
            settings = settings,
            onUpdate = { enabled -> viewModel.updateSettings { s -> s.copy(hapticFeedback = enabled) } }
        )
        AppearanceSection(
            settings = settings,
            onUpdate = { updated -> viewModel.updateSettings { updated } }
        )
        AvatarIdentitySection(
            settings = settings,
            onUpdate = { identity -> viewModel.updateSettings { s -> s.copy(avatarIdentity = identity) } }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = { }, modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp)) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Import\nSettings", style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = { }, modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp)) {
                Icon(Icons.Default.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text("Export\nSettings", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Click Action ──────────────────────────────────────────────────────────────

@Composable
private fun ClickActionSection(settings: WidgetSettings, onUpdate: (WidgetSettings) -> Unit) {
    Column {
        SectionHeader("GLOBAL ACTION", "Select the primary behavior when tapping a contact tile.")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(ClickAction.SHOW_DIALOG, "Show selection dialog", "Choose how to call on every tap"),
                Triple(ClickAction.DIRECT_CALL, "Direct Call", "Call immediately without dialog")
            ).forEach { (action, title, subtitle) ->
                ActionCard(
                    title = title, subtitle = subtitle,
                    selected = settings.clickAction == action,
                    icon = if (action == ClickAction.SHOW_DIALOG) Icons.Default.GridView else Icons.Default.Call,
                    onClick = { onUpdate(settings.copy(clickAction = action)) }
                )
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

// ── Messenger picker ──────────────────────────────────────────────────────────

@Composable
private fun MessengerSection(settings: WidgetSettings, onUpdate: (WidgetSettings) -> Unit) {
    val context = LocalContext.current

    val allApps: List<AppInfo> = remember {
        val pm = context.packageManager
        // GET_META_DATA needed to get all installed apps on Android 11+
        pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                runCatching {
                    // Only include apps that have a launcher activity (visible to user)
                    val hasLauncher = pm.getLaunchIntentForPackage(info.packageName) != null
                    if (!hasLauncher) return@mapNotNull null
                    val label = pm.getApplicationLabel(info).toString()
                    val iconBmp = runCatching {
                        pm.getApplicationIcon(info.packageName).toBitmap(48, 48).asImageBitmap()
                    }.getOrNull()
                    AppInfo(info.packageName, label, iconBmp)
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    var showPickerFor by remember { mutableStateOf<String?>(null) } // "whatsapp" | "viber" | "telegram"

    Column {
        SectionHeader("MESSENGERS", "Choose which app to use for each messenger.")
        Spacer(Modifier.height(12.dp))
        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            MessengerRow("WhatsApp", settings.messengerWhatsApp, allApps) {
                showPickerFor = "whatsapp"
            }
            HorizontalDivider()
            MessengerRow("Viber", settings.messengerViber, allApps) {
                showPickerFor = "viber"
            }
            HorizontalDivider()
            MessengerRow("Telegram", settings.messengerTelegram, allApps) {
                showPickerFor = "telegram"
            }
        }
    }

    // App picker dialog
    if (showPickerFor != null) {
        AppPickerDialog(
            apps     = allApps,
            current  = when (showPickerFor) {
                "whatsapp" -> settings.messengerWhatsApp
                "viber"    -> settings.messengerViber
                else       -> settings.messengerTelegram
            },
            onSelect = { pkg ->
                when (showPickerFor) {
                    "whatsapp" -> onUpdate(settings.copy(messengerWhatsApp = pkg))
                    "viber"    -> onUpdate(settings.copy(messengerViber = pkg))
                    else       -> onUpdate(settings.copy(messengerTelegram = pkg))
                }
                showPickerFor = null
            },
            onDismiss = { showPickerFor = null }
        )
    }
}

@Composable
private fun MessengerRow(label: String, currentPkg: String, allApps: List<AppInfo>, onClick: () -> Unit) {
    val displayLabel = if (currentPkg.isBlank()) "Not set"
    else allApps.firstOrNull { it.packageName == currentPkg }?.label ?: currentPkg
    val icon = if (currentPkg.isNotBlank())
        allApps.firstOrNull { it.packageName == currentPkg }?.icon else null

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)))
            }
            Text(displayLabel, style = MaterialTheme.typography.bodyMedium,
                color = if (currentPkg.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AppPickerDialog(apps: List<AppInfo>, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isBlank()) apps
    else apps.filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose app") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect("") }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                            Text("None (auto)", modifier = Modifier.weight(1f))
                            if (current.isBlank()) Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        HorizontalDivider()
                    }
                    items(filtered) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(app.packageName) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (app.icon != null) {
                                Image(bitmap = app.icon, contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)))
                            } else {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (current == app.packageName) {
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Feedback ──────────────────────────────────────────────────────────────────

@Composable
private fun FeedbackSection(settings: WidgetSettings, onUpdate: (Boolean) -> Unit) {
    Column {
        Text("FEEDBACK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Vibration, null) }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haptic Feedback", style = MaterialTheme.typography.titleMedium)
                    Text("Vibrate on interaction", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.hapticFeedback, onCheckedChange = onUpdate)
            }
        }
    }
}

// ── Appearance ────────────────────────────────────────────────────────────────

@Composable
private fun AppearanceSection(settings: WidgetSettings, onUpdate: (WidgetSettings) -> Unit) {
    Column {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Customize how Nexus Connect looks on your device.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AppTheme.LIGHT to "Light", AppTheme.DARK to "Dark").forEach { (theme, label) ->
                ThemeCard(label = label, isDark = theme == AppTheme.DARK,
                    selected = settings.theme == theme,
                    onClick = { onUpdate(settings.copy(theme = theme)) },
                    modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dynamic Colors", style = MaterialTheme.typography.titleMedium)
                    Text("Use system colors for a cohesive look (Monet)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentColors.forEachIndexed { idx, color ->
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                                .then(if (settings.accentColorIndex == idx) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                .clickable { onUpdate(settings.copy(accentColorIndex = idx)) })
                        }
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Palette, null, modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Switch(checked = settings.dynamicColors,
                    onCheckedChange = { onUpdate(settings.copy(dynamicColors = it)) })
            }
        }
    }
}

@Composable
private fun ThemeCard(label: String, isDark: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable { onClick() }, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp))
                .background(if (isDark) Color(0xFF1A1A2E) else Color.White)) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (isDark) Color(0xFF3A3A5C) else Color(0xFFDDDDDD)))
                    Box(Modifier.fillMaxWidth(0.4f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (isDark) Color(0xFF2A2A4C) else Color(0xFFEEEEEE)))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.End))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                RadioButton(selected = selected, onClick = onClick)
            }
        }
    }
}

// ── Avatar Identity ───────────────────────────────────────────────────────────

@Composable
private fun AvatarIdentitySection(settings: WidgetSettings, onUpdate: (AvatarIdentity) -> Unit) {
    Column {
        Text("Avatar Identity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val options = listOf(
            Triple(AvatarIdentity.SYSTEM_DEFAULT, "System Default", "Generic silhouette based on account status"),
            Triple(AvatarIdentity.DYNAMIC_INITIALS, "Dynamic Initials", "Your first and last name initials on theme color"),
            Triple(AvatarIdentity.PHOTOS_ONLY, "Photos Only", "Prioritize actual profile pictures when available"),
        )
        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            options.forEachIndexed { idx, (identity, title, subtitle) ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onUpdate(identity) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(
                        when (identity) {
                            AvatarIdentity.SYSTEM_DEFAULT   -> MaterialTheme.colorScheme.surfaceVariant
                            AvatarIdentity.DYNAMIC_INITIALS -> MaterialTheme.colorScheme.primary
                            AvatarIdentity.PHOTOS_ONLY      -> Color(0xFFF0E8D8)
                        }
                    ), contentAlignment = Alignment.Center) {
                        when (identity) {
                            AvatarIdentity.SYSTEM_DEFAULT ->
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            AvatarIdentity.DYNAMIC_INITIALS ->
                                Text("JD", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                            AvatarIdentity.PHOTOS_ONLY ->
                                Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = settings.avatarIdentity == identity, onClick = { onUpdate(identity) })
                }
                if (idx < options.lastIndex) HorizontalDivider()
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsCard(contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
