package com.toxictrace.nexusconnect.data.backup

import com.toxictrace.nexusconnect.data.model.AppTheme
import com.toxictrace.nexusconnect.data.model.AvatarIdentity
import com.toxictrace.nexusconnect.data.model.ClickAction
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupManagerTest {

    @Test
    fun settingsRoundTripPreservesAllPortableSettingsAndSelectedContacts() {
        val original = WidgetSettings(
            columns = 5,
            tileHeightDp = 6,
            maxContacts = 24,
            filterFavorites = false,
            filterRecents = false,
            filterFrequent = true,
            clickAction = ClickAction.DIRECT_CALL,
            hapticFeedback = false,
            theme = AppTheme.DARK,
            dynamicColors = false,
            accentColorIndex = 7,
            avatarIdentity = AvatarIdentity.CUSTOM,
            customAvatarUri = "content://com.example/avatar/42",
            showUnknownNumbers = false,
            unknownNumbersDays = 9,
            messengerWhatsApp = "com.whatsapp",
            messengerViber = "com.viber.voip",
            messengerTelegram = "org.telegram.messenger",
            showCallTypeIcon = false,
            callIconStyle = "GLASS",
            backupFolderUri = "content://device-specific/backup",
            language = "ru",
            recentsDays = 14,
            showCallLogButton = true,
            callLogAppPackage = "com.example.calllog"
        )
        val selectedIds = listOf(41L, 7L, 9001L)
        val selectedLookupKeys = listOf("lookup:first", "lookup,second")

        // JVM tests cannot load the Android Rust JNI library.
        // The encoded value is covered by Rust serialization tests.
        val json = JSONObject(BackupManager.settingsToJson(original, selectedIds, emptyList()))
            .put("selectedContactLookupKeys", "bG9va3VwOmZpcnN0,bG9va3VwLHNlY29uZA")
            .toString(2)
        val (restored, restoredIds, restoredLookupKeys) = BackupManager.jsonToSettings(json)

        assertEquals(original.copy(backupFolderUri = ""), restored)
        assertEquals(selectedIds, restoredIds)
        assertEquals(selectedLookupKeys, restoredLookupKeys)
    }

    @Test
    fun legacyBackupWithoutNewFieldsUsesCompatibleDefaults() {
        val legacyJson = """
            {
              "version": 1,
              "columns": 3,
              "tileHeightDp": 4,
              "selectedContactIds": "12,5"
            }
        """.trimIndent()

        val (restored, restoredIds, restoredLookupKeys) = BackupManager.jsonToSettings(legacyJson)

        assertEquals(3, restored.columns)
        assertEquals(4, restored.tileHeightDp)
        assertEquals(12, restored.maxContacts)
        assertEquals("MATERIAL", restored.callIconStyle)
        assertEquals("system", restored.language)
        assertEquals(3, restored.recentsDays)
        assertEquals(false, restored.showCallLogButton)
        assertEquals("", restored.callLogAppPackage)
        assertEquals(listOf(12L, 5L), restoredIds)
        assertEquals(emptyList<String>(), restoredLookupKeys)
    }
}
