package com.example.viewBinding

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.example.MainActivity
import com.example.MainActivity.Companion.mainActivity

/**
 * 支援 ViewBinding 的抽象 Fragment 基底類別
 *
 * 用途：
 * 1. 統一管理所有 Fragment 的 ViewBinding 初始化與視圖載入流程
 * 2. 在 [onDestroyView] 自動將 [binding] 設為 null，徹底避免 Fragment 視圖脫離後的記憶體流失 (Memory Leak)
 * 3. 於 [onResume] 自動向 [MainActivity] 回報當前活動的 Fragment 類別名稱
 * 4. 提供 [setLog] 等輔助日誌工具函式
 *
 * @param VB 對應 Fragment 佈局的 [ViewBinding] 介面型別
 */
abstract class BaseBindingFragment<VB : ViewBinding?> : Fragment() {

    /**
     * 當前 Fragment 所持有的 ViewBinding 實例
     * 僅在 [onCreateView] 至 [onDestroyView] 期間有效
     */
    var binding: VB? = null
        private set

    /**
     * Fragment 建立視圖時呼叫
     * 呼叫抽象方法 [onCreateViewBinding] 填充 binding，並將根視圖 (root) 回傳給 Fragment Manager
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = onCreateViewBinding(inflater)
        return binding?.root
    }

    /**
     * 視圖建立完成後的生命週期回呼，由子類別覆寫進行視圖元件的監聽與資料綁定
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    /**
     * 視圖銷毀時呼叫
     * 將 [binding] 設為 null，確保 View 階層能夠被 GC 正確回收，避免記憶體洩漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    /**
     * 抽象方法：由各 Fragment 子類別實作，負責填充具體的 ViewBinding 實例
     *
     * @param layoutInflater 佈局填充器
     * @return 具體的 [ViewBinding] 物件
     */
    protected abstract fun onCreateViewBinding(layoutInflater: LayoutInflater): VB?

    /**
     * 當 Fragment 恢復前景執行時呼叫
     * 自動記錄當前 Fragment 類別名稱至 [MainActivity.currentFragment]
     */
    override fun onResume() {
        mainActivity?.currentFragment = javaClass.simpleName
        super.onResume()
    }

    /**
     * 快速印出除錯 Log 工具函式
     *
     * @param mes 欲輸出的日誌訊息字串
     */
    fun setLog(mes: String) {
        Log.d(javaClass.simpleName, mes)
    }
}