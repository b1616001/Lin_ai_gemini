package com.example

import android.content.Context
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 核心業務邏輯與畫面導航 Robolectric 本地 JVM 單元測試
 *
 * 用途：
 * 1. 驗證應用程式字串資源與語系載入
 * 2. 驗證 [PhotoRepository] 本地檔案產生與命名規則
 * 3. 驗證 Navigation 導覽圖初始目的地為 [LogoFragment]
 * 4. 驗證導覽路徑：LogoFragment -> HomeFragment -> CameraFragment -> 回到 HomeFragment
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  /**
   * 測試 Context 字串資源讀取，確認 app_name 正確載入
   */
  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertTrue(appName == "相機" || appName == "Camera")
  }

  /**
   * 測試 PhotoRepository 建立新照片檔案路徑，確保副檔名為 jpg 且非空
   */
  @Test
  fun `verify photo repository creates valid file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = PhotoRepository(context)
    val photoFile = repository.createNewPhotoFile()
    assertNotNull(photoFile)
    assertEquals("jpg", photoFile.extension)
  }

  /**
   * 測試 MainActivity 啟動後，Navigation 導覽元件預設起始畫面為 LogoFragment
   */
  @Test
  fun `verify navigation component starts at LogoFragment`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val navHostFragment = activity.supportFragmentManager
          .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        assertNotNull(navHostFragment)
        val navController = navHostFragment!!.navController
        assertEquals(R.id.logoFragment, navController.currentDestination?.id)
      }
    }
  }

  /**
   * 測試畫面完整導航生命週期：Logo -> Home -> Camera -> 返回 Home
   */
  @Test
  fun `verify navigation from Logo to Home to Camera`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val navHostFragment = activity.supportFragmentManager
          .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 導向首頁 HomeFragment
        navController.navigate(R.id.action_logoFragment_to_homeFragment)
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)

        // 導向相機 CameraFragment
        navController.navigate(R.id.action_homeFragment_to_cameraFragment)
        assertEquals(R.id.cameraFragment, navController.currentDestination?.id)

        // 向上返回至首頁 HomeFragment
        navController.navigateUp()
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)
      }
    }
  }
}
