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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val contactsRepo = ContactsRepository(application)

    val settings: StateFlow<WidgetSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WidgetSettings())

    private val _allContacts  = MutableStateFlow<List<Contact>>(emptyList())
    val allContacts: StateFlow<List<Contact>> = _allContacts.asStateFlow()
    private val _selectedIds  = MutableStateFlow<List<Long>>(emptyList())
    private val _searchQuery  = MutableStateFlow("")
    private val selectionSaveMutex = Mutex()
    private val settingsUpdateMutex = Mutex()
    private var contactsObserverJob: Job? = null

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
        ContactDisplayList.build(all, ids, query)
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
        if (contactsObserverJob?.isActive == true) {
            AppLogger.i("MainViewModel", "loadContacts: observer already active")
            return
        }

        contactsObserverJob = viewModelScope.launch {
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
                // Serialize startup loading with restore and backup operations. Otherwise,
                // a delayed startup read could publish IDs from before a restore completed.
                settingsUpdateMutex.withLock {
                    selectionSaveMutex.withLock {
                        // Read persisted settings directly: settings.value may still contain
                        // its initial defaults before the StateFlow has observed DataStore.
                        val currentSettings = settingsRepo.settings.first()
                        val saved = settingsRepo.getSelectedContactIds()
                        val maxTiles = currentSettings.columns * currentSettings.tileHeightDp
                        val trimmed = SelectionLimit.trim(saved, maxTiles)
                        if (trimmed.size != saved.size) {
                            AppLogger.w("MainViewModel", "loadSavedSelection: trimming ${saved.size} ids to maxTiles=$maxTiles")
                            settingsRepo.saveSelectedContactIds(trimmed)
                        }
                        _selectedIds.value = trimmed
                        AppLogger.i("MainViewModel", "loadSavedSelection: ${trimmed.size} ids maxTiles=$maxTiles")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "loadSavedSelection failed", e)
            }
        }
    }

    fun toggleContactSelection(contactId: Long) {
        viewModelScope.launch {
            try {
                // Serialize selection changes with settings updates and restore. Read the
                // persisted layout because settings.value may briefly lag behind DataStore.
                settingsUpdateMutex.withLock {
                    selectionSaveMutex.withLock {
                        val currentSettings = settingsRepo.settings.first()
                        val maxTiles = currentSettings.columns * currentSettings.tileHeightDp
                        val current = _selectedIds.value
                        val updated = ContactSelectionOperations.toggle(current, contactId, maxTiles)
                        if (updated == null) {
                            AppLogger.w("MainViewModel", "toggleContactSelection: max tiles reached ($maxTiles)")
                            return@withLock
                        }

                        if (contactId in current) {
                            AppLogger.i("MainViewModel", "contact deselected: id=$contactId")
                        } else {
                            AppLogger.i("MainViewModel", "contact selected: id=$contactId")
                        }
                        _selectedIds.value = updated
                        settingsRepo.saveSelectedContactIds(updated)
                        ContactWidgetProvider.updateAllWidgets(getApplication())
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "toggleContactSelection failed", e)
            }
        }
    }

    /**
     * Move a selected contact by its stable ID, not by a UI index that can
     * change after recomposition or another quick tap.
     * direction: -1 moves up, +1 moves down.
     */
    fun moveSelected(contactId: Long, direction: Int) {
        viewModelScope.launch {
            try {
                // Read and update the latest selection only after obtaining the same lock
                // used by toggles, startup loading, and backup restore.
                selectionSaveMutex.withLock {
                    val current = _selectedIds.value
                    val updated = ContactSelectionOperations.move(current, contactId, direction)
                    if (updated == null) {
                        AppLogger.w(
                            "MainViewModel",
                            "moveSelected ignored: id=$contactId direction=$direction cannot be applied"
                        )
                    } else {
                        val fromIndex = current.indexOf(contactId)
                        val toIndex = fromIndex + direction
                        _selectedIds.value = updated
                        settingsRepo.saveSelectedContactIds(updated)
                        ContactWidgetProvider.updateAllWidgets(getApplication())
                        AppLogger.i(
                            "MainViewModel",
                            "moveSelected: id=$contactId $fromIndex->$toIndex direction=$direction order=${updated.joinToString(",")}"
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "moveSelected failed", e)
            }
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
                settingsUpdateMutex.withLock {
                    // Read the latest persisted values after acquiring the lock.
                    // settings.value may not have observed the previous write yet.
                    val latestSettings = settingsRepo.settings.first()
                    settingsRepo.updateSettings(update(latestSettings))
                }
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
                // Snapshot settings and selected IDs under the same locks used by
                // settings updates and selection writes. A backup must not capture
                // the middle of a concurrent restore.
                val (currentSettings, json) = settingsUpdateMutex.withLock {
                    selectionSaveMutex.withLock {
                        val persistedSettings = settingsRepo.settings.first()
                        val selectedIds = settingsRepo.getSelectedContactIds()
                        persistedSettings to com.toxictrace.nexusconnect.data.backup.BackupManager
                            .settingsToJson(persistedSettings, selectedIds)
                    }
                }
                val folderUri = android.net.Uri.parse(currentSettings.backupFolderUri)
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
                val (restoredSettings, backupIds) = com.toxictrace.nexusconnect.data.backup.BackupManager
                    .jsonToSettings(json)
                // Keep restored selection within the capacity defined by the restored layout,
                // matching the limit applied when loading a saved selection at startup.
                val maxTiles = restoredSettings.columns * restoredSettings.tileHeightDp
                val restoredIds = SelectionLimit.trim(backupIds, maxTiles)
                // Use the same lock order as saveBackup: settings first, then selection.
                // This makes the restored settings and IDs appear as one consistent snapshot
                // to backup creation and prevents stale selection writes from winning.
                settingsUpdateMutex.withLock {
                    selectionSaveMutex.withLock {
                        val currentSettings = settingsRepo.settings.first()
                        settingsRepo.updateSettings(
                            restoredSettings.copy(backupFolderUri = currentSettings.backupFolderUri)
                        )
                        settingsRepo.saveSelectedContactIds(restoredIds)
                        _selectedIds.value = restoredIds
                    }
                }
                ContactWidgetProvider.updateAllWidgets(getApplication())
                if (restoredIds.size != backupIds.size) {
                    AppLogger.w(
                        "MainViewModel",
                        "restoreBackup: trimmed ${backupIds.size} selected ids to maxTiles=$maxTiles"
                    )
                }
                AppLogger.i("MainViewModel", "restoreBackup success: ${restoredIds.size} contacts restored")
                onResult()
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "restoreBackup failed", e)
                onError(e.message ?: "Restore failed")
            }
        }
    }

    suspend fun listBackups(): List<Pair<String, android.net.Uri>> {
        return try {
            // Read the persisted folder under the settings lock. The StateFlow may briefly
            // lag behind a completed folder-selection write.
            val folderUriStr = settingsUpdateMutex.withLock {
                settingsRepo.settings.first().backupFolderUri
            }
            if (folderUriStr.isBlank()) return emptyList()
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
                // Read the persisted folder after acquiring the same lock used by settings
                // updates. The StateFlow can briefly lag behind a completed DataStore write.
                val folderUri = settingsUpdateMutex.withLock {
                    android.net.Uri.parse(settingsRepo.settings.first().backupFolderUri)
                }
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
    private val _photoUris = MutableStateFlow<Map<Long, String>>(emptyMap())

    private var photoCacheJob: kotlinx.coroutines.Job? = null

    fun preloadPhotos(contacts: List<Contact>) {
        photoCacheJob?.cancel()
        photoCacheJob = viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val currentCache = _photoCache.value
            val plan = PhotoCachePlan.build(contacts, currentCache.keys, _photoUris.value)
            val newCache = currentCache
                .filterKeys { it in plan.retainedIds && it !in plan.idsToReload }
                .toMutableMap()
            val newUris = _photoUris.value
                .filter { (id, uri) -> id in plan.retainedIds && contacts.any { it.id == id && it.photoUri == uri } }
                .toMutableMap()

            plan.contactsToLoad.forEach { contact ->
                if (!isActive) return@launch
                try {
                    val photoUri = contact.photoUri ?: return@forEach
                    val uri = android.net.Uri.parse(photoUri)
                    val bitmap = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                    }
                    if (bitmap != null) {
                        newCache[contact.id] = bitmap
                        newUris[contact.id] = photoUri
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("MainViewModel", "preloadPhotos: failed for contact ${contact.id}: ${e.message}")
                }
            }
            if (isActive) {
                _photoCache.value = newCache
                _photoUris.value = newUris
            }
        }
    }
}
