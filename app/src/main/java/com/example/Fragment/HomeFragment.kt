package com.example.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.HomeAdapter
import com.example.HomeFeatureItem
import com.example.R
import com.example.Utils.LanguageUtils
import com.example.databinding.FragmentHomeBinding
import com.example.viewBinding.BaseBindingFragment

/**
 * 應用程式首頁功能選單畫面
 *
 * 用途：
 * 1. 繼承 [BaseBindingFragment]，顯示應用程式的主要功能入口
 * 2. 顯示目前系統/應用語言標籤，並透過 [LanguageUtils] 提供多國語言切換功能（支援繁體中文、English、日本語、簡體中文）
 * 3. 透過 RecyclerView 展現主要功能卡片列表（多國語系切換、相機拍照功能）
 * 4. 支援 Jetpack Navigation 畫面導覽至 [CameraFragment]
 */
class HomeFragment : BaseBindingFragment<FragmentHomeBinding>() {

    /**
     * 實作基底類別方法：載入首頁畫面的 ViewBinding
     */
    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentHomeBinding? {
        return FragmentHomeBinding.inflate(layoutInflater)
    }

    /**
     * 視圖初始化完成後建立語言標籤與功能選單列表
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLanguageBadge()
        setupRecyclerView()
    }

    /**
     * 當使用者從子畫面返回首頁時，重新刷新語言標籤文字
     */
    override fun onResume() {
        super.onResume()
        setupLanguageBadge()
    }

    /**
     * 更新頂部當前語言指示徽章
     * 依據目前應用程式的 Locale 設定，對應顯示適合的語言名稱
     */
    private fun setupLanguageBadge() {
        binding?.tvCurrentLanguageBadge?.text = LanguageUtils.getLanguageDisplayName()
    }

    /**
     * 初始化首頁功能卡片清單 (RecyclerView)
     * 包含：
     * 1. 多國語系選擇卡片（點擊呼叫 LanguageUtils 開啟語言選單）
     * 2. 相機卡片（點擊導航至 CameraFragment）
     */
    private fun setupRecyclerView() {
        val currentLangName = LanguageUtils.getLanguageDisplayName()

        val featureItems = listOf(
            HomeFeatureItem(
                id = "languages",
                title = getString(R.string.menu_languages),
                description = "${getString(R.string.menu_languages_desc)} (${currentLangName})",
                iconRes = R.drawable.ic_language,
                testTag = "home_item_languages",
                onClick = {
                    LanguageUtils.showLanguageDialog(requireContext()) { _, _ ->
                        setupLanguageBadge()
                        setupRecyclerView()
                    }
                }
            ),
            HomeFeatureItem(
                id = "camera",
                title = getString(R.string.menu_camera),
                description = getString(R.string.menu_camera_desc),
                iconRes = R.drawable.ic_camera,
                testTag = "home_item_camera",
                onClick = {
                    try {
                        findNavController().navigate(R.id.action_homeFragment_to_cameraFragment)
                    } catch (_: Exception) {
                        // 防止重複點擊導航觸發例外
                    }
                }
            )
        )

        binding?.rvFeatures?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = HomeAdapter(featureItems)
        }
    }
}
