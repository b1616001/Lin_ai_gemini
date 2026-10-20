package com.example

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemHomeFeatureBinding

/**
 * 首頁功能卡片資料模型
 *
 * @param id 功能項目的唯一辨識碼
 * @param title 功能標題名稱
 * @param description 功能簡短說明
 * @param iconRes 圖示資源 ID
 * @param testTag 測試標籤，用於 UI 自動化測試與識別
 * @param onClick 點擊該卡片時執行的回呼動作
 */
data class HomeFeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val testTag: String,
    val onClick: () -> Unit
)

/**
 * 首頁功能選單列表 RecyclerView 配接器 (Adapter)
 *
 * 用途：
 * 將功能項目資料集合 [items] 綁定至卡片佈局 [ItemHomeFeatureBinding]
 *
 * @param items 功能項目列表
 */
class HomeAdapter(
    private val items: List<HomeFeatureItem>
) : RecyclerView.Adapter<HomeAdapter.FeatureViewHolder>() {

    /**
     * 建立 ViewHolder 並綁定 ViewBinding
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val binding = ItemHomeFeatureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeatureViewHolder(binding)
    }

    /**
     * 將指定位置的項目資料綁定至 ViewHolder
     */
    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        holder.bind(items[position])
    }

    /**
     * 回傳項目總筆數
     */
    override fun getItemCount(): Int = items.size

    /**
     * 功能卡片的 ViewHolder，負責單一卡片的視圖資料賦值與點擊事件設定
     */
    class FeatureViewHolder(private val binding: ItemHomeFeatureBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * 綁定功能卡片資料
         *
         * @param item 功能項目資料
         */
        fun bind(item: HomeFeatureItem) {
            binding.tvFeatureTitle.text = item.title
            binding.tvFeatureDesc.text = item.description
            binding.ivFeatureIcon.setImageResource(item.iconRes)
            binding.cardFeature.tag = item.testTag

            binding.cardFeature.setOnClickListener {
                item.onClick()
            }
        }
    }
}
