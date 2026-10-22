package com.example.Fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.Utils.RoomUtils
import com.example.databinding.DialogEditNoteBinding
import com.example.databinding.FragmentRoomBinding
import com.example.room.NoteAdapter
import com.example.room.NoteEntity
import com.example.room.NoteRepository
import com.example.viewBinding.BaseBindingFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Room 本地資料庫記事管理畫面 (RoomFragment)
 *
 * 功能：
 * 1. 繼承 [BaseBindingFragment]
 * 2. 透過 [RoomUtils] 與 [NoteRepository] 進行 Room 資料庫的 CRUD (新增、修改、刪除、查詢)
 * 3. 採用 Kotlin Flow 即時監聽與差異化局部刷新 (ListAdapter + DiffUtil)
 * 4. 支援關鍵字即時搜尋標題與內容
 * 5. 提供分類標籤選擇與自訂範例資料快速注入
 */
class RoomFragment : BaseBindingFragment<FragmentRoomBinding>() {

    private lateinit var repository: NoteRepository
    private lateinit var noteAdapter: NoteAdapter
    private var searchJob: Job? = null

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentRoomBinding? {
        return FragmentRoomBinding.inflate(layoutInflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = RoomUtils.getNoteRepository(requireContext())

        setupRecyclerView()
        setupListeners()
        observeNotes()
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onNoteClick = { note ->
                showEditNoteDialog(note)
            },
            onEditClick = { note ->
                showEditNoteDialog(note)
            },
            onDeleteClick = { note ->
                showDeleteConfirmDialog(note)
            }
        )

        binding?.rvNotes?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = noteAdapter
        }
    }

    private fun setupListeners() {
        // 返回按鈕
        binding?.btnRoomBack?.setOnClickListener {
            findNavController().navigateUp()
        }

        // 新增記事 FAB
        binding?.fabAddNote?.setOnClickListener {
            showAddNoteDialog()
        }

        // 加入範例資料按鈕
        binding?.btnAddSampleNotes?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                RoomUtils.populateSampleData(repository)
                Toast.makeText(requireContext(), "已成功加入範例資料", Toast.LENGTH_SHORT).show()
            }
        }

        // 搜尋輸入監聽
        binding?.etSearchNotes?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                observeNotes(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * 監聽記事清單 Flow
     */
    private fun observeNotes(query: String = "") {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = if (query.isBlank()) {
                repository.allNotes
            } else {
                repository.searchNotes(query)
            }

            flow.collectLatest { list ->
                noteAdapter.submitList(list)

                binding?.apply {
                    val count = list.size
                    tvRoomCountBadge.text = getString(R.string.room_total_count, count)

                    if (list.isEmpty()) {
                        layoutEmptyNotes.visibility = View.VISIBLE
                        rvNotes.visibility = View.GONE
                    } else {
                        layoutEmptyNotes.visibility = View.GONE
                        rvNotes.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /**
     * 彈出新增記事對話框
     */
    private fun showAddNoteDialog() {
        val dialogBinding = DialogEditNoteBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.room_add_note)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                val title = dialogBinding.etNoteTitle.text?.toString()?.trim() ?: ""
                val content = dialogBinding.etNoteContent.text?.toString()?.trim() ?: ""

                if (!RoomUtils.isInputValid(title, content)) {
                    Toast.makeText(requireContext(), "標題與內容皆不可為空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedChipId = dialogBinding.chipGroupCategory.checkedChipId
                val category = if (selectedChipId != View.NO_ID) {
                    dialogBinding.chipGroupCategory.findViewById<Chip>(selectedChipId)?.text?.toString() ?: "一般"
                } else {
                    "一般"
                }

                val newNote = NoteEntity(
                    title = title,
                    content = content,
                    category = category,
                    timestamp = System.currentTimeMillis()
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    repository.insert(newNote)
                    Toast.makeText(requireContext(), R.string.room_saved_toast, Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 彈出編輯既有記事對話框
     */
    private fun showEditNoteDialog(note: NoteEntity) {
        val dialogBinding = DialogEditNoteBinding.inflate(layoutInflater)

        dialogBinding.etNoteTitle.setText(note.title)
        dialogBinding.etNoteContent.setText(note.content)

        // 選取原分類 Chip
        when (note.category) {
            "工作" -> dialogBinding.chipWork.isChecked = true
            "生活" -> dialogBinding.chipLife.isChecked = true
            "重要" -> dialogBinding.chipImportant.isChecked = true
            else -> dialogBinding.chipGeneral.isChecked = true
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.room_edit_note)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                val title = dialogBinding.etNoteTitle.text?.toString()?.trim() ?: ""
                val content = dialogBinding.etNoteContent.text?.toString()?.trim() ?: ""

                if (!RoomUtils.isInputValid(title, content)) {
                    Toast.makeText(requireContext(), "標題與內容皆不可為空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedChipId = dialogBinding.chipGroupCategory.checkedChipId
                val category = if (selectedChipId != View.NO_ID) {
                    dialogBinding.chipGroupCategory.findViewById<Chip>(selectedChipId)?.text?.toString() ?: "一般"
                } else {
                    "一般"
                }

                val updatedNote = note.copy(
                    title = title,
                    content = content,
                    category = category,
                    timestamp = System.currentTimeMillis()
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    repository.update(updatedNote)
                    Toast.makeText(requireContext(), R.string.room_updated_toast, Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 彈出刪除確認對話框
     */
    private fun showDeleteConfirmDialog(note: NoteEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.room_confirm_delete_title)
            .setMessage(getString(R.string.room_confirm_delete_msg, note.title))
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.delete(note)
                    Toast.makeText(requireContext(), R.string.room_deleted_toast, Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
