package com.toxictrace.nexusconnect.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toxictrace.nexusconnect.data.model.*
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.data.repository.CallLogRepository
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import com.toxictrace.nexusconnect.widget.ContactWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val contactsRepo = ContactsRepository(application)
    private val callLogRepo  = CallLogRepository(application)

    val settings: StateFlow<WidgetSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WidgetSettings())

    // All contacts from system, sorted alphabetically
    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())

    // Selected contact IDs in user-defined order
    private val _selectedIds = MutableStateFlow<List<Long>>(emptyList())

    val selectedCount: StateFlow<Int> = _selectedIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Display list:
     * 1. Selected contacts in user-defined order (top)
     * 2. Unselected contacts alphabetically (below)
     */
    val displayContacts: StateFlow<List<Contact>> = combine(_allContacts, _selectedIds) { all, ids ->
        val idSet = ids.toSet()
        val selectedMap = all.associateBy { it.id }
        val selectedOrdered = ids.mapNotNull { id -> selectedMap[id]?.copy(isSelected = true) }
        val unselected = all
            .filter { it.id !in idSet }
            .map { it.copy(isSelected = false) }
            .sortedBy { it.name }
        selectedOrdered + unselected
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadContacts()
        loadSavedSelection()
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
            // Limit to cols × rows
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

// Kept for potential future use
enum class ContactSortMode(val label: String) {
    ALPHABETICAL("Alphabetical"),
    RECENTS("Recents"),
    FREQUENCY("Starred"),
    MANUAL("Manual Order")
}
