package com.example.viewBinding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

/**
 * 支援 ViewBinding 的抽象 Activity 基底類別
 *
 * 用途：
 * 1. 簡化所有 Activity 的 ViewBinding 初始化與 `setContentView` 樣板程式碼
 * 2. 提供全域的 binding 存取屬性，確保類型安全與空值安全
 * 3. 封裝全螢幕沉浸式模式等常見 Activity 工具函式
 *
 * @param VB 對應 Activity 佈局的 [ViewBinding] 介面型別
 */
abstract class BaseBindingActivity<VB : ViewBinding?> : AppCompatActivity() {

    /**
     * 當前 Activity 所持有的 ViewBinding 實例
     * 僅允許基底類別在生命週期中賦值，外部僅能讀取
     */
    var binding: VB? = null
        private set

    /**
     * Activity 建立時統一進行 ViewBinding 初始化並載入根視圖
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = onCreateViewBinding(layoutInflater)
        binding?.root?.let { setContentView(it) }
    }

    /**
     * 抽象方法：由各子類別實作，負責填充具體的 ViewBinding 實例
     *
     * @param layoutInflater 佈局填充器
     * @return 具體的 [ViewBinding] 物件
     */
    protected abstract fun onCreateViewBinding(layoutInflater: LayoutInflater): VB?

    /**
     * Activity 恢復前景執行時觸發，可供子類別擴充全螢幕或狀態同步邏輯
     */
    override fun onResume() {
        super.onResume()
        // 可依需求呼叫 fullScreenImmersive 啟用沉浸式全螢幕
    }

    /**
     * 全螢幕沉浸式模式工具函式
     * 隱藏系統狀態列 (Status Bar) 與導航列 (Navigation Bar)，並啟用 Sticky 沉浸式互動
     *
     * @param view 目標視圖（通常傳入 binding.root）
     */
    fun fullScreenImmersive(view: View) {
        val uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        view.systemUiVisibility = uiOptions
    }
}