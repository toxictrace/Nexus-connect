package com.toxictrace.nexusconnect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toxictrace.nexusconnect.data.model.*
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val contactsRepo = ContactsRepository(application)

    val settings: StateFlow<WidgetSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WidgetSettings())

    // Contacts list with selection state
    private val _contacts = MutableStateFlow<List<Contact>>(contactsRepo.getMockContacts())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    // Contact sort mode
    private val _sortMode = MutableStateFlow(ContactSortMode.ALPHABETICAL)
    val sortMode: StateFlow<ContactSortMode> = _sortMode.asStateFlow()

    val selectedContacts: StateFlow<List<Contact>> = contacts.map { list ->
        list.filter { it.isSelected }.sortedBy { it.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSettings(update: (WidgetSettings) -> WidgetSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(update(settings.value))
        }
    }

    fun toggleContactSelection(contactId: Long) {
        _contacts.update { list ->
            list.map { c ->
                if (c.id == contactId) c.copy(isSelected = !c.isSelected) else c
            }
        }
    }

    fun reorderContact(from: Int, to: Int) {
        _contacts.update { list ->
            val mutable = list.toMutableList()
            val item = mutable.removeAt(from)
            mutable.add(to, item)
            mutable.mapIndexed { idx, c -> c.copy(sortOrder = idx) }
        }
    }

    fun setSortMode(mode: ContactSortMode) {
        _sortMode.value = mode
        when (mode) {
            ContactSortMode.ALPHABETICAL -> _contacts.update { it.sortedBy { c -> c.name } }
            ContactSortMode.FREQUENCY -> _contacts.update { it.sortedByDescending { c -> c.id } }
            ContactSortMode.MANUAL -> { /* respect sortOrder */ }
        }
    }

    fun loadRealContacts() {
        viewModelScope.launch {
            contactsRepo.getContacts().collect { list ->
                if (list.isNotEmpty()) _contacts.value = list
            }
        }
    }
}

enum class ContactSortMode(val label: String) {
    ALPHABETICAL("Alphabetical"),
    FREQUENCY("Frequency"),
    MANUAL("Manual Order")
}
