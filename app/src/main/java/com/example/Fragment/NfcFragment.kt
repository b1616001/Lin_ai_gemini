package com.example.Fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.R
import com.example.Utils.NfcUtils
import com.example.databinding.FragmentNfcBinding
import com.example.viewBinding.BaseBindingFragment

/**
 * NFC 晶片感應與標籤資料展示畫面 (NfcFragment)
 *
 * 功能：
 * 1. 繼承 [BaseBindingFragment]
 * 2. 透過 [NfcUtils] 偵測 NFC 支援與開啟狀態
 * 3. 註冊 Reader Mode 於背景捕捉任何感應卡片 (NfcA, Mifare, IsoDep, Ndef 等)
 * 4. 展示卡片 UID、規格、NDEF 內容與感應歷史
 * 5. 支援複製 UID 與跳轉系統 NFC 設定
 */
class NfcFragment : BaseBindingFragment<FragmentNfcBinding>() {

    private lateinit var nfcUtils: NfcUtils
    private var lastScannedUid = ""
    private val historyList = mutableListOf<String>()

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentNfcBinding? {
        return FragmentNfcBinding.inflate(layoutInflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nfcUtils = NfcUtils(requireContext())

        setupListeners()
        updateNfcStatusUi()
    }

    override fun onResume() {
        super.onResume()
        updateNfcStatusUi()
        if (nfcUtils.isNfcSupported() && nfcUtils.isNfcEnabled()) {
            activity?.let { act ->
                nfcUtils.enableReaderMode(act) { tagData ->
                    handleTagScanned(tagData)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        activity?.let { act ->
            nfcUtils.disableReaderMode(act)
        }
    }

    private fun setupListeners() {
        // 返回按鈕
        binding?.btnNfcBack?.setOnClickListener {
            findNavController().navigateUp()
        }

        // 開啟系統 NFC 設定
        binding?.btnOpenNfcSettings?.setOnClickListener {
            try {
                startActivity(nfcUtils.createNfcSettingsIntent())
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "無法開啟 NFC 設定頁", Toast.LENGTH_SHORT).show()
            }
        }

        // 複製卡片 UID
        binding?.btnCopyUid?.setOnClickListener {
            if (lastScannedUid.isNotBlank() && lastScannedUid != "Unknown") {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("NFC UID", lastScannedUid)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.nfc_uid_copied_toast, Toast.LENGTH_SHORT).show()
            }
        }

        // 清除歷史紀錄
        binding?.btnClearHistory?.setOnClickListener {
            historyList.clear()
            binding?.tvHistoryList?.text = getString(R.string.nfc_history_empty)
        }
    }

    private fun updateNfcStatusUi() {
        binding?.apply {
            when {
                !nfcUtils.isNfcSupported() -> {
                    tvNfcStatusBadge.text = getString(R.string.nfc_status_unsupported)
                    tvNfcStatusBadge.setBackgroundColor(requireContext().getColor(R.color.delete_red))
                    tvNfcPromptMessage.text = getString(R.string.nfc_status_unsupported)
                    tvNfcHardwareStatus.text = "硬體不支援 NFC 模組"
                }
                !nfcUtils.isNfcEnabled() -> {
                    tvNfcStatusBadge.text = getString(R.string.nfc_status_disabled)
                    tvNfcStatusBadge.setBackgroundColor(requireContext().getColor(R.color.delete_red))
                    tvNfcPromptMessage.text = getString(R.string.nfc_status_disabled)
                    tvNfcHardwareStatus.text = "請點擊下方按鈕前往設定開啟 NFC"
                }
                else -> {
                    tvNfcStatusBadge.text = "NFC 運作中"
                    tvNfcStatusBadge.setBackgroundColor(requireContext().getColor(R.color.lin_blue_dark))
                    tvNfcPromptMessage.text = getString(R.string.nfc_status_ready)
                    tvNfcHardwareStatus.text = "已進入高靈敏度 Reader Mode"
                }
            }
        }
    }

    private fun handleTagScanned(data: NfcUtils.NfcTagData) {
        lastScannedUid = data.uidHex

        binding?.apply {
            tvNfcUidHex.text = data.uidHex
            tvNfcUidDec.text = "Decimal: ${data.uidDec}"
            tvNfcTechList.text = if (data.techList.isNotEmpty()) {
                data.techList.joinToString(", ")
            } else {
                "--"
            }

            tvNfcNdefContent.text = if (data.ndefRecords.isNotEmpty()) {
                data.ndefRecords.joinToString("\n")
            } else {
                getString(R.string.nfc_no_ndef)
            }

            // 更新歷史紀錄
            val historyEntry = "[${data.formattedTime()}] UID: ${data.uidHex}"
            historyList.add(0, historyEntry)
            tvHistoryList.text = historyList.joinToString("\n")

            Toast.makeText(requireContext(), "感應成功：${data.uidHex}", Toast.LENGTH_SHORT).show()
        }
    }
}
