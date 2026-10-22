package com.example

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.Utils.ImageLoaderUtils
import com.example.databinding.ItemPhotoCardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 相片列表 RecyclerView 配接器 (PhotoAdapter)
 *
 * 用途：
 * 1. 顯示已拍攝相片的圖片列表，支援水平橫向滑動列表（相機預覽頁）與 3 欄網格列表（底部相簿頁）
 * 2. 使用 [ListAdapter] 與 [DiffUtil] 進行高效差異化比對與局部刷新
 * 3. 採用 [ImageLoaderUtils.loadBitmapSampled] 非同步高效取樣壓縮載入縮圖，徹底預防 OOM 崩潰
 * 4. 支援點擊相片檢視大圖詳情與點擊刪除按鈕
 *
 * @param coroutineScope 協程範圍（若未傳入則自動使用 ViewTreeLifecycleOwner 或主執行緒 Scope）
 * @param isHorizontal 是否為相機畫面上的水平預覽列表模式（若是則自動調整卡片為固定寬高尺寸）
 * @param onPhotoClick 點擊照片卡片的回呼函式
 * @param onDeleteClick 點擊刪除按鈕的回呼函式
 */
class PhotoAdapter(
    private val coroutineScope: CoroutineScope? = null,
    private val isHorizontal: Boolean = false,
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

        // 若為相機畫面上的水平滾動模式，設定固定寬度與高度以呈現正方形相片卡片
        if (isHorizontal) {
            val density = parent.context.resources.displayMetrics.density
            val itemSize = (84 * density).toInt()
            binding.root.layoutParams = ViewGroup.MarginLayoutParams(itemSize, itemSize).apply {
                val marginHorizontal = (4 * density).toInt()
                val marginVertical = (2 * density).toInt()
                setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical)
            }
        }

        return PhotoViewHolder(binding)
    }

    /**
     * 綁定指定位置的照片資料
     */
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 當 ViewHolder 脫離視窗時取消進行中的圖片載入協程，避免背景資源浪費
     */
    override fun onViewDetachedFromWindow(holder: PhotoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.cancelLoad()
    }

    /**
     * 相片項目的 ViewHolder
     */
    inner class PhotoViewHolder(private val binding: ItemPhotoCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * 記錄當前項目正在載入縮圖的協程 Job
         */
        private var loadJob: Job? = null

        /**
         * 取消圖片載入協程
         */
        fun cancelLoad() {
            loadJob?.cancel()
            loadJob = null
        }

        /**
         * 綁定相片資料至視圖元件
         *
         * @param item 相片資料模型
         */
        fun bind(item: PhotoItem) {
            // 顯示拍攝時間 (HH:mm:ss)
            binding.tvPhotoTime.text = item.formattedTime()

            // 取消舊的載入任務
            cancelLoad()

            // 預設清空縮圖，避免 ViewHolder 複用時閃現舊照片
            binding.ivPhotoThumbnail.setImageDrawable(null)

            // 安全獲取可用 CoroutineScope
            val scope = coroutineScope
                ?: binding.root.findViewTreeLifecycleOwner()?.lifecycleScope
                ?: CoroutineScope(Dispatchers.Main + SupervisorJob())

            // 非同步壓縮讀取縮圖 (240x240) 並設置至 ImageView
            loadJob = scope.launch {
                val bitmap = ImageLoaderUtils.loadBitmapSampled(item.file, 240, 240)
                if (bitmap != null) {
                    binding.ivPhotoThumbnail.setImageBitmap(bitmap)
                } else {
                    // 若圖片解碼失敗或檔案不存在時的替代圖示
                    binding.ivPhotoThumbnail.setImageResource(R.drawable.ic_camera)
                }
            }

            // 點擊照片卡片開啟詳情檢視
            binding.root.setOnClickListener {
                onPhotoClick(item)
            }

            // 點擊垃圾桶按鈕刪除相片
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
         * 檢查兩筆資料是否代表同一張照片（比對檔案絕對路徑）
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
