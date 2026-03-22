package com.toxictrace.nexusconnect.data.backup

import android.content.Context
import android.net.Uri
import com.toxictrace.nexusconnect.data.model.*
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {

    private const val PASSWORD = "NexusConnect_SecureBackup_2024"
    private const val SALT     = "NexusConnectSalt"
    private const val ZIP_ENTRY = "settings.json"

    fun settingsToJson(settings: WidgetSettings, selectedIds: List<Long>): String {
        return JSONObject().apply {
            put("version",            1)
            put("columns",            settings.columns)
            put("tileHeightDp",       settings.tileHeightDp)
            put("filterFavorites",    settings.filterFavorites)
            put("filterRecents",      settings.filterRecents)
            put("filterFrequent",     settings.filterFrequent)
            put("clickAction",        settings.clickAction.name)
            put("hapticFeedback",     settings.hapticFeedback)
            put("theme",              settings.theme.name)
            put("dynamicColors",      settings.dynamicColors)
            put("accentColorIndex",   settings.accentColorIndex)
            put("avatarIdentity",     settings.avatarIdentity.name)
            put("customAvatarUri",    settings.customAvatarUri)
            put("showUnknownNumbers", settings.showUnknownNumbers)
            put("unknownNumbersDays", settings.unknownNumbersDays)
            put("messengerWhatsApp",  settings.messengerWhatsApp)
            put("messengerViber",     settings.messengerViber)
            put("messengerTelegram",  settings.messengerTelegram)
            put("showCallTypeIcon",   settings.showCallTypeIcon)
            put("selectedContactIds", selectedIds.joinToString(","))
        }.toString(2)
    }

    fun jsonToSettings(json: String): Pair<WidgetSettings, List<Long>> {
        val o = JSONObject(json)
        val settings = WidgetSettings(
            columns            = o.optInt("columns", 4),
            tileHeightDp       = o.optInt("tileHeightDp", 3),
            filterFavorites    = o.optBoolean("filterFavorites", true),
            filterRecents      = o.optBoolean("filterRecents", true),
            filterFrequent     = o.optBoolean("filterFrequent", false),
            clickAction        = runCatching { ClickAction.valueOf(o.optString("clickAction", "SHOW_DIALOG")) }.getOrDefault(ClickAction.SHOW_DIALOG),
            hapticFeedback     = o.optBoolean("hapticFeedback", true),
            theme              = runCatching { AppTheme.valueOf(o.optString("theme", "LIGHT")) }.getOrDefault(AppTheme.LIGHT),
            dynamicColors      = o.optBoolean("dynamicColors", true),
            accentColorIndex   = o.optInt("accentColorIndex", 0),
            avatarIdentity     = runCatching { AvatarIdentity.valueOf(o.optString("avatarIdentity", "DEFAULT")) }.getOrDefault(AvatarIdentity.DEFAULT),
            customAvatarUri    = o.optString("customAvatarUri", ""),
            showUnknownNumbers = o.optBoolean("showUnknownNumbers", true),
            unknownNumbersDays = o.optInt("unknownNumbersDays", 3),
            messengerWhatsApp  = o.optString("messengerWhatsApp", ""),
            messengerViber     = o.optString("messengerViber", ""),
            messengerTelegram  = o.optString("messengerTelegram", ""),
            showCallTypeIcon   = o.optBoolean("showCallTypeIcon", true)
        )
        val ids = o.optString("selectedContactIds", "")
            .split(",").mapNotNull { it.toLongOrNull() }
        return Pair(settings, ids)
    }

    private fun deriveKey(): SecretKeySpec {
        val spec = PBEKeySpec(PASSWORD.toCharArray(), SALT.toByteArray(), 65536, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun encrypt(data: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey())
        return cipher.iv + cipher.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(), IvParameterSpec(data.copyOfRange(0, 16)))
        return String(cipher.doFinal(data.copyOfRange(16, data.size)), Charsets.UTF_8)
    }

    private fun packZip(payload: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { it.putNextEntry(ZipEntry(ZIP_ENTRY)); it.write(payload); it.closeEntry() }
        return baos.toByteArray()
    }

    private fun unpackZip(data: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            if (zip.nextEntry?.name == ZIP_ENTRY) return zip.readBytes()
        }
        throw IllegalArgumentException("Invalid backup file")
    }

    fun generateFileName(): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date()) + ".nexbkup"

    fun displayName(fileName: String): String = fileName.removeSuffix(".nexbkup")

    fun saveBackup(context: Context, folderUri: Uri, json: String): String {
        val fileName = generateFileName()
        val data = packZip(encrypt(json))
        val file = androidx.documentfile.provider.DocumentFile
            .fromTreeUri(context, folderUri)
            ?.createFile("application/octet-stream", fileName)
            ?.uri ?: throw IOException("Cannot create file")
        context.contentResolver.openOutputStream(file)?.use { it.write(data) }
            ?: throw IOException("Cannot write file")
        return displayName(fileName)
    }

    fun listBackups(context: Context, folderUri: Uri): List<Pair<String, Uri>> {
        return androidx.documentfile.provider.DocumentFile
            .fromTreeUri(context, folderUri)
            ?.listFiles()
            ?.filter { it.name?.endsWith(".nexbkup") == true }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f -> f.name?.let { Pair(displayName(it), f.uri) } }
            ?: emptyList()
    }

    fun loadBackup(context: Context, fileUri: Uri): String {
        val data = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read file")
        return decrypt(unpackZip(data))
    }
}
