package com.toxictrace.nexusconnect.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Brightness4
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
    val installedApps by viewModel.installedApps.collectAsState()
    val appsLoading by viewModel.appsLoading.collectAsState()

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
            apps = installedApps,
            appsLoading = appsLoading,
            onUpdate = { updated -> viewModel.updateSettings { updated } }
        )
        FeedbackSection(
            settings = settings,
            onUpdate = { enabled -> viewModel.updateSettings { s -> s.copy(hapticFeedback = enabled) } }
        )
        CallIconSection(
            settings = settings,
            onUpdate = { enabled -> viewModel.updateSettings { s -> s.copy(showCallTypeIcon = enabled) } }
        )
        AppearanceSection(
            settings = settings,
            onUpdate = { updated -> viewModel.updateSettings { updated } }
        )
        AvatarIdentitySection(
            settings = settings,
            onUpdate = { updated -> viewModel.updateSettings { updated } }
        )
        BackupSection(
            settings = settings,
            viewModel = viewModel
        )
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
private fun MessengerSection(
    settings: WidgetSettings,
    apps: List<AppInfo>,
    appsLoading: Boolean,
    onUpdate: (WidgetSettings) -> Unit
) {
    var showPickerFor by remember { mutableStateOf<String?>(null) }

    Column {
        SectionHeader("MESSENGERS", "Choose which app to use for each messenger.")
        Spacer(Modifier.height(12.dp))
        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            MessengerRow("WhatsApp", settings.messengerWhatsApp, apps) {
                showPickerFor = "whatsapp"
            }
            HorizontalDivider()
            MessengerRow("Viber", settings.messengerViber, apps) {
                showPickerFor = "viber"
            }
            HorizontalDivider()
            MessengerRow("Telegram", settings.messengerTelegram, apps) {
                showPickerFor = "telegram"
            }
        }
        if (appsLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }

    if (showPickerFor != null) {
        AppPickerDialog(
            apps      = apps,
            loading   = appsLoading,
            current   = when (showPickerFor) {
                "whatsapp" -> settings.messengerWhatsApp
                "viber"    -> settings.messengerViber
                else       -> settings.messengerTelegram
            },
            onSelect  = { pkg ->
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
                androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)))
            }
            Text(displayLabel, style = MaterialTheme.typography.bodyMedium,
                color = if (currentPkg.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AppPickerDialog(apps: List<AppInfo>, loading: Boolean, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
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
                                androidx.compose.foundation.Image(bitmap = app.icon, contentDescription = null,
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
private fun CallIconSection(settings: WidgetSettings, onUpdate: (Boolean) -> Unit) {
    Column {
        Text("CALL TYPE ICON", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Call, null)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show call type icon", style = MaterialTheme.typography.titleMedium)
                    Text("Incoming / Outgoing / Missed on tile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.showCallTypeIcon, onCheckedChange = onUpdate)
            }
        }
    }
}

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeCard(label = "Light", isDark = false,
                selected = settings.theme == AppTheme.LIGHT,
                onClick = { onUpdate(settings.copy(theme = AppTheme.LIGHT)) },
                modifier = Modifier.weight(1f))
            ThemeCard(label = "Dark", isDark = true,
                selected = settings.theme == AppTheme.DARK,
                onClick = { onUpdate(settings.copy(theme = AppTheme.DARK)) },
                modifier = Modifier.weight(1f))
            ThemeCard(label = "System", isDark = isSystemInDarkTheme(),
                selected = settings.theme == AppTheme.SYSTEM,
                onClick = { onUpdate(settings.copy(theme = AppTheme.SYSTEM)) },
                modifier = Modifier.weight(1f),
                showAuto = true)
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (settings.accentColorIndex == idx && !settings.dynamicColors)
                                            Modifier.border(3.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        onUpdate(settings.copy(
                                            accentColorIndex = idx,
                                            dynamicColors = false
                                        ))
                                    }
                            )
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
private fun ThemeCard(
    label: String, isDark: Boolean, selected: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier,
    showAuto: Boolean = false
) {
    Card(modifier = modifier.clickable { onClick() }, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp))
                .background(if (isDark) Color(0xFF1A1A2E) else Color.White)) {
                if (showAuto) {
                    // Half light / half dark for System
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1A1A2E)))
                    }
                    Icon(Icons.Default.Brightness4, null,
                        tint = Color(0xFF888888),
                        modifier = Modifier.align(Alignment.Center).size(20.dp))
                } else {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (isDark) Color(0xFF3A3A5C) else Color(0xFFDDDDDD)))
                        Box(Modifier.fillMaxWidth(0.4f).height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (isDark) Color(0xFF2A2A4C) else Color(0xFFEEEEEE)))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Avatar Identity ───────────────────────────────────────────────────────────

@Composable
private fun AvatarIdentitySection(settings: WidgetSettings, onUpdate: (WidgetSettings) -> Unit) {
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onUpdate(settings.copy(
                avatarIdentity  = AvatarIdentity.CUSTOM,
                customAvatarUri = uri.toString()
            ))
        }
    }

    Column {
        Text("Avatar Identity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Shown for contacts without a photo.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SettingsCard(contentPadding = PaddingValues(0.dp)) {

            // Option 1: Default silhouette
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onUpdate(settings.copy(avatarIdentity = AvatarIdentity.DEFAULT)) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview of silhouette
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Default", style = MaterialTheme.typography.titleMedium)
                    Text("Standard silhouette image", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(
                    selected = settings.avatarIdentity == AvatarIdentity.DEFAULT,
                    onClick = { onUpdate(settings.copy(avatarIdentity = AvatarIdentity.DEFAULT)) }
                )
            }

            HorizontalDivider()

            // Option 2: Custom image from gallery
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { galleryLauncher.launch("image/*") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview of selected image
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (settings.avatarIdentity == AvatarIdentity.CUSTOM && settings.customAvatarUri.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = settings.customAvatarUri,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.AddPhotoAlternate, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Custom image", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.avatarIdentity == AvatarIdentity.CUSTOM && settings.customAvatarUri.isNotBlank())
                            "Tap to change image"
                        else "Pick from gallery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RadioButton(
                    selected = settings.avatarIdentity == AvatarIdentity.CUSTOM,
                    onClick = { galleryLauncher.launch("image/*") }
                )
            }
        }
    }
}

// ── Backup & Restore ──────────────────────────────────────────────────────────

@Composable
private fun BackupSection(settings: WidgetSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<Pair<String, android.net.Uri>>>(emptyList()) }

    // Folder picker
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateSettings { s -> s.copy(backupFolderUri = uri.toString()) }
        }
    }

    Column {
        SectionHeader("BACKUP & RESTORE", "Save and restore your settings.")
        Spacer(Modifier.height(12.dp))

        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            // Folder selection
            Row(
                modifier = Modifier.fillMaxWidth().clickable { folderPicker.launch(null) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Folder, null,
                    tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Backup folder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.backupFolderUri.isBlank()) "Not selected"
                        else decodeFolderUri(settings.backupFolderUri),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.backupFolderUri.isBlank())
                            MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary
                    )
                }
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp))
            }

            HorizontalDivider()

            // Save backup
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = settings.backupFolderUri.isNotBlank()) {
                        viewModel.saveBackup(
                            onResult = { name -> message = "Saved: $name" },
                            onError  = { err  -> message = "Error: $err" }
                        )
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Upload, null,
                    tint = if (settings.backupFolderUri.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Save backup", style = MaterialTheme.typography.titleMedium,
                        color = if (settings.backupFolderUri.isBlank())
                            MaterialTheme.colorScheme.outline else Color.Unspecified)
                    Text("Save current settings to file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            // Restore backup
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = settings.backupFolderUri.isNotBlank()) {
                        backups = viewModel.listBackups()
                        showRestoreDialog = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Download, null,
                    tint = if (settings.backupFolderUri.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Restore backup", style = MaterialTheme.typography.titleMedium,
                        color = if (settings.backupFolderUri.isBlank())
                            MaterialTheme.colorScheme.outline else Color.Unspecified)
                    Text("Load settings from saved file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Status message
        message?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("Error"))
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp))
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(4000)
                message = null
            }
        }
    }

    // Restore file picker dialog
    if (showRestoreDialog) {
        RestoreDialog(
            backups   = backups,
            onSelect  = { uri ->
                showRestoreDialog = false
                viewModel.restoreBackup(
                    fileUri  = uri,
                    onResult = { message = "Restored successfully" },
                    onError  = { err -> message = "Error: $err" }
                )
            },
            onDismiss = { showRestoreDialog = false }
        )
    }
}

@Composable
private fun RestoreDialog(
    backups: List<Pair<String, android.net.Uri>>,
    onSelect: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select backup") },
        text = {
            if (backups.isEmpty()) {
                Text("No backups found in selected folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(backups) { (name, uri) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(uri) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Description, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun decodeFolderUri(uriStr: String): String {
    return try {
        // URI looks like: content://com.android.externalstorage.documents/tree/primary%3ADownload%2FNexus
        val decoded = java.net.URLDecoder.decode(uriStr, "UTF-8")
        // Extract path after "primary:" or "sdcard:"
        val path = decoded.substringAfterLast("primary:")
            .substringAfterLast("sdcard:")
            .substringAfterLast(":")
            .replace("/tree/", "")
            .ifBlank { decoded.substringAfterLast("/") }
        path.ifBlank { "Selected" }
    } catch (_: Exception) {
        uriStr.substringAfterLast("/").ifBlank { "Selected" }
    }
}

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
