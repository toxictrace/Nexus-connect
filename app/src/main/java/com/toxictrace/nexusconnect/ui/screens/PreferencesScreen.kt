package com.toxictrace.nexusconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toxictrace.nexusconnect.data.model.*
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.ui.theme.AccentColors
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

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
            OutlinedButton(
                onClick = { },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import\nSettings", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export\nSettings", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ClickActionSection(settings: WidgetSettings, onUpdate: (WidgetSettings) -> Unit) {
    Column {
        SectionHeader("GLOBAL ACTION", "Select the primary behavior when tapping a contact tile.")
        Spacer(Modifier.height(12.dp))
        val actions = listOf(
            Triple(ClickAction.SHOW_DIALOG, "Show selection dialog", "Choose how to call on every tap"),
            Triple(ClickAction.DIRECT_CALL, "Direct Call",           "Call immediately without dialog"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (action, title, subtitle) ->
                val icon: ImageVector = when (action) {
                    ClickAction.SHOW_DIALOG -> Icons.Default.GridView
                    ClickAction.DIRECT_CALL -> Icons.Default.Call
                }
                ActionCard(
                    title = title, subtitle = subtitle,
                    selected = settings.clickAction == action,
                    icon = icon,
                    onClick = { onUpdate(settings.copy(clickAction = action)) }
                )
            }
        }
    }
}

@Composable
private fun MessengerSection(settings: WidgetSettings, onUpdate: (WidgetSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Get all installed apps that could handle messaging
    val allApps = remember {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("tel:+1234567890"))
        // Get apps by known packages
        val knownMessengers = listOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.viber.voip" to "Viber",
            "air.WL.android.viber" to "Viber (alt)",
            "org.telegram.messenger" to "Telegram",
            "org.telegram.messenger.web" to "Telegram Web",
            "org.thunderdog.challegram" to "Telegram X",
            "im.molly.app" to "Molly",
            "org.telegram.plus" to "Telegram Plus",
            "com.nicegram.app" to "Nicegram",
            "org.telegram.bimbot" to "BimBot",
        )
        knownMessengers.filter { (pkg, _) ->
            runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
    }

    Column {
        SectionHeader("MESSENGERS", "Choose which app to use for each messenger.")
        Spacer(Modifier.height(12.dp))
        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            MessengerPicker(
                label       = "WhatsApp",
                currentPkg  = settings.messengerWhatsApp,
                defaultPkgs = listOf("com.whatsapp", "com.whatsapp.w4b"),
                allApps     = allApps,
                onSelect    = { onUpdate(settings.copy(messengerWhatsApp = it)) }
            )
            HorizontalDivider()
            MessengerPicker(
                label       = "Viber",
                currentPkg  = settings.messengerViber,
                defaultPkgs = listOf("com.viber.voip", "air.WL.android.viber"),
                allApps     = allApps,
                onSelect    = { onUpdate(settings.copy(messengerViber = it)) }
            )
            HorizontalDivider()
            MessengerPicker(
                label       = "Telegram",
                currentPkg  = settings.messengerTelegram,
                defaultPkgs = listOf("org.telegram.messenger", "org.telegram.messenger.web",
                    "org.thunderdog.challegram"),
                allApps     = allApps,
                onSelect    = { onUpdate(settings.copy(messengerTelegram = it)) }
            )
        }
    }
}

@Composable
private fun MessengerPicker(
    label: String,
    currentPkg: String,
    defaultPkgs: List<String>,
    allApps: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    // Apps relevant to this messenger slot
    val relevantApps = allApps.filter { (pkg, _) ->
        pkg in defaultPkgs || currentPkg == pkg
    }.ifEmpty {
        // Show all known apps if none of defaults are installed
        allApps
    }

    val displayName = if (currentPkg.isBlank()) {
        val firstInstalled = defaultPkgs.firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
        if (firstInstalled != null) "Auto-detect" else "Not installed"
    } else {
        allApps.firstOrNull { it.first == currentPkg }?.second ?: currentPkg
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = relevantApps.isNotEmpty()) { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(displayName, style = MaterialTheme.typography.bodyMedium,
                color = if (relevantApps.isEmpty())
                    MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.primary)
            if (relevantApps.isNotEmpty()) {
                Icon(Icons.Default.ArrowDropDown, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (currentPkg.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Auto-detect") },
                    onClick = { onSelect(""); expanded = false }
                )
                HorizontalDivider()
            }
            relevantApps.forEach { (pkg, name) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(name)
                            if (pkg == currentPkg) {
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = { onSelect(pkg); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String, subtitle: String, selected: Boolean,
    icon: ImageVector, onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun FeedbackSection(settings: WidgetSettings, onUpdate: (Boolean) -> Unit) {
    Column {
        Text("FEEDBACK", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Vibration, null)
                    }
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

@Composable
private fun AppearanceSection(settings: WidgetSettings, onUpdate: (WidgetSettings) -> Unit) {
    Column {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Customize how Nexus Connect looks on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AppTheme.LIGHT to "Light", AppTheme.DARK to "Dark").forEach { (theme, label) ->
                ThemeCard(
                    label = label, isDark = theme == AppTheme.DARK,
                    selected = settings.theme == theme,
                    onClick = { onUpdate(settings.copy(theme = theme)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dynamic Colors", style = MaterialTheme.typography.titleMedium)
                    Text("Use system colors for a cohesive look (Monet)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentColors.forEachIndexed { idx, color ->
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                                    .then(if (settings.accentColorIndex == idx)
                                        Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { onUpdate(settings.copy(accentColorIndex = idx)) }
                            )
                        }
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant).clickable { },
                            contentAlignment = Alignment.Center
                        ) {
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
private fun ThemeCard(
    label: String, isDark: Boolean, selected: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF1A1A2E) else Color.White)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (isDark) Color(0xFF3A3A5C) else Color(0xFFDDDDDD)))
                    Box(Modifier.fillMaxWidth(0.4f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (isDark) Color(0xFF2A2A4C) else Color(0xFFEEEEEE)))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(20.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary).align(Alignment.End))
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

@Composable
private fun AvatarIdentitySection(settings: WidgetSettings, onUpdate: (AvatarIdentity) -> Unit) {
    Column {
        Text("Avatar Identity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val options = listOf(
            Triple(AvatarIdentity.SYSTEM_DEFAULT,   "System Default",   "Generic silhouette based on account status"),
            Triple(AvatarIdentity.DYNAMIC_INITIALS, "Dynamic Initials", "Your first and last name initials on theme color"),
            Triple(AvatarIdentity.PHOTOS_ONLY,      "Photos Only",      "Prioritize actual profile pictures when available"),
        )
        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            options.forEachIndexed { idx, (identity, title, subtitle) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onUpdate(identity) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(
                            when (identity) {
                                AvatarIdentity.SYSTEM_DEFAULT   -> MaterialTheme.colorScheme.surfaceVariant
                                AvatarIdentity.DYNAMIC_INITIALS -> MaterialTheme.colorScheme.primary
                                AvatarIdentity.PHOTOS_ONLY      -> Color(0xFFF0E8D8)
                            }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (identity) {
                            AvatarIdentity.SYSTEM_DEFAULT ->
                                Icon(Icons.Default.Person, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            AvatarIdentity.DYNAMIC_INITIALS ->
                                Text("JD", style = MaterialTheme.typography.titleSmall,
                                    color = Color.White, fontWeight = FontWeight.Bold)
                            AvatarIdentity.PHOTOS_ONLY ->
                                Icon(Icons.Default.Image, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = settings.avatarIdentity == identity,
                        onClick = { onUpdate(identity) })
                }
                if (idx < options.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
