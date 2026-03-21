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

    private val settingsRepo  = SettingsRepository(application)
    private val contactsRepo  = ContactsRepository(application)
    private val callLogRepo   = CallLogRepository(application)

    val settings: StateFlow<WidgetSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WidgetSettings())

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _sortMode    = MutableStateFlow(ContactSortMode.ALPHABETICAL)
    val sortMode: StateFlow<ContactSortMode> = _sortMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<List<Long>>(emptyList())

    val selectedCount: StateFlow<Int> = _selectedIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Contacts with selection flag, sorted by current mode
    val displayContacts: StateFlow<List<Contact>> = combine(_allContacts, _selectedIds) { all, ids ->
        all.map { c -> c.copy(isSelected = c.id in ids) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadContacts()
        loadSavedSelection()
    }

    fun loadContacts() {
        val hasContacts = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) return

        viewModelScope.launch {
            contactsRepo.observeContacts().collect { list ->
                _allContacts.value = sortList(list, _sortMode.value)
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
        if (contactId in current) current.remove(contactId) else current.add(contactId)
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
        viewModelScope.launch {
            when (mode) {
                ContactSortMode.ALPHABETICAL ->
                    _allContacts.update { sortList(it, mode) }
                ContactSortMode.FREQUENCY ->
                    _allContacts.update { it.sortedByDescending { c -> c.isStarred } }
                ContactSortMode.RECENTS ->
                    sortByRecents()
                ContactSortMode.MANUAL -> { }
            }
        }
    }

    private suspend fun sortByRecents() {
        val hasCallLog = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCallLog) return

        withContext(Dispatchers.IO) {
            val contacts = _allContacts.value
            // Build number → contactId map
            val numberMap = contacts
                .filter { it.phoneNumber != null }
                .associate { normalizeNumber(it.phoneNumber!!) to it.id }

            val recentIds = callLogRepo.getRecentContactIds(emptyList(), numberMap, 100)
            val recentSet = recentIds.toList()

            val sorted = contacts.sortedWith(compareBy { c ->
                val pos = recentSet.indexOf(c.id)
                if (pos == -1) Int.MAX_VALUE else pos
            })
            _allContacts.value = sorted
        }
    }

    private fun normalizeNumber(number: String) =
        number.replace(Regex("[\\s\\-().+]"), "").takeLast(7)

    private fun sortList(list: List<Contact>, mode: ContactSortMode) = when (mode) {
        ContactSortMode.ALPHABETICAL -> list.sortedBy { it.name }
        ContactSortMode.FREQUENCY    -> list.sortedByDescending { it.isStarred }
        ContactSortMode.RECENTS      -> list // handled separately
        ContactSortMode.MANUAL       -> list
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
