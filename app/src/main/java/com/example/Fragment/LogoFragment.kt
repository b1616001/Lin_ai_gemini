package com.example.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.R
import com.example.databinding.FragmentLogoBinding
import com.example.viewBinding.BaseBindingFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 啟動與品牌展示畫面 (Logo / Splash Screen)
 *
 * 用途：
 * 1. 繼承 [BaseBindingFragment]，顯示品牌 Logo 與標題提示
 * 2. 啟動 3 秒自動倒數計時協程，倒數結束後自動導向首頁 [HomeFragment]
 * 3. 支援使用者點擊畫面立即略過倒數，提供流暢的使用者體驗
 * 4. 妥善管理協程生命週期，避免銷毀後觸發無效導航
 */
class LogoFragment : BaseBindingFragment<FragmentLogoBinding>() {

    /**
     * 倒數計時的協程工作物件，可用於使用者點擊跳過或畫面銷毀時立即取消
     */
    private var timerJob: Job? = null

    /**
     * 實作基底類別方法：載入 Logo 畫面的 ViewBinding
     */
    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentLogoBinding? {
        return FragmentLogoBinding.inflate(layoutInflater)
    }

    /**
     * 視圖初始化：設置點擊跳過監聽與自動倒數計時協程
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 允許使用者點擊畫面任意區域立即跳過等待，前往首頁
        binding?.root?.setOnClickListener {
            timerJob?.cancel()
            navigateToHome()
        }

        // 啟動 3 秒倒數計時協程
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            for (remaining in 3 downTo 1) {
                if (!isAdded) return@launch
                binding?.tvLogoTimerHint?.text = "${remaining}s..."
                delay(1000L)
            }
            if (isAdded) {
                navigateToHome()
            }
        }
    }

    /**
     * 透過 Jetpack Navigation 安全切換至首頁 [HomeFragment]
     * 加入例外防護，避免快速連續點擊或 Fragment 脫離時拋出例外
     */
    private fun navigateToHome() {
        if (!isAdded) return
        try {
            findNavController().navigate(R.id.action_logoFragment_to_homeFragment)
        } catch (_: Exception) {
            // 防範連續點擊或導覽目標重複觸發
        }
    }

    /**
     * 視圖銷毀時釋放資源，取消倒數計時協程
     */
    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
    }
}
