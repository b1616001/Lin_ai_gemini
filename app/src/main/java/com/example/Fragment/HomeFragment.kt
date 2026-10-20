package com.example.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.HomeAdapter
import com.example.HomeFeatureItem
import com.example.R
import com.example.databinding.FragmentHomeBinding
import com.example.viewBinding.BaseBindingFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * 應用程式首頁功能選單畫面
 *
 * 用途：
 * 1. 繼承 [BaseBindingFragment]，顯示應用程式的主要功能入口
 * 2. 顯示目前系統/應用語言標籤，並提供多國語言切換彈窗功能（支援繁體中文、English、日本語、簡體中文）
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
        val currentTag = getCurrentLanguageTag()
        val displayName = when {
            currentTag.startsWith("en", ignoreCase = true) -> "English"
            currentTag.startsWith("ja", ignoreCase = true) -> "日本語"
            currentTag.startsWith("zh-cn", ignoreCase = true) || currentTag.startsWith("zh-hans", ignoreCase = true) -> "簡體中文"
            else -> "繁體中文"
        }
        binding?.tvCurrentLanguageBadge?.text = displayName
    }

    /**
     * 取得當前應用程式所設定的語言標籤 (BCP 47 language tag)
     * 優先取得 AppCompatDelegate 所設定的 App 語系，若無則回傳系統預設語系
     *
     * @return 語言代碼字串（例如 "zh-TW", "en", "ja"）
     */
    private fun getCurrentLanguageTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            return appLocales.get(0)?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
        }
        return Locale.getDefault().toLanguageTag()
    }

    /**
     * 初始化首頁功能卡片清單 (RecyclerView)
     * 包含：
     * 1. 多國語系選擇卡片（點擊開啟語言選單）
     * 2. 相機卡片（點擊導航至 CameraFragment）
     */
    private fun setupRecyclerView() {
        val currentTag = getCurrentLanguageTag()
        val currentLangName = when {
            currentTag.startsWith("en", ignoreCase = true) -> "English"
            currentTag.startsWith("ja", ignoreCase = true) -> "日本語"
            currentTag.startsWith("zh-cn", ignoreCase = true) || currentTag.startsWith("zh-hans", ignoreCase = true) -> "簡體中文"
            else -> "繁體中文"
        }

        val featureItems = listOf(
            HomeFeatureItem(
                id = "languages",
                title = getString(R.string.menu_languages),
                description = "${getString(R.string.menu_languages_desc)} (${currentLangName})",
                iconRes = R.drawable.ic_language,
                testTag = "home_item_languages",
                onClick = { showLanguageDialog() }
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

    /**
     * 顯示切換語言的 Material 對話框
     * 支援使用者在應用程式內動態切換繁體中文、英文、日文與簡體中文
     */
    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.lang_zh_tw) to "zh-TW",
            getString(R.string.lang_en) to "en",
            getString(R.string.lang_ja) to "ja",
            getString(R.string.lang_zh_cn) to "zh-CN"
        )

        val languageNames = languages.map { it.first }.toTypedArray()
        val currentTag = getCurrentLanguageTag()
        val checkedItem = languages.indexOfFirst {
            currentTag.startsWith(it.second, ignoreCase = true)
        }.let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_language_title)
            .setSingleChoiceItems(languageNames, checkedItem) { dialog, which ->
                val (selectedName, selectedTag) = languages[which]
                val localeList = LocaleListCompat.forLanguageTags(selectedTag)
                // 套用至整個應用程式的語言設定（Android 13+ 原生相容支援）
                AppCompatDelegate.setApplicationLocales(localeList)

                Toast.makeText(
                    requireContext(),
                    getString(R.string.language_changed_toast, selectedName),
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
