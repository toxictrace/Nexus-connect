package com.toxictrace.nexusconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
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
        WidgetPreview(columns = draft.columns, rows = draft.tileHeightDp.coerceIn(3, 6))

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
                label = "Number of Rows",
                value = draft.tileHeightDp.coerceIn(3, 6),
                valueRange = 3f..6f,
                steps = 2,
                startLabel = "3 ROWS",
                endLabel = "6 ROWS",
                onValueChange = { draft = draft.copy(tileHeightDp = it) }
            )
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

        // Unknown numbers card
        Text(
            "Unknown Numbers",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            FilterCheckItem(
                icon = { Icon(Icons.Default.PhoneCallback, contentDescription = null) },
                title = "Show unknown numbers",
                subtitle = "Display recent calls from numbers not in contacts",
                checked = draft.showUnknownNumbers,
                onCheckedChange = { draft = draft.copy(showUnknownNumbers = it) }
            )
            if (draft.showUnknownNumbers) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep for", style = MaterialTheme.typography.titleMedium)
                        Text("Show unknown numbers only within this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 7).forEach { days ->
                            FilterChip(
                                selected = draft.unknownNumbersDays == days,
                                onClick = { draft = draft.copy(unknownNumbersDays = days) },
                                label = { Text("${days}d") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
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
private fun WidgetPreview(columns: Int, rows: Int) {
    SettingsCard {
        Text(
            "Preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(columns) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
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
