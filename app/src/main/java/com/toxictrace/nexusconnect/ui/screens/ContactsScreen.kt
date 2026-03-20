package com.toxictrace.nexusconnect.ui.screens

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.viewmodel.ContactSortMode
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

@Composable
fun ContactsScreen(viewModel: MainViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val selectedCount = contacts.count { it.isSelected }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = contacts.filter {
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
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Sort mode tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContactSortMode.values().forEach { mode ->
                    val selected = sortMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setSortMode(mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
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
                contentPadding = PaddingValues(bottom = if (selectedCount > 0) 80.dp else 16.dp)
            ) {
                items(filteredContacts, key = { it.id }) { contact ->
                    ContactItem(
                        contact = contact,
                        onToggle = { viewModel.toggleContactSelection(contact.id) }
                    )
                }
            }
        }

        // Bottom action bar when contacts selected
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
                        onClick = { /* trigger widget update */ },
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
private fun ContactItem(
    contact: Contact,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (contact.isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drag handle
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )

            // Checkbox
            Checkbox(
                checked = contact.isSelected,
                onCheckedChange = { onToggle() }
            )

            // Avatar
            ContactAvatar(contact = contact, size = 48)

            // Name + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (contact.isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                contact.phoneNumber?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ContactAvatar(contact: Contact, size: Int = 40) {
    // Initials avatar (real photo would use Coil AsyncImage)
    val initials = contact.name
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    val colors = listOf(
        Color(0xFF1A3CA8), Color(0xFF7B3FA0), Color(0xFF007A6E),
        Color(0xFF8B2252), Color(0xFF2E7D32), Color(0xFFB85C00)
    )
    val bgColor = colors[(contact.id % colors.size).toInt()]

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (contact.photoUri != null) {
            // TODO: replace with AsyncImage(coil) when available
            Icon(Icons.Default.Person, null, tint = Color.White,
                modifier = Modifier.size((size * 0.6).dp))
        } else {
            Text(
                initials,
                style = if (size >= 44) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
