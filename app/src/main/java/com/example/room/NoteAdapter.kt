package com.example.room

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemRoomNoteBinding

/**
 * Room 記事列表 RecyclerView 配接器 (NoteAdapter)
 *
 * @param onNoteClick 點擊卡片回呼
 * @param onEditClick 點擊編輯按鈕回呼
 * @param onDeleteClick 點擊刪除按鈕回呼
 */
class NoteAdapter(
    private val onNoteClick: (NoteEntity) -> Unit,
    private val onEditClick: (NoteEntity) -> Unit,
    private val onDeleteClick: (NoteEntity) -> Unit
) : ListAdapter<NoteEntity, NoteAdapter.NoteViewHolder>(NoteDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemRoomNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemRoomNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: NoteEntity) {
            binding.tvNoteTitle.text = note.title
            binding.tvNoteContent.text = note.content
            binding.tvNoteCategory.text = note.category
            binding.tvNoteTime.text = note.formattedDateTime()

            binding.root.setOnClickListener {
                onNoteClick(note)
            }

            binding.btnEditNote.setOnClickListener {
                onEditClick(note)
            }

            binding.btnDeleteNote.setOnClickListener {
                onDeleteClick(note)
            }
        }
    }

    companion object NoteDiffCallback : DiffUtil.ItemCallback<NoteEntity>() {
        override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
            return oldItem == newItem
        }
    }
}
