package com.toxictrace.nexusconnect.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.viewmodel.ContactSortMode
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

@Composable
fun ContactsScreen(viewModel: MainViewModel) {
    val context       = LocalContext.current
    val contacts      by viewModel.displayContacts.collectAsState()
    val sortMode      by viewModel.sortMode.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    var searchQuery   by remember { mutableStateOf("") }

    // Check permission once on composition
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadContacts()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadContacts()
        else permLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    val filtered = contacts.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Find contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Sort tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContactSortMode.values().forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { viewModel.setSortMode(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (!hasPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Contacts permission required.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { permLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                            Text("Grant")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Contact list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = if (selectedCount > 0) 88.dp else 16.dp)
            ) {
                items(filtered, key = { it.id }) { contact ->
                    ContactItem(
                        contact = contact,
                        onToggle = { viewModel.toggleContactSelection(contact.id) }
                    )
                }
            }
        }

        // Bottom action bar
        if (selectedCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Selected $selectedCount / ${contacts.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "MANAGE WIDGET VIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.applyAndUpdateWidget() },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Update Widget")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactItem(contact: Contact, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (contact.isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.DragHandle, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Checkbox(
                checked = contact.isSelected,
                onCheckedChange = { onToggle() }
            )
            ContactAvatar(contact = contact, size = 48)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (contact.isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (contact.isStarred) {
                        Icon(Icons.Default.Star, null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp))
                    }
                }
                contact.phoneNumber?.let {
                    Text(it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ContactAvatar(contact: Contact, size: Int = 40) {
    val initials = contact.name.split(" ")
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    val colors = listOf(
        Color(0xFF1A3CA8), Color(0xFF7B3FA0), Color(0xFF007A6E),
        Color(0xFF8B2252), Color(0xFF2E7D32), Color(0xFFB85C00)
    )
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape)
            .background(colors[(contact.id % colors.size).toInt()]),
        contentAlignment = Alignment.Center
    ) {
        if (contact.photoUri != null) {
            Icon(Icons.Default.Person, null, tint = Color.White,
                modifier = Modifier.size((size * 0.6).dp))
        } else {
            Text(initials,
                style = if (size >= 44) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
