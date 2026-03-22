package com.toxictrace.nexusconnect.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toxictrace.nexusconnect.data.model.*
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import com.toxictrace.nexusconnect.ui.screens.AppInfo
import com.toxictrace.nexusconnect.widget.ContactWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val contactsRepo = ContactsRepository(application)

    val settings: StateFlow<WidgetSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WidgetSettings())

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _selectedIds = MutableStateFlow<List<Long>>(emptyList())

    // Installed apps cache — loaded once in background on startup
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()
    val appsLoading = MutableStateFlow(true)

    val selectedCount: StateFlow<Int> = _selectedIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val displayContacts: StateFlow<List<Contact>> = combine(_allContacts, _selectedIds) { all, ids ->
        val idSet = ids.toSet()
        val selectedMap = all.associateBy { it.id }
        val selectedOrdered = ids.mapNotNull { id -> selectedMap[id]?.copy(isSelected = true) }
        val unselected = all
            .filter { it.id !in idSet }
            .map { it.copy(isSelected = false) }
        selectedOrdered + unselected
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadContacts()
        loadSavedSelection()
        loadInstalledApps()
    }

    /** Load all installed apps in background — called once, result cached in StateFlow */
    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            // Use CATEGORY_LAUNCHER to get all apps with home screen icon
            // This works without QUERY_ALL_PACKAGES
            val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != getApplication<Application>().packageName }
                .mapNotNull { pkg ->
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(info).toString()
                        val iconBmp = runCatching {
                            pm.getApplicationIcon(pkg).toBitmap(48, 48).asImageBitmap()
                        }.getOrNull()
                        AppInfo(pkg, label, iconBmp)
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
            appsLoading.value = false
        }
    }

    fun loadContacts() {
        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        viewModelScope.launch {
            contactsRepo.observeContacts().collect { list ->
                _allContacts.value = list.sortedBy { it.name }
            }
        }
    }

    private fun loadSavedSelection() {
        viewModelScope.launch {
            _selectedIds.value = settingsRepo.getSelectedContactIds()
        }
    }

    fun toggleContactSelection(contactId: Long) {
        val current = _selectedIds.value.toMutableList()
        if (contactId in current) {
            current.remove(contactId)
        } else {
            val maxAllowed = settings.value.run { columns * tileHeightDp.coerceIn(3, 6) }
            if (current.size >= maxAllowed) return
            current.add(contactId)
        }
        _selectedIds.value = current
    }

    fun reorderSelected(from: Int, to: Int) {
        val current = _selectedIds.value.toMutableList()
        if (from < 0 || to < 0 || from >= current.size || to >= current.size) return
        val item = current.removeAt(from)
        current.add(to, item)
        _selectedIds.value = current
    }

    fun applyAndUpdateWidget() {
        viewModelScope.launch {
            settingsRepo.saveSelectedContactIds(_selectedIds.value)
            ContactWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun updateSettings(update: (WidgetSettings) -> WidgetSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(update(settings.value))
            ContactWidgetProvider.updateAllWidgets(getApplication())
        }
    }
}

enum class ContactSortMode(val label: String) {
    ALPHABETICAL("Alphabetical"),
    RECENTS("Recents"),
    FREQUENCY("Starred"),
    MANUAL("Manual Order")
}
