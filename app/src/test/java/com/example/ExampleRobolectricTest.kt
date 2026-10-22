package com.example

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.Utils.CameraUtils
import com.example.Utils.GpsUtils
import com.example.Utils.LanguageUtils
import com.example.Utils.NfcUtils
import com.example.Utils.RoomUtils
import com.example.room.AppDatabase
import com.example.room.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 核心業務邏輯、CameraUtils、PhotoAdapter 與畫面導航 Robolectric 本地 JVM 單元測試
 *
 * 用途：
 * 1. 驗證應用程式字串資源與語系載入
 * 2. 驗證 [PhotoRepository] 與 [CameraUtils] 本地檔案產生與命名規則
 * 3. 驗證 [CameraUtils] 鏡頭方向切換與閃光燈循環邏輯
 * 4. 驗證 [PhotoAdapter] 圖片清單提交與項目數量計算
 * 5. 驗證 Navigation 導覽圖初始目的地為 [LogoFragment]
 * 6. 驗證導覽路徑：LogoFragment -> HomeFragment -> CameraFragment -> 回到 HomeFragment
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
   * 測試 CameraUtils 拍照路徑建立、鏡頭切換與閃光燈切換功能
   */
  @Test
  fun `verify camera utils operations`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cameraUtils = CameraUtils(context)

    // 驗證建立照片檔案
    val photoFile = cameraUtils.createNewPhotoFile()
    assertNotNull(photoFile)
    assertEquals("jpg", photoFile.extension)

    // 驗證鏡頭方向切換
    val frontFacing = cameraUtils.toggleLensFacing(CameraSelector.LENS_FACING_BACK)
    assertEquals(CameraSelector.LENS_FACING_FRONT, frontFacing)
    val backFacing = cameraUtils.toggleLensFacing(CameraSelector.LENS_FACING_FRONT)
    assertEquals(CameraSelector.LENS_FACING_BACK, backFacing)

    // 驗證閃光燈模式切換循環：AUTO -> ON -> OFF -> AUTO
    val onMode = cameraUtils.getNextFlashMode(ImageCapture.FLASH_MODE_AUTO)
    assertEquals(ImageCapture.FLASH_MODE_ON, onMode)
    val offMode = cameraUtils.getNextFlashMode(ImageCapture.FLASH_MODE_ON)
    assertEquals(ImageCapture.FLASH_MODE_OFF, offMode)
    val autoMode = cameraUtils.getNextFlashMode(ImageCapture.FLASH_MODE_OFF)
    assertEquals(ImageCapture.FLASH_MODE_AUTO, autoMode)

    // 驗證刪除不存在檔案回傳 false
    val item = PhotoItem(photoFile)
    assertFalse(cameraUtils.deletePhoto(item))
  }

  /**
   * 測試 PhotoAdapter 圖片列表資料綁定與清單提交更新
   */
  @Test
  fun `verify PhotoAdapter displays photo list`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeFile1 = File(context.cacheDir, "test1.jpg").apply { createNewFile() }
    val fakeFile2 = File(context.cacheDir, "test2.jpg").apply { createNewFile() }

    val photoList = listOf(PhotoItem(fakeFile1), PhotoItem(fakeFile2))

    val adapter = PhotoAdapter(
      isHorizontal = true,
      onPhotoClick = {},
      onDeleteClick = {}
    )

    adapter.submitList(photoList)
    assertEquals(2, adapter.itemCount)
    assertEquals(fakeFile1.absolutePath, adapter.currentList[0].file.absolutePath)
    assertEquals(fakeFile2.absolutePath, adapter.currentList[1].file.absolutePath)

    // 清理暫存檔
    fakeFile1.delete()
    fakeFile2.delete()
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

  /**
   * 測試 LanguageUtils 語言標籤與顯示名稱解析
   */
  @Test
  fun `verify LanguageUtils tag and display name mapping`() {
    assertEquals("English", LanguageUtils.getLanguageDisplayName("en"))
    assertEquals("日本語", LanguageUtils.getLanguageDisplayName("ja"))
    assertEquals("簡體中文", LanguageUtils.getLanguageDisplayName("zh-CN"))
    assertEquals("繁體中文", LanguageUtils.getLanguageDisplayName("zh-TW"))
    assertTrue(LanguageUtils.SUPPORTED_LANGUAGES.isNotEmpty())
    assertNotNull(LanguageUtils.getCurrentLanguageTag())
  }

  /**
   * 測試 GpsUtils 格式化函式與功能
   */
  @Test
  fun `verify GpsUtils operations`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gpsUtils = GpsUtils(context)

    val coords = gpsUtils.formatCoordinates(25.033964, 121.564468)
    assertEquals("25.033964, 121.564468", coords)

    val speed = gpsUtils.formatSpeed(10.0f)
    assertTrue(speed.contains("36.0 km/h"))

    val altitude = gpsUtils.formatAltitude(150.5)
    assertTrue(altitude.contains("150.5 m"))

    val accuracy = gpsUtils.formatAccuracy(3.2f)
    assertTrue(accuracy.contains("3.2 m"))

    val intent = gpsUtils.createLocationSettingsIntent()
    assertNotNull(intent.action)
  }

  /**
   * 測試 NfcUtils 格式轉換與設定 Intent
   */
  @Test
  fun `verify NfcUtils operations`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val nfcUtils = NfcUtils(context)

    val bytes = byteArrayOf(0x04, 0xA2.toByte(), 0x3B.toByte(), 0x5C.toByte())
    val hex = nfcUtils.bytesToHex(bytes)
    assertEquals("04:A2:3B:5C", hex)

    val dec = nfcUtils.bytesToDecimal(bytes)
    assertTrue(dec.isNotEmpty())

    val intent = nfcUtils.createNfcSettingsIntent()
    assertNotNull(intent.action)
  }

  /**
   * 測試 Room 資料庫 CRUD 業務邏輯 (新增、修改、刪除、搜尋)
   */
  @Test
  fun `verify Room Database CRUD operations`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val repository = RoomUtils.getNoteRepository(context)

      // 清空資料庫確保乾淨環境
      repository.clearAll()

      // 1. 新增記事 (Create)
      val testNote = NoteEntity(
        title = "測試筆記標題",
        content = "這是測試內容",
        category = "工作"
      )
      val insertedId = repository.insert(testNote)
      assertTrue(insertedId > 0)

      // 2. 查詢記事 (Read)
      val listAfterInsert = repository.allNotes.first()
      assertEquals(1, listAfterInsert.size)
      assertEquals("測試筆記標題", listAfterInsert[0].title)

      // 3. 修改記事 (Update)
      val noteToUpdate = listAfterInsert[0].copy(title = "已修改的筆記標題")
      repository.update(noteToUpdate)
      val listAfterUpdate = repository.allNotes.first()
      assertEquals("已修改的筆記標題", listAfterUpdate[0].title)

      // 4. 關鍵字搜尋 (Search)
      val searchResult = repository.searchNotes("修改").first()
      assertEquals(1, searchResult.size)
      val searchEmpty = repository.searchNotes("不存在的關鍵字").first()
      assertEquals(0, searchEmpty.size)

      // 5. 刪除記事 (Delete)
      repository.delete(listAfterUpdate[0])
      val listAfterDelete = repository.allNotes.first()
      assertEquals(0, listAfterDelete.size)

      // 6. 範例資料批次注入
      RoomUtils.populateSampleData(repository)
      val sampleList = repository.allNotes.first()
      assertTrue(sampleList.size >= 4)

      // 清理
      repository.clearAll()
    }
  }

  /**
   * 測試 Navigation 導航支援跳轉至 GpsFragment、NfcFragment、RoomFragment
   */
  @Test
  fun `verify navigation to Gps, Nfc, and Room fragments`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val navHostFragment = activity.supportFragmentManager
          .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 前往首頁
        navController.navigate(R.id.action_logoFragment_to_homeFragment)

        // 測試導覽至 GPS 畫面
        navController.navigate(R.id.action_homeFragment_to_gpsFragment)
        assertEquals(R.id.gpsFragment, navController.currentDestination?.id)
        navController.navigateUp()
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)

        // 測試導覽至 NFC 畫面
        navController.navigate(R.id.action_homeFragment_to_nfcFragment)
        assertEquals(R.id.nfcFragment, navController.currentDestination?.id)
        navController.navigateUp()
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)

        // 測試導覽至 Room 畫面
        navController.navigate(R.id.action_homeFragment_to_roomFragment)
        assertEquals(R.id.roomFragment, navController.currentDestination?.id)
        navController.navigateUp()
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)
      }
    }
  }
}
