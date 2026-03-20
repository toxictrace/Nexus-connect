package com.toxictrace.nexusconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

@Composable
fun LayoutScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    // Local draft state - applied only on button press
    var draft by remember(settings) { mutableStateOf(settings) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Widget preview
        WidgetPreview(columns = draft.columns, maxItems = draft.maxContacts)

        // Columns + Tile Height card
        SettingsCard {
            SettingsSlider(
                label = "Number of Columns",
                value = draft.columns,
                valueRange = 3f..6f,
                steps = 2,
                startLabel = "3 COLUMNS",
                endLabel = "6 COLUMNS",
                onValueChange = { draft = draft.copy(columns = it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSlider(
                label = "Tile Height (dp)",
                value = draft.tileHeightDp,
                valueRange = 56f..100f,
                steps = 8,
                startLabel = "COMPACT",
                endLabel = "SPACIOUS",
                onValueChange = { draft = draft.copy(tileHeightDp = it) }
            )
        }

        // Max Contacts card
        SettingsCard {
            SettingsSlider(
                label = "Max Contacts",
                value = draft.maxContacts,
                valueRange = 4f..24f,
                steps = 19,
                startLabel = "4 ITEMS",
                endLabel = "24 ITEMS",
                onValueChange = { draft = draft.copy(maxContacts = it) }
            )

            // Info banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Adjusting the limit will automatically paginate your widget tiles if the grid is full.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Filtering Mode card
        Text(
            "Filtering Mode",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp)
        )

        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            FilterCheckItem(
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                title = "Favorites",
                subtitle = "Pinned and starred contacts",
                checked = draft.filterFavorites,
                onCheckedChange = { draft = draft.copy(filterFavorites = it) }
            )
            HorizontalDivider()
            FilterCheckItem(
                icon = { Icon(Icons.Outlined.AccessTime, contentDescription = null) },
                title = "Recents",
                subtitle = "Last interacted or messaged",
                checked = draft.filterRecents,
                onCheckedChange = { draft = draft.copy(filterRecents = it) }
            )
            HorizontalDivider()
            FilterCheckItem(
                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                title = "Frequent",
                subtitle = "Prioritize by engagement score",
                checked = draft.filterFrequent,
                onCheckedChange = { draft = draft.copy(filterFrequent = it) }
            )
        }

        // Apply button
        Button(
            onClick = { viewModel.updateSettings { draft } },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Apply Layout Changes", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WidgetPreview(columns: Int, maxItems: Int) {
    val itemCount = minOf(maxItems, columns * 2)
    SettingsCard {
        Text(
            "Preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val rows = (itemCount + columns - 1) / columns
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(rows) { rowIdx ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(columns) { colIdx ->
                        val idx = rowIdx * columns + colIdx
                        val isAdd = idx == columns // highlight add button like screenshot
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isAdd) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAdd) {
                                Text("+", style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    startLabel: String,
    endLabel: String,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(endLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterCheckItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsCard(
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
