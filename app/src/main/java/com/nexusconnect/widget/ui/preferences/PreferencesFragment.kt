package com.nexusconnect.widget.ui.preferences

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nexusconnect.widget.R
import com.nexusconnect.widget.data.models.*
import com.nexusconnect.widget.databinding.FragmentPreferencesBinding
import com.nexusconnect.widget.ui.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class PreferencesFragment : Fragment() {

    private var _binding: FragmentPreferencesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { writeExport(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { readImport(it) } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreferencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeSettings()
        setupClickActions()
        setupPriorityApps()
        setupTheme()
        setupAvatarStyle()
        setupImportExport()
    }

    private fun observeSettings() {
        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            when (settings.globalClickAction) {
                ClickAction.SHOW_DIALOG -> binding.radioShowDialog.isChecked = true
                ClickAction.DIRECT_CALL -> binding.radioDirectCall.isChecked = true
                ClickAction.OPEN_PROFILE -> binding.radioOpenProfile.isChecked = true
            }
            when (settings.priorityApp) {
                MessengerApp.PHONE -> binding.btnPhone.isSelected = true
                MessengerApp.WHATSAPP -> binding.btnWhatsapp.isSelected = true
                MessengerApp.TELEGRAM -> binding.btnTelegram.isSelected = true
                MessengerApp.VIBER -> binding.btnViber.isSelected = true
            }
            binding.switchHaptic.isChecked = settings.hapticFeedback
            when (settings.theme) {
                AppTheme.LIGHT -> binding.radioLight.isChecked = true
                AppTheme.DARK -> binding.radioDark.isChecked = true
                AppTheme.SYSTEM -> binding.radioSystem.isChecked = true
            }
            binding.switchDynamicColors.isChecked = settings.dynamicColors
            when (settings.avatarStyle) {
                AvatarStyle.SYSTEM_DEFAULT -> binding.radioAvatarSystem.isChecked = true
                AvatarStyle.DYNAMIC_INITIALS -> binding.radioAvatarInitials.isChecked = true
                AvatarStyle.PHOTOS_ONLY -> binding.radioAvatarPhotos.isChecked = true
            }
        }
    }

    private fun setupClickActions() {
        binding.radioGroupClickAction.setOnCheckedChangeListener { _, checkedId ->
            val action = when (checkedId) {
                R.id.radio_show_dialog -> ClickAction.SHOW_DIALOG
                R.id.radio_direct_call -> ClickAction.DIRECT_CALL
                R.id.radio_open_profile -> ClickAction.OPEN_PROFILE
                else -> ClickAction.SHOW_DIALOG
            }
            saveSettings { it.copy(globalClickAction = action) }
        }
        binding.switchHaptic.setOnCheckedChangeListener { _, checked ->
            saveSettings { it.copy(hapticFeedback = checked) }
        }
    }

    private fun setupPriorityApps() {
        val appButtons = mapOf(
            binding.btnPhone to MessengerApp.PHONE,
            binding.btnWhatsapp to MessengerApp.WHATSAPP,
            binding.btnTelegram to MessengerApp.TELEGRAM,
            binding.btnViber to MessengerApp.VIBER
        )
        appButtons.forEach { (btn, app) ->
            btn.setOnClickListener {
                appButtons.keys.forEach { b -> b.isSelected = false }
                btn.isSelected = true
                saveSettings { it.copy(priorityApp = app) }
            }
        }
    }

    private fun setupTheme() {
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radio_light -> AppTheme.LIGHT
                R.id.radio_dark -> AppTheme.DARK
                R.id.radio_system -> AppTheme.SYSTEM
                else -> AppTheme.LIGHT
            }
            saveSettings { it.copy(theme = theme) }
        }
        binding.switchDynamicColors.setOnCheckedChangeListener { _, checked ->
            saveSettings { it.copy(dynamicColors = checked) }
        }
    }

    private fun setupAvatarStyle() {
        binding.radioGroupAvatar.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.radio_avatar_system -> AvatarStyle.SYSTEM_DEFAULT
                R.id.radio_avatar_initials -> AvatarStyle.DYNAMIC_INITIALS
                R.id.radio_avatar_photos -> AvatarStyle.PHOTOS_ONLY
                else -> AvatarStyle.DYNAMIC_INITIALS
            }
            saveSettings { it.copy(avatarStyle = style) }
        }
    }

    private fun setupImportExport() {
        binding.btnExportSettings.setOnClickListener {
            exportLauncher.launch("nexus_connect_settings.json")
        }
        binding.btnImportSettings.setOnClickListener {
            importLauncher.launch("application/json")
        }
    }

    private fun saveSettings(transform: (WidgetSettings) -> WidgetSettings) {
        val current = viewModel.settings.value ?: return
        viewModel.updateSettings(transform(current))
    }

    private fun writeExport(uri: Uri) {
        try {
            val json = viewModel.exportSettings()
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { it.write(json) }
            }
            Snackbar.make(binding.root, "Settings exported!", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun readImport(uri: Uri) {
        try {
            val json = requireContext().contentResolver.openInputStream(uri)?.use { inp ->
                BufferedReader(InputStreamReader(inp)).readText()
            } ?: return
            if (viewModel.importSettings(json)) {
                Snackbar.make(binding.root, "Settings imported!", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Invalid settings file", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Import failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
