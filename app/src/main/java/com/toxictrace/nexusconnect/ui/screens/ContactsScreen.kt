package com.toxictrace.nexusconnect.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

@Composable
fun ContactsScreen(viewModel: MainViewModel) {
    val context       = LocalContext.current
    val contacts      by viewModel.displayContacts.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    var searchQuery   by remember { mutableStateOf("") }

    var hasContacts by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasContacts = results[Manifest.permission.READ_CONTACTS] == true
        if (hasContacts) viewModel.loadContacts()
    }

    LaunchedEffect(hasContacts) {
        if (hasContacts) viewModel.loadContacts()
        else permLauncher.launch(arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG
        ))
    }

    // Split: selected (in order) on top, unselected below
    val selected   = contacts.filter { it.isSelected }
    val unselected = contacts.filter { !it.isSelected }.let { list ->
        if (searchQuery.isBlank()) list
        else list.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Drag state for selected contacts reordering
    var dragFromIndex by remember { mutableStateOf(-1) }
    var dragToIndex   by remember { mutableStateOf(-1) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Search bar — only affects unselected list
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

            if (!hasContacts) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Contacts permission required.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            permLauncher.launch(arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_CALL_LOG
                            ))
                        }) { Text("Grant") }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = if (selectedCount > 0) 88.dp else 16.dp
                )
            ) {
                // ── Selected contacts (draggable) ──
                if (selected.isNotEmpty()) {
                    item {
                        Text(
                            "SELECTED · ${selected.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                itemsIndexed(selected, key = { _, c -> "sel_${c.id}" }) { idx, contact ->
                    val isDragging = dragFromIndex == idx
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 1.dp)

                    ContactItem(
                        contact    = contact,
                        isSelected = true,
                        showDrag   = true,
                        elevation  = elevation,
                        onToggle   = { viewModel.toggleContactSelection(contact.id) },
                        onDragStart = { dragFromIndex = idx; dragToIndex = idx },
                        onDrag     = { delta ->
                            // Simple index shift by pixel delta
                            val newIdx = (dragToIndex + if (delta > 30f) 1 else if (delta < -30f) -1 else 0)
                                .coerceIn(0, selected.lastIndex)
                            if (newIdx != dragToIndex) {
                                viewModel.reorderSelected(dragToIndex, newIdx)
                                dragToIndex = newIdx
                            }
                        },
                        onDragEnd  = { dragFromIndex = -1; dragToIndex = -1 }
                    )
                }

                // ── Divider ──
                if (selected.isNotEmpty() && unselected.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "ALL CONTACTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // ── Unselected contacts ──
                itemsIndexed(unselected, key = { _, c -> "uns_${c.id}" }) { _, contact ->
                    ContactItem(
                        contact    = contact,
                        isSelected = false,
                        showDrag   = false,
                        elevation  = 1.dp,
                        onToggle   = { viewModel.toggleContactSelection(contact.id) }
                    )
                }
            }
        }

        // Bottom bar
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
                        Text("Selected $selectedCount / ${contacts.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("WIDGET ORDER", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { viewModel.applyAndUpdateWidget() },
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Update Widget") }
                }
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: Contact,
    isSelected: Boolean,
    showDrag: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(12.dp))
            .zIndex(if (isSelected) 1f else 0f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drag handle — only for selected
            if (showDrag) {
                Icon(
                    Icons.Default.DragHandle, null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(22.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { _, offset -> onDrag(offset.y) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd
                            )
                        }
                )
            } else {
                Spacer(Modifier.size(22.dp))
            }

            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })

            ContactAvatar(contact = contact, size = 46)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (contact.isStarred) {
                        Icon(Icons.Default.Star, null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(13.dp))
                    }
                }
                contact.phoneNumber?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
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
    val bg = colors[(contact.id % colors.size).toInt()]

    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(initials,
                style = if (size >= 44) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                color = Color.White, fontWeight = FontWeight.Bold)
        }
        if (contact.photoUri != null) {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = contact.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
