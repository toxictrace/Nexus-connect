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
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import com.toxictrace.nexusconnect.widget.ContactWidgetProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo  = SettingsRepository(application)
    private val contactsRepo  = ContactsRepository(application)

    val settings: StateFlow<WidgetSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WidgetSettings())

    // All contacts from system (live, via ContentObserver)
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _sortMode = MutableStateFlow(ContactSortMode.ALPHABETICAL)
    val sortMode: StateFlow<ContactSortMode> = _sortMode.asStateFlow()

    // Contacts the user has checked — in widget order
    private val _selectedIds = MutableStateFlow<List<Long>>(emptyList())

    val selectedCount: StateFlow<Int> = _selectedIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Merge contacts list with selection state and sort
    val displayContacts: StateFlow<List<Contact>> = combine(_contacts, _selectedIds) { all, ids ->
        all.map { c -> c.copy(isSelected = c.id in ids) }
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
                _contacts.value = sortList(list, _sortMode.value)
            }
        }
    }

    private fun loadSavedSelection() {
        viewModelScope.launch {
            val ids = settingsRepo.getSelectedContactIds()
            _selectedIds.value = ids
        }
    }

    fun toggleContactSelection(contactId: Long) {
        val current = _selectedIds.value.toMutableList()
        if (contactId in current) current.remove(contactId)
        else current.add(contactId)
        _selectedIds.value = current
    }

    fun reorderSelected(from: Int, to: Int) {
        val current = _selectedIds.value.toMutableList()
        if (from < 0 || to < 0 || from >= current.size || to >= current.size) return
        val item = current.removeAt(from)
        current.add(to, item)
        _selectedIds.value = current
    }

    fun setSortMode(mode: ContactSortMode) {
        _sortMode.value = mode
        _contacts.update { sortList(it, mode) }
    }

    private fun sortList(list: List<Contact>, mode: ContactSortMode): List<Contact> = when (mode) {
        ContactSortMode.ALPHABETICAL -> list.sortedBy { it.name }
        ContactSortMode.FREQUENCY    -> list.sortedByDescending { it.isStarred }
        ContactSortMode.MANUAL       -> list
    }

    /** Save selection to DataStore and update the widget */
    fun applyAndUpdateWidget() {
        viewModelScope.launch {
            settingsRepo.saveSelectedContactIds(_selectedIds.value)
            ContactWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun updateSettings(update: (WidgetSettings) -> WidgetSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(update(settings.value))
            // Also refresh widget when layout settings change
            ContactWidgetProvider.updateAllWidgets(getApplication())
        }
    }
}

enum class ContactSortMode(val label: String) {
    ALPHABETICAL("Alphabetical"),
    FREQUENCY("Frequency"),
    MANUAL("Manual Order")
}
