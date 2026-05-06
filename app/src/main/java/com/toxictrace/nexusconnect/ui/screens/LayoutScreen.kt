package com.toxictrace.nexusconnect.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

@Composable
fun LayoutScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    var draft by remember(settings) { mutableStateOf(settings) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard {
            SettingsSlider(
                label = stringResource(R.string.number_of_columns),
                value = draft.columns,
                valueRange = 3f..6f,
                steps = 2,
                startLabel = "3",
                endLabel = "6",
                onValueChange = { draft = draft.copy(columns = it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSlider(
                label = stringResource(R.string.number_of_rows),
                value = draft.tileHeightDp.coerceIn(3, 6),
                valueRange = 3f..6f,
                steps = 2,
                startLabel = "3",
                endLabel = "6",
                onValueChange = { draft = draft.copy(tileHeightDp = it) }
            )
        }

        Text(stringResource(R.string.filtering_mode),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp))

        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            FilterCheckItem(
                icon = { Icon(Icons.Default.Star, null) },
                title = stringResource(R.string.favorites),
                subtitle = stringResource(R.string.favorites_subtitle),
                checked = draft.filterFavorites,
                onCheckedChange = { draft = draft.copy(filterFavorites = it) }
            )
            HorizontalDivider()
            FilterCheckItem(
                icon = { Icon(Icons.Outlined.AccessTime, null) },
                title = stringResource(R.string.recents),
                subtitle = stringResource(R.string.recents_subtitle),
                checked = draft.filterRecents,
                onCheckedChange = { draft = draft.copy(filterRecents = it) }
            )
            if (draft.filterRecents) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.recents_days),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.recents_days_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dayLabels = listOf(
                            1 to stringResource(R.string.day_1),
                            3 to stringResource(R.string.day_3),
                            7 to stringResource(R.string.day_7),
                            0 to stringResource(R.string.day_unlimited)
                        )
                        dayLabels.forEach { (days, label) ->
                            FilterChip(
                                selected = draft.recentsDays == days,
                                onClick = { draft = draft.copy(recentsDays = days) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            FilterCheckItem(
                icon = { Icon(Icons.Default.BarChart, null) },
                title = stringResource(R.string.frequent),
                subtitle = stringResource(R.string.frequent_subtitle),
                checked = draft.filterFrequent,
                onCheckedChange = { draft = draft.copy(filterFrequent = it) }
            )
        }

        Text(stringResource(R.string.unknown_numbers),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp))

        SettingsCard(contentPadding = PaddingValues(0.dp)) {
            FilterCheckItem(
                icon = { Icon(Icons.Default.PhoneCallback, null) },
                title = stringResource(R.string.show_unknown_numbers),
                subtitle = stringResource(R.string.show_unknown_subtitle),
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
                        Text(stringResource(R.string.keep_for),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.keep_for_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dayLabels = listOf(
                            1 to stringResource(R.string.day_1),
                            3 to stringResource(R.string.day_3),
                            7 to stringResource(R.string.day_7),
                            0 to stringResource(R.string.day_unlimited)
                        )
                        dayLabels.forEach { (days, label) ->
                            FilterChip(
                                selected = draft.unknownNumbersDays == days,
                                onClick = { draft = draft.copy(unknownNumbersDays = days) },
                                label = { Text(label) },
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

        Button(
            onClick = { viewModel.updateSettings { draft } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(stringResource(R.string.apply_layout), style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(8.dp))
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
