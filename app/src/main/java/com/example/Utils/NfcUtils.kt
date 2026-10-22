package com.example.Utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.provider.Settings
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NFC (近距離無線通訊) 標籤感應與資料解析工具類別 (NfcUtils)
 *
 * 主要功能：
 * 1. 檢測裝置是否支援 NFC 硬體晶片
 * 2. 檢測 NFC 功能是否已在系統設定中開啟
 * 3. 透過現代 [NfcAdapter.enableReaderMode] 註冊高靈敏讀卡模式
 * 4. 解析感應卡片之 UID (十六進位 Hex 與十進位 Decimal)
 * 5. 解析卡片支援的 RFID/NFC 協定清單 (IsoDep, NfcA, Mifare, Ndef 等)
 * 6. 解碼 NDEF (NFC Data Exchange Format) 訊息內之純文字與 URI 內容
 * 7. 提供跳轉至系統 NFC 開啟設定頁面的 Intent
 */
class NfcUtils(private val context: Context) {

    /**
     * 讀取的 NFC 標籤資訊模型
     */
    data class NfcTagData(
        val uidHex: String,
        val uidDec: String,
        val techList: List<String>,
        val ndefRecords: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun formattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    private val nfcAdapter: NfcAdapter? by lazy {
        try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 檢查當前裝置是否具備 NFC 硬體模組
     */
    fun isNfcSupported(): Boolean {
        return nfcAdapter != null
    }

    /**
     * 檢查系統 NFC 功能是否已處於開啟狀態
     */
    fun isNfcEnabled(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    /**
     * 開啟現代 Reader Mode 監聽 NFC 靠卡事件
     * 支援 IsoDep (A/B)、NfcA、NfcB、NfcF、NfcV 等主流晶片
     *
     * @param activity 宿主 Activity
     * @param onTagDiscovered 感應到 NFC 標籤時的回呼（已解析為 [NfcTagData]）
     */
    fun enableReaderMode(activity: Activity, onTagDiscovered: (NfcTagData) -> Unit) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }

        try {
            adapter.enableReaderMode(activity, { tag ->
                val tagData = parseTag(tag)
                activity.runOnUiThread {
                    onTagDiscovered(tagData)
                }
            }, flags, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 停止 Reader Mode 監聽
     */
    fun disableReaderMode(activity: Activity) {
        try {
            nfcAdapter?.disableReaderMode(activity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解析原生 [Tag] 物件為易讀的 [NfcTagData]
     */
    fun parseTag(tag: Tag): NfcTagData {
        val idBytes = tag.id ?: byteArrayOf()
        val hexUid = bytesToHex(idBytes)
        val decUid = bytesToDecimal(idBytes)

        val techList = tag.techList.map { fullTech ->
            fullTech.substringAfterLast(".")
        }

        val ndefRecords = mutableListOf<String>()

        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                val cachedMessage = ndef.cachedNdefMessage
                if (cachedMessage != null) {
                    ndefRecords.addAll(parseNdefMessage(cachedMessage))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return NfcTagData(
            uidHex = if (hexUid.isNotEmpty()) hexUid else "Unknown",
            uidDec = if (decUid.isNotEmpty()) decUid else "--",
            techList = techList,
            ndefRecords = ndefRecords
        )
    }

    /**
     * 解析 [NdefMessage] 內的各項記錄
     */
    fun parseNdefMessage(message: NdefMessage): List<String> {
        val results = mutableListOf<String>()
        for (record in message.records) {
            val text = parseNdefRecord(record)
            if (text.isNotBlank()) {
                results.add(text)
            }
        }
        return results
    }

    /**
     * 解析單一 [NdefRecord] 文字或 URI 內容
     */
    private fun parseNdefRecord(record: NdefRecord): String {
        return try {
            val tnf = record.tnf
            val type = record.type

            if (tnf == NdefRecord.TNF_WELL_KNOWN) {
                if (type.contentEquals(NdefRecord.RTD_TEXT)) {
                    // RTD_TEXT 文字解碼
                    val payload = record.payload
                    if (payload.isEmpty()) return ""
                    val isUtf8 = (payload[0].toInt() and 0x80) == 0
                    val langCodeLength = payload[0].toInt() and 0x3F
                    val charset = if (isUtf8) Charset.forName("UTF-8") else Charset.forName("UTF-16")
                    String(payload, langCodeLength + 1, payload.size - langCodeLength - 1, charset)
                } else if (type.contentEquals(NdefRecord.RTD_URI)) {
                    // RTD_URI 連結解碼
                    record.toUri()?.toString() ?: ""
                } else {
                    String(record.payload, Charset.defaultCharset())
                }
            } else {
                String(record.payload, Charset.defaultCharset())
            }
        } catch (_: Exception) {
            String(record.payload, Charset.defaultCharset())
        }
    }

    /**
     * 建立開啟系統 NFC 設定頁面的 Intent
     */
    fun createNfcSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NFC_SETTINGS)
    }

    /**
     * 將 Byte Array 轉為大寫十六進位字串（格式為 04:A2:3B:5C...）
     */
    fun bytesToHex(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    /**
     * 將 Byte Array 轉為十進位長整數位元組表示
     */
    fun bytesToDecimal(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        var result = 0L
        for (b in bytes) {
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result.toString()
    }
}
