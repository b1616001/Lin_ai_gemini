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

/**
 * 相簿網格相片列表的 RecyclerView 配接器 (ListAdapter)
 *
 * 用途：
 * 1. 使用 [ListAdapter] 與 [DiffUtil] 提高網格相片更新時的渲染效能
 * 2. 針對各網格項目非同步取樣壓縮解碼縮圖，避免 OOM (Out Of Memory) 記憶體崩潰
 * 3. 在 ViewHolder 複用時自動取消前次未完成的載入協程，避免圖片錯位或浪費系統資源
 * 4. 提供相片點擊檢視與個別刪除按鈕監聽
 *
 * @param onPhotoClick 點擊相片項目的回呼
 * @param onDeleteClick 點擊垃圾桶刪除按鈕的回呼
 */
class PhotoAdapter(
    private val onPhotoClick: (PhotoItem) -> Unit,
    private val onDeleteClick: (PhotoItem) -> Unit
) : ListAdapter<PhotoItem, PhotoAdapter.PhotoViewHolder>(DiffCallback) {

    /**
     * 建立相片項目的 ViewHolder 並綁定 ViewBinding
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    /**
     * 綁定指定位置的相片項目資料
     */
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 相片項目的 ViewHolder
     */
    inner class PhotoViewHolder(private val binding: ItemPhotoCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * 記錄當前項目正在載入縮圖的協程工作，方便在回收時取消
         */
        private var loadJob: Job? = null

        /**
         * 綁定相片資料至視圖元件
         *
         * @param item 相片資料模型
         */
        fun bind(item: PhotoItem) {
            // 顯示拍攝時間 (HH:mm:ss)
            binding.tvPhotoTime.text = item.formattedTime()

            // 取消此 View 之前可能未完成的圖片載入工作
            loadJob?.cancel()

            // 先清空縮圖，避免重用時閃現舊圖
            binding.ivPhotoThumbnail.setImageDrawable(null)

            // 非同步壓縮讀取縮圖 (240x240)
            val lifecycleOwner = binding.root.findViewTreeLifecycleOwner()
            if (lifecycleOwner != null) {
                loadJob = lifecycleOwner.lifecycleScope.launch {
                    val bitmap = ImageLoaderUtils.loadBitmapSampled(item.file, 240, 240)
                    if (bitmap != null) {
                        binding.ivPhotoThumbnail.setImageBitmap(bitmap)
                    }
                }
            } else {
                // 若無法取得生命週期物件時的備援同步載入
                val bitmap = android.graphics.BitmapFactory.decodeFile(item.file.absolutePath)
                if (bitmap != null) {
                    binding.ivPhotoThumbnail.setImageBitmap(bitmap)
                }
            }

            // 設置相片點擊事件（開啟大圖詳情）
            binding.root.setOnClickListener {
                onPhotoClick(item)
            }

            // 設置個別相片刪除按鈕事件
            binding.btnItemDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    /**
     * DiffUtil 比對實作，判斷清單資料是否變更以進行最小化刷新
     */
    companion object DiffCallback : DiffUtil.ItemCallback<PhotoItem>() {
        /**
         * 檢查兩筆資料是否代表同一張照片（比對檔案路徑）
         */
        override fun areItemsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        /**
         * 檢查兩筆資料的內容是否相同（比對時間戳記與檔案大小）
         */
        override fun areContentsTheSame(oldItem: PhotoItem, newItem: PhotoItem): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.sizeBytes == newItem.sizeBytes
        }
    }
}
