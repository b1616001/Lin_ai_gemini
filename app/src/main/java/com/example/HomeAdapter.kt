package com.example

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.databinding.ItemHomeFeatureBinding

data class HomeFeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val testTag: String,
    val onClick: () -> Unit
)

class HomeAdapter(
    private val items: List<HomeFeatureItem>
) : RecyclerView.Adapter<HomeAdapter.FeatureViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val binding = ItemHomeFeatureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeatureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class FeatureViewHolder(private val binding: ItemHomeFeatureBinding) :
        RecyclerView.ViewHolder(binding.root) {

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
