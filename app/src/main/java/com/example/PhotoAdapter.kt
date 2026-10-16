package com.example

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemPhotoCardBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PhotoAdapter(
    private val onPhotoClick: (PhotoItem) -> Unit,
    private val onDeleteClick: (PhotoItem) -> Unit
) : ListAdapter<PhotoItem, PhotoAdapter.PhotoViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(private val binding: ItemPhotoCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var loadJob: Job? = null

        fun bind(item: PhotoItem) {
            binding.tvPhotoTime.text = item.formattedTime()

            // Cancel any previous image load for recycled view
            loadJob?.cancel()

            // Placeholder
            binding.ivPhotoThumbnail.setImageDrawable(null)

            // Async load thumbnail
            val lifecycleOwner = binding.root.findViewTreeLifecycleOwner()
            if (lifecycleOwner != null) {
                loadJob = lifecycleOwner.lifecycleScope.launch {
                    val bitmap = ImageLoaderUtils.loadBitmapSampled(item.file, 240, 240)
                    if (bitmap != null) {
                        binding.ivPhotoThumbnail.setImageBitmap(bitmap)
                    }
                }
            } else {
                // Fallback direct load
                val bitmap = android.graphics.BitmapFactory.decodeFile(item.file.absolutePath)
                if (bitmap != null) {
                    binding.ivPhotoThumbnail.setImageBitmap(bitmap)
                }
            }

            binding.root.setOnClickListener {
                onPhotoClick(item)
            }

            binding.btnItemDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PhotoItem>() {
        override fun areItemsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.sizeBytes == newItem.sizeBytes
        }
    }
}
