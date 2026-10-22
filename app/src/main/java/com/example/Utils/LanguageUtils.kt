package com.example.Utils

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * 多國語系管理與動態切換工具類別 (LanguageUtils)
 *
 * 主要功能：
 * 1. 取得當前應用程式或系統的語言標籤 (BCP 47 language tag)
 * 2. 提供目前語言名稱轉換與顯示 (例如：繁體中文、English、日本語、簡體中文)
 * 3. 封裝應用程式動態語系切換 (相容 Android 13+ 原生 Per-app language preferences)
 * 4. 提供標準 Material 3 多國語言選擇對話框 (Dialog)
 */
object LanguageUtils {

    /**
     * 支援的語言選項資料結構
     *
     * @param tag BCP 47 語言標籤（如 "zh-TW", "en", "ja", "zh-CN"）
     * @param stringResId 對應 strings.xml 中的字串資源 ID
     */
    data class SupportedLanguage(
        val tag: String,
        val stringResId: Int
    )

    /**
     * 應用程式支援的語系列表
     */
    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage(tag = "zh-TW", stringResId = R.string.lang_zh_tw),
        SupportedLanguage(tag = "en", stringResId = R.string.lang_en),
        SupportedLanguage(tag = "ja", stringResId = R.string.lang_ja),
        SupportedLanguage(tag = "zh-CN", stringResId = R.string.lang_zh_cn)
    )

    /**
     * 取得當前應用程式所設定的語言代碼標籤 (BCP 47 language tag)
     * 優先取得 AppCompatDelegate 所設定的 App 專屬語系，若無則回傳系統預設語系
     *
     * @return 語言代碼字串（例如 "zh-TW", "en", "ja"）
     */
    fun getCurrentLanguageTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            return appLocales.get(0)?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
        }
        return Locale.getDefault().toLanguageTag()
    }

    /**
     * 依據語言代碼標籤取得友好的顯示名稱
     *
     * @param languageTag 語言標籤，預設為當前語言 [getCurrentLanguageTag]
     * @return 顯示名稱（例如 "繁體中文"、"English"、"日本語"、"簡體中文"）
     */
    fun getLanguageDisplayName(languageTag: String = getCurrentLanguageTag()): String {
        return when {
            languageTag.startsWith("en", ignoreCase = true) -> "English"
            languageTag.startsWith("ja", ignoreCase = true) -> "日本語"
            languageTag.startsWith("zh-cn", ignoreCase = true) ||
                    languageTag.startsWith("zh-hans", ignoreCase = true) -> "簡體中文"
            else -> "繁體中文"
        }
    }

    /**
     * 套用指定語言至整個應用程式
     *
     * @param languageTag 欲切換的 BCP 47 語言代碼（如 "zh-TW", "en", "ja", "zh-CN"）
     */
    fun applyLanguage(languageTag: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * 顯示多國語言切換對話框
     *
     * @param context Context 實例（建議使用 Activity 或 Fragment Context）
     * @param onLanguageChanged 語言切換成功後的回呼函式，帶入切換後語言名稱與標籤
     */
    fun showLanguageDialog(
        context: Context,
        onLanguageChanged: ((displayName: String, languageTag: String) -> Unit)? = null
    ) {
        val languages = SUPPORTED_LANGUAGES.map { option ->
            context.getString(option.stringResId) to option.tag
        }

        val languageNames = languages.map { it.first }.toTypedArray()
        val currentTag = getCurrentLanguageTag()
        val checkedIndex = languages.indexOfFirst {
            currentTag.startsWith(it.second, ignoreCase = true)
        }.let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.select_language_title)
            .setSingleChoiceItems(languageNames, checkedIndex) { dialog, which ->
                val (selectedName, selectedTag) = languages[which]
                applyLanguage(selectedTag)

                Toast.makeText(
                    context,
                    context.getString(R.string.language_changed_toast, selectedName),
                    Toast.LENGTH_SHORT
                ).show()

                onLanguageChanged?.invoke(selectedName, selectedTag)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
