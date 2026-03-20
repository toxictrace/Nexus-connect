package com.nexusconnect.widget.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexusconnect.widget.R
import com.nexusconnect.widget.data.models.ContactModel
import com.nexusconnect.widget.databinding.ItemContactBinding
import com.bumptech.glide.Glide

class ContactsAdapter(
    private val onContactClick: (ContactModel) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<ContactModel, ContactsAdapter.ContactViewHolder>(DiffCallback()) {

    inner class ContactViewHolder(val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ContactModel) {
            binding.tvContactName.text = contact.name
            binding.tvContactSub.text = contact.phone.ifBlank { "No phone" }
            binding.cbContactSelected.isChecked = contact.isSelected

            // Load avatar
            if (contact.photoUri != null) {
                Glide.with(binding.ivAvatar)
                    .load(android.net.Uri.parse(contact.photoUri))
                    .circleCrop()
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_person_placeholder)
                val initials = contact.name.split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .joinToString("")
                binding.tvInitials.text = initials
                binding.tvInitials.visibility = android.view.View.VISIBLE
                binding.ivAvatar.setColorFilter(generateColor(contact.name))
            }

            binding.root.setOnClickListener { onContactClick(contact) }
            binding.cbContactSelected.setOnClickListener { onContactClick(contact) }

            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDragStart(this)
                }
                false
            }
        }

        private fun generateColor(name: String): Int {
            val colors = intArrayOf(
                Color.parseColor("#1A56DB"),
                Color.parseColor("#7C3AED"),
                Color.parseColor("#0891B2"),
                Color.parseColor("#059669"),
                Color.parseColor("#DC2626"),
                Color.parseColor("#D97706")
            )
            return colors[Math.abs(name.hashCode()) % colors.size]
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMoved(from: Int, to: Int) {
        val list = currentList.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        submitList(list)
    }

    class DiffCallback : DiffUtil.ItemCallback<ContactModel>() {
        override fun areItemsTheSame(old: ContactModel, new: ContactModel) = old.id == new.id
        override fun areContentsTheSame(old: ContactModel, new: ContactModel) = old == new
    }
}

class DragDropCallback(private val adapter: ContactsAdapter) :
    ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    override fun onMove(
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.onItemMoved(vh.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
}
