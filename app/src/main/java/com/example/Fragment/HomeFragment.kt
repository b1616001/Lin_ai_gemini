package com.example.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.HomeAdapter
import com.example.HomeFeatureItem
import com.example.R
import com.example.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLanguageBadge()
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        setupLanguageBadge()
    }

    private fun setupLanguageBadge() {
        val currentTag = getCurrentLanguageTag()
        val displayName = when {
            currentTag.startsWith("en", ignoreCase = true) -> "English"
            currentTag.startsWith("ja", ignoreCase = true) -> "日本語"
            currentTag.startsWith("zh-cn", ignoreCase = true) || currentTag.startsWith("zh-hans", ignoreCase = true) -> "簡體中文"
            else -> "繁體中文"
        }
        binding.tvCurrentLanguageBadge.text = displayName
    }

    private fun getCurrentLanguageTag(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            return appLocales.get(0)?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
        }
        return Locale.getDefault().toLanguageTag()
    }

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
                        // Safe guard against multiple clicks
                    }
                }
            )
        )

        binding.rvFeatures.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = HomeAdapter(featureItems)
        }
    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
