package com.example

import android.os.Bundle
import android.view.LayoutInflater
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.databinding.ActivityMainBinding
import com.example.viewBinding.BaseBindingActivity

/**
 * 應用程式的主 Activity
 * 
 * 職責：
 * 1. 繼承 [BaseBindingActivity]，使用 ViewBinding 自動管理畫面佈局綁定
 * 2. 作為單一 Activity 架構 (Single Activity Architecture) 的主要容器，掛載 NavHostFragment
 * 3. 協調 Navigation 元件進行 Fragment 之間的切換與返回操作
 */
class MainActivity : BaseBindingActivity<ActivityMainBinding>() {

    companion object {
        /**
         * 全域 MainActivity 實例參考，方便在特定情境（如 BaseBindingFragment 生命週期追蹤）存取當前狀態
         */
        @JvmField
        var mainActivity: MainActivity? = null
    }

    /**
     * Jetpack Navigation 的導覽控制器，負責管理畫面路由與返回堆疊
     */
    private lateinit var navController: NavController

    /**
     * 記錄當前顯示的 Fragment 類別名稱，由各 Fragment 於 onResume 時自動更新
     */
    var currentFragment: String = "HomeFragment"

    /**
     * 實作基底類別抽象方法，載入 ActivityMainBinding 實例
     *
     * @param layoutInflater 佈局填充器
     * @return 綁定後的 [ActivityMainBinding] 實例
     */
    override fun onCreateViewBinding(layoutInflater: LayoutInflater): ActivityMainBinding? {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    /**
     * Activity 建立時呼叫
     * 
     * 初始化流程：
     * 1. 呼叫父類別 onCreate，由 BaseBindingActivity 自動呼叫 setContentView 設定 ViewBinding root
     * 2. 指派靜態參考 mainActivity
     * 3. 尋找並初始化 NavHostFragment 與 NavController，建立畫面導覽機制
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = this

        // 取得佈局中的 NavHostFragment 並綁定 NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    /**
     * 處理頂部返回鍵或系統向上導航
     *
     * @return 若 NavController 成功處理返回則回傳 true，否則交由父類別處理
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
