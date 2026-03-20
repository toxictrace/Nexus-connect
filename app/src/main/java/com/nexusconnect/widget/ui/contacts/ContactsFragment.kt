package com.nexusconnect.widget.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexusconnect.widget.R
import com.nexusconnect.widget.databinding.FragmentContactsBinding
import com.nexusconnect.widget.ui.adapters.ContactsAdapter
import com.nexusconnect.widget.ui.adapters.DragDropCallback
import com.nexusconnect.widget.ui.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayout

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ContactsAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupSortTabs()
        setupUpdateButton()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter(
            onContactClick = { contact ->
                viewModel.toggleContactSelection(contact.id)
            },
            onDragStart = { holder ->
                itemTouchHelper.startDrag(holder)
            }
        )

        val callback = DragDropCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerContacts)

        binding.recyclerContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerContacts.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
            refreshList()
        }
    }

    private fun setupSortTabs() {
        binding.tabSort.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { refreshList() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupUpdateButton() {
        binding.btnUpdateWidget.setOnClickListener {
            // Trigger widget update broadcast
            requireContext().sendBroadcast(
                android.content.Intent("com.nexusconnect.widget.ACTION_UPDATE_WIDGET")
            )
            com.google.android.material.snackbar.Snackbar.make(
                binding.root, "Widget updated!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeData() {
        viewModel.allContacts.observe(viewLifecycleOwner) { refreshList() }
        viewModel.selectedContactIds.observe(viewLifecycleOwner) { ids ->
            binding.tvSelectedCount.text = "Selected ${ids.size} / ${viewModel.allContacts.value?.size ?: 0}"
            refreshList()
        }
    }

    private fun refreshList() {
        val filtered = viewModel.getFilteredContacts()
        val selectedIds = viewModel.selectedContactIds.value ?: emptyList()

        val sortedTab = binding.tabSort.selectedTabPosition
        val sorted = when (sortedTab) {
            0 -> filtered.sortedBy { it.name }
            1 -> filtered.sortedByDescending { selectedIds.contains(it.id) }
            2 -> filtered.sortedBy { selectedIds.indexOf(it.id).let { i -> if (i == -1) Int.MAX_VALUE else i } }
            else -> filtered
        }

        adapter.submitList(sorted.map { it.copy(isSelected = selectedIds.contains(it.id)) })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
