package com.nexusconnect.widget.ui.layout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nexusconnect.widget.databinding.FragmentLayoutBinding
import com.nexusconnect.widget.ui.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar

class LayoutFragment : Fragment() {

    private var _binding: FragmentLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeSettings()
        setupSliders()
        setupFilterCheckboxes()
        setupApplyButton()
        updateWidgetPreview()
    }

    private fun observeSettings() {
        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            binding.sliderColumns.value = settings.columns.toFloat()
            binding.tvColumnsValue.text = settings.columns.toString()
            binding.sliderTileHeight.value = settings.tileHeightDp.toFloat()
            binding.tvTileHeightValue.text = settings.tileHeightDp.toString()
            binding.sliderMaxContacts.value = settings.maxContacts.toFloat()
            binding.tvMaxContactsValue.text = settings.maxContacts.toString()
            binding.cbFavorites.isChecked = settings.showFavorites
            binding.cbRecents.isChecked = settings.showRecents
            binding.cbFrequent.isChecked = settings.showFrequent
        }
    }

    private fun setupSliders() {
        binding.sliderColumns.apply {
            valueFrom = 3f
            valueTo = 6f
            stepSize = 1f
            addOnChangeListener { _, value, _ ->
                binding.tvColumnsValue.text = value.toInt().toString()
                updateWidgetPreview()
            }
        }

        binding.sliderTileHeight.apply {
            valueFrom = 56f
            valueTo = 100f
            stepSize = 4f
            addOnChangeListener { _, value, _ ->
                binding.tvTileHeightValue.text = value.toInt().toString()
            }
        }

        binding.sliderMaxContacts.apply {
            valueFrom = 4f
            valueTo = 24f
            stepSize = 1f
            addOnChangeListener { _, value, _ ->
                binding.tvMaxContactsValue.text = value.toInt().toString()
            }
        }
    }

    private fun setupFilterCheckboxes() {
        // No extra logic needed; state captured on Apply
    }

    private fun setupApplyButton() {
        binding.btnApplyLayout.setOnClickListener {
            val current = viewModel.settings.value ?: return@setOnClickListener
            viewModel.updateSettings(
                current.copy(
                    columns = binding.sliderColumns.value.toInt(),
                    tileHeightDp = binding.sliderTileHeight.value.toInt(),
                    maxContacts = binding.sliderMaxContacts.value.toInt(),
                    showFavorites = binding.cbFavorites.isChecked,
                    showRecents = binding.cbRecents.isChecked,
                    showFrequent = binding.cbFrequent.isChecked
                )
            )
            Snackbar.make(binding.root, "Layout settings saved!", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateWidgetPreview() {
        val cols = binding.sliderColumns.value.toInt()
        val previewGrid = binding.widgetPreviewGrid
        previewGrid.columnCount = cols
        // Rebuild preview tiles
        previewGrid.removeAllViews()
        val tileCount = cols * 2
        repeat(tileCount) { i ->
            val tile = layoutInflater.inflate(
                com.nexusconnect.widget.R.layout.item_widget_preview_tile,
                previewGrid,
                false
            )
            if (i == cols) {
                tile.alpha = 0.4f
            }
            previewGrid.addView(tile)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
