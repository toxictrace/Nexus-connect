package com.toxictrace.nexusconnect.data.backup

import com.toxictrace.nexusconnect.data.model.AppTheme
import com.toxictrace.nexusconnect.data.model.AvatarIdentity
import com.toxictrace.nexusconnect.data.model.ClickAction
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        val json = JSONObject(BackupManager.settingsToJson(original, emptyList(), emptyList()))
            .put("selectedContactIds", selectedIds.joinToString(","))
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

    @Test
    fun backupJsonKeepsVersionThreePortableSchema() {
        val json = JSONObject(
            BackupManager.settingsToJson(WidgetSettings(), emptyList(), emptyList())
        )

        assertEquals(3, json.getInt("version"))
        assertEquals(
            setOf(
                "version",
                "columns",
                "tileHeightDp",
                "maxContacts",
                "filterFavorites",
                "filterRecents",
                "filterFrequent",
                "clickAction",
                "hapticFeedback",
                "theme",
                "dynamicColors",
                "accentColorIndex",
                "avatarIdentity",
                "customAvatarUri",
                "showUnknownNumbers",
                "unknownNumbersDays",
                "messengerWhatsApp",
                "messengerViber",
                "messengerTelegram",
                "showCallTypeIcon",
                "callIconStyle",
                "language",
                "recentsDays",
                "showCallLogButton",
                "callLogAppPackage",
                "selectedContactIds",
                "selectedContactLookupKeys"
            ),
            json.keySet()
        )
        assertFalse(json.has("backupFolderUri"))
    }

    @Test
    fun malformedEnumsAndSelectedIdsUseExistingFallbackRules() {
        val json = """
            {
              "version": 3,
              "clickAction": "UNKNOWN_ACTION",
              "theme": "UNKNOWN_THEME",
              "avatarIdentity": "UNKNOWN_AVATAR",
              "selectedContactIds": "10,invalid,-2,9223372036854775808",
              "selectedContactLookupKeys": "bG9va3Vw,bad!"
            }
        """.trimIndent()

        val (restored, ids, lookupKeys) = BackupManager.jsonToSettings(json)

        assertEquals(ClickAction.SHOW_DIALOG, restored.clickAction)
        assertEquals(AppTheme.LIGHT, restored.theme)
        assertEquals(AvatarIdentity.DEFAULT, restored.avatarIdentity)
        assertEquals(listOf(10L, -2L), ids)
        assertEquals(listOf("lookup"), lookupKeys)
    }

    @Test
    fun zipRoundTripPreservesTheSettingsEntryAndPayloadBytes() {
        val payload = "Nexus backup payload\u0000with UTF-8: резервная копия".toByteArray(Charsets.UTF_8)

        val archive = BackupManager.packZip(payload)
        val zip = ZipInputStream(archive.inputStream())
        val entry = zip.use {
            val first = it.nextEntry
            val bytes = it.readBytes()
            first?.name to bytes
        }

        assertEquals("settings.json", entry.first)
        assertEquals(payload.toList(), entry.second.toList())
        assertEquals(payload.toList(), BackupManager.unpackZip(archive).toList())
    }

    @Test
    fun zipReaderRejectsAnArchiveWithoutTheExpectedSettingsEntry() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("other.json"))
            zip.write("{}".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.unpackZip(output.toByteArray())
        }
    }
}
