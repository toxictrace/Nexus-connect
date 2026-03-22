package com.toxictrace.nexusconnect.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

@Composable
fun ContactsScreen(viewModel: MainViewModel) {
    val context       = LocalContext.current
    val contacts      by viewModel.displayContacts.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val searchQuery   by viewModel.searchQuery.collectAsState()
    val photoCache    by viewModel.photoCache.collectAsState()

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

    // Preload photos when contacts list is ready
    LaunchedEffect(contacts) {
        if (contacts.isNotEmpty()) viewModel.preloadPhotos(contacts)
    }

    val selected   = contacts.filter { it.isSelected }
    val unselected = contacts.filter { !it.isSelected }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Search bar with clear button
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Find contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
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
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = if (selectedCount > 0) 88.dp else 16.dp
                )
            ) {
                // ── Selected contacts ──
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
                    val onToggle  = remember(contact.id) { { viewModel.toggleContactSelection(contact.id) } }
                    val onMoveUp  = remember(idx) { { if (idx > 0) viewModel.reorderSelected(idx, idx - 1) } }
                    val onMoveDown = remember(idx, selected.size) { { if (idx < selected.lastIndex) viewModel.reorderSelected(idx, idx + 1) } }
                    SelectedContactItem(
                        contact    = contact,
                        position   = idx,
                        total      = selected.size,
                        photoCache = photoCache,
                        onToggle   = onToggle,
                        onMoveUp   = onMoveUp,
                        onMoveDown = onMoveDown
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
                items(unselected, key = { c -> "uns_${c.id}" }) { contact ->
                    val onToggle = remember(contact.id) { { viewModel.toggleContactSelection(contact.id) } }
                    UnselectedContactItem(contact = contact, onToggle = onToggle, photoCache = photoCache)
                }
            }
        }

        // Bottom action bar
        if (selectedCount > 0) {
            val settings by viewModel.settings.collectAsState()
            val maxAllowed = settings.columns * settings.tileHeightDp.coerceIn(3, 6)

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
                        Text("Selected $selectedCount / $maxAllowed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("WIDGET ORDER",
                            style = MaterialTheme.typography.labelSmall,
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

@androidx.compose.runtime.NonRestartableComposable
@Composable
private fun SelectedContactItem(
    contact: Contact,
    position: Int,
    total: Int,
    photoCache: Map<Long, android.graphics.Bitmap> = emptyMap(),
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("${position + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold)
        }

        ContactAvatar(contact = contact, size = 44, photoCache = photoCache)

        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            contact.phoneNumber?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }

        IconButton(onClick = onMoveUp, enabled = position > 0,
            modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMoveDown, enabled = position < total - 1,
            modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
        }
    }
}

@androidx.compose.runtime.NonRestartableComposable
@Composable
private fun UnselectedContactItem(
    contact: Contact,
    onToggle: () -> Unit,
    photoCache: Map<Long, android.graphics.Bitmap> = emptyMap()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(checked = false, onCheckedChange = { onToggle() })
        ContactAvatar(contact = contact, size = 46, photoCache = photoCache)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (contact.isStarred) {
                    Icon(Icons.Default.Star, null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(13.dp))
                }
            }
            contact.phoneNumber?.let {
                Text(it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
    }
}

@androidx.compose.runtime.NonRestartableComposable
@Composable
fun ContactAvatar(
    contact: Contact,
    size: Int = 40,
    photoCache: Map<Long, android.graphics.Bitmap> = emptyMap()
) {
    val colors = remember {
        listOf(
            Color(0xFF1A3CA8), Color(0xFF7B3FA0), Color(0xFF007A6E),
            Color(0xFF8B2252), Color(0xFF2E7D32), Color(0xFFB85C00)
        )
    }
    val bg = colors[(contact.id % colors.size).toInt()]
    val cachedBitmap = photoCache[contact.id]

    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            cachedBitmap != null -> {
                // Preloaded — zero IO, instant draw
                androidx.compose.foundation.Image(
                    bitmap = cachedBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            contact.photoUri != null -> {
                // Not yet cached — AsyncImage while loading
                val ctx = LocalContext.current
                val req = remember(contact.id) {
                    ImageRequest.Builder(ctx)
                        .data(contact.photoUri)
                        .memoryCacheKey("avatar_${contact.id}")
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(bg))
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxSize(0.65f)
                )
            }
        }
    }
}
