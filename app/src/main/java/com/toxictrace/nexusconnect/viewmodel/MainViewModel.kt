package com.toxictrace.nexusconnect.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
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
import com.toxictrace.nexusconnect.util.AppLogger
import com.toxictrace.nexusconnect.widget.ContactWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val contactsRepo = ContactsRepository(application)

    val settings: StateFlow<WidgetSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WidgetSettings())

    private val _allContacts  = MutableStateFlow<List<Contact>>(emptyList())
    private val _selectedIds  = MutableStateFlow<List<Long>>(emptyList())
    private val _searchQuery  = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()
    val appsLoading = MutableStateFlow(true)

    val selectedCount: StateFlow<Int> = combine(_selectedIds, _allContacts) { ids, all ->
        val allIds = all.map { it.id }.toSet()
        ids.count { it in allIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val displayContacts: StateFlow<List<Contact>> = combine(
        _allContacts, _selectedIds, _searchQuery.debounce(120)
    ) { all, ids, query ->
        val idSet = ids.toSet()
        val selectedMap = all.filter { it.id in idSet }.associateBy { it.id }
        val selectedOrdered = ids.mapNotNull { id ->
            selectedMap[id]?.let { if (it.isSelected) it else it.copy(isSelected = true) }
        }
        val unselected = all.filter { it.id !in idSet }.let { list ->
            if (query.isBlank()) list
            else list.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.phoneNumber?.contains(query) == true
            }
        }
        selectedOrdered + unselected
    }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        AppLogger.i("MainViewModel", "init")
        loadContacts()
        loadSavedSelection()
        loadInstalledApps()
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.i("MainViewModel", "loadInstalledApps started")
            try {
                val pm = getApplication<Application>().packageManager
                val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
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
                AppLogger.i("MainViewModel", "loadInstalledApps done: ${apps.size} apps")
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "loadInstalledApps failed", e)
                appsLoading.value = false
            }
        }
    }

    fun loadContacts() {
        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            AppLogger.w("MainViewModel", "loadContacts: READ_CONTACTS permission not granted")
            return
        }
        viewModelScope.launch {
            AppLogger.i("MainViewModel", "loadContacts: starting observer")
            try {
                contactsRepo.observeContacts()
                    .distinctUntilChanged()
                    .collect { list ->
                        AppLogger.i("MainViewModel", "contacts updated: ${list.size} contacts")
                        _allContacts.value = list
                    }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "loadContacts failed", e)
            }
        }
    }

    private fun loadSavedSelection() {
        viewModelScope.launch {
            try {
                _selectedIds.value = settingsRepo.getSelectedContactIds()
                AppLogger.i("MainViewModel", "loadSavedSelection: ${_selectedIds.value.size} ids")
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "loadSavedSelection failed", e)
            }
        }
    }

    fun toggleContactSelection(contactId: Long) {
        val current = _selectedIds.value.toMutableList()
        val maxTiles = settings.value.columns * settings.value.tileHeightDp
        if (contactId in current) {
            current.remove(contactId)
            AppLogger.i("MainViewModel", "contact deselected: id=$contactId")
        } else {
            if (current.size >= maxTiles) {
                AppLogger.w("MainViewModel", "toggleContactSelection: max tiles reached ($maxTiles)")
                return
            }
            current.add(contactId)
            AppLogger.i("MainViewModel", "contact selected: id=$contactId")
        }
        _selectedIds.value = current
        viewModelScope.launch {
            settingsRepo.saveSelectedContactIds(current)
            ContactWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun reorderSelected(fromIndex: Int, toIndex: Int) {
        val current = _selectedIds.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _selectedIds.value = current
        AppLogger.i("MainViewModel", "reorderSelected: $fromIndex -> $toIndex")
        viewModelScope.launch {
            settingsRepo.saveSelectedContactIds(current)
            ContactWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun applyAndUpdateWidget() {
        AppLogger.i("MainViewModel", "applyAndUpdateWidget")
        viewModelScope.launch {
            ContactWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun updateSettings(update: (WidgetSettings) -> WidgetSettings) {
        viewModelScope.launch {
            try {
                settingsRepo.updateSettings(update(settings.value))
                ContactWidgetProvider.updateAllWidgets(getApplication())
                AppLogger.i("MainViewModel", "settings updated")
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "updateSettings failed", e)
            }
        }
    }

    fun saveBackup(onResult: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                AppLogger.i("MainViewModel", "saveBackup started")
                val folderUri = android.net.Uri.parse(settings.value.backupFolderUri)
                val selectedIds = settingsRepo.getSelectedContactIds()
                val json = com.toxictrace.nexusconnect.data.backup.BackupManager
                    .settingsToJson(settings.value, selectedIds)
                val name = com.toxictrace.nexusconnect.data.backup.BackupManager
                    .saveBackup(getApplication(), folderUri, json)
                AppLogger.i("MainViewModel", "saveBackup success: $name")
                onResult(name)
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "saveBackup failed", e)
                onError(e.message ?: "Backup failed")
            }
        }
    }

    fun restoreBackup(fileUri: android.net.Uri, onResult: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                AppLogger.i("MainViewModel", "restoreBackup started: $fileUri")
                val json = com.toxictrace.nexusconnect.data.backup.BackupManager
                    .loadBackup(getApplication(), fileUri)
                val (restoredSettings, restoredIds) = com.toxictrace.nexusconnect.data.backup.BackupManager
                    .jsonToSettings(json)
                val folderUri = settings.value.backupFolderUri
                settingsRepo.updateSettings(restoredSettings.copy(backupFolderUri = folderUri))
                settingsRepo.saveSelectedContactIds(restoredIds)
                _selectedIds.value = restoredIds
                ContactWidgetProvider.updateAllWidgets(getApplication())
                AppLogger.i("MainViewModel", "restoreBackup success: ${restoredIds.size} contacts restored")
                onResult()
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "restoreBackup failed", e)
                onError(e.message ?: "Restore failed")
            }
        }
    }

    fun listBackups(): List<Pair<String, android.net.Uri>> {
        val folderUriStr = settings.value.backupFolderUri
        if (folderUriStr.isBlank()) return emptyList()
        return try {
            com.toxictrace.nexusconnect.data.backup.BackupManager
                .listBackups(getApplication(), android.net.Uri.parse(folderUriStr))
        } catch (e: Exception) {
            AppLogger.e("MainViewModel", "listBackups failed", e)
            emptyList()
        }
    }

    fun saveLog(onResult: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                AppLogger.i("MainViewModel", "saveLog requested")
                val folderUri = android.net.Uri.parse(settings.value.backupFolderUri)
                val name = AppLogger.exportToFolder(getApplication(), folderUri)
                AppLogger.i("MainViewModel", "saveLog success: $name")
                onResult(name)
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "saveLog failed", e)
                onError(e.message ?: "Export failed")
            }
        }
    }

    // ── Photo preload cache ───────────────────────────────────────────────────

    private val _photoCache = MutableStateFlow<Map<Long, android.graphics.Bitmap>>(emptyMap())
    val photoCache: StateFlow<Map<Long, android.graphics.Bitmap>> = _photoCache.asStateFlow()

    private var photoCacheJob: kotlinx.coroutines.Job? = null

    fun preloadPhotos(contacts: List<Contact>) {
        photoCacheJob?.cancel()
        photoCacheJob = viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val newCache = _photoCache.value.toMutableMap()
            contacts.filter { it.photoUri != null && it.id !in newCache }.forEach { contact ->
                if (!isActive) return@launch
                try {
                    val uri = android.net.Uri.parse(contact.photoUri)
                    ctx.contentResolver.openInputStream(uri)?.use { stream ->
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                            ?.let { bmp -> newCache[contact.id] = bmp }
                    }
                } catch (e: Exception) {
                    AppLogger.w("MainViewModel", "preloadPhotos: failed for contact ${contact.id}: ${e.message}")
                }
            }
            _photoCache.value = newCache
        }
    }
}
