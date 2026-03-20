package com.nexusconnect.widget.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nexusconnect.widget.data.models.ContactModel
import com.nexusconnect.widget.data.models.WidgetSettings
import com.nexusconnect.widget.data.repository.ContactsRepository
import com.nexusconnect.widget.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val contactsRepo = ContactsRepository(application)

    private val _settings = MutableLiveData(settingsRepo.getSettings())
    val settings: LiveData<WidgetSettings> = _settings

    private val _allContacts = MutableLiveData<List<ContactModel>>(emptyList())
    val allContacts: LiveData<List<ContactModel>> = _allContacts

    private val _selectedContactIds = MutableLiveData<List<String>>(settingsRepo.getSelectedContactIds())
    val selectedContactIds: LiveData<List<String>> = _selectedContactIds

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    init {
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val contacts = contactsRepo.getAllContacts()
            _allContacts.postValue(contacts)
        }
    }

    fun updateSettings(settings: WidgetSettings) {
        _settings.value = settings
        settingsRepo.saveSettings(settings)
    }

    fun toggleContactSelection(contactId: String) {
        val current = _selectedContactIds.value?.toMutableList() ?: mutableListOf()
        if (current.contains(contactId)) {
            current.remove(contactId)
        } else {
            current.add(contactId)
        }
        _selectedContactIds.value = current
        settingsRepo.saveSelectedContactIds(current)
    }

    fun reorderContacts(fromPos: Int, toPos: Int) {
        val ids = _selectedContactIds.value?.toMutableList() ?: return
        if (fromPos < 0 || toPos < 0 || fromPos >= ids.size || toPos >= ids.size) return
        val item = ids.removeAt(fromPos)
        ids.add(toPos, item)
        _selectedContactIds.value = ids
        settingsRepo.saveSelectedContactIds(ids)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredContacts(): List<ContactModel> {
        val all = _allContacts.value ?: emptyList()
        val query = _searchQuery.value ?: ""
        return if (query.isBlank()) all
        else all.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
    }

    fun exportSettings(): String = settingsRepo.exportToJson()

    fun importSettings(json: String): Boolean {
        val result = settingsRepo.importFromJson(json)
        if (result) {
            _settings.value = settingsRepo.getSettings()
            _selectedContactIds.value = settingsRepo.getSelectedContactIds()
        }
        return result
    }
}
