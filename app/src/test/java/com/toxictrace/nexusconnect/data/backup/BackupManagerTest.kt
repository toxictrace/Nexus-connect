package com.toxictrace.nexusconnect.data.backup

import com.toxictrace.nexusconnect.data.model.AppTheme
import com.toxictrace.nexusconnect.data.model.AvatarIdentity
import com.toxictrace.nexusconnect.data.model.ClickAction
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupManagerTest {

    @Test
    fun settingsRoundTripPreservesAllPortableSettingsAndSelectedIds() {
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

        val json = BackupManager.settingsToJson(original, selectedIds)
        val (restored, restoredIds) = BackupManager.jsonToSettings(json)

        // The backup folder URI is intentionally device-specific and is restored by the caller.
        assertEquals(original.copy(backupFolderUri = ""), restored)
        assertEquals(selectedIds, restoredIds)
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

        val (restored, restoredIds) = BackupManager.jsonToSettings(legacyJson)

        assertEquals(3, restored.columns)
        assertEquals(4, restored.tileHeightDp)
        assertEquals(12, restored.maxContacts)
        assertEquals("MATERIAL", restored.callIconStyle)
        assertEquals("system", restored.language)
        assertEquals(3, restored.recentsDays)
        assertEquals(false, restored.showCallLogButton)
        assertEquals("", restored.callLogAppPackage)
        assertEquals(listOf(12L, 5L), restoredIds)
    }
}
