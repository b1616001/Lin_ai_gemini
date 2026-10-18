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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertTrue(appName == "相機" || appName == "Camera")
  }

  @Test
  fun `verify photo repository creates valid file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = PhotoRepository(context)
    val photoFile = repository.createNewPhotoFile()
    assertNotNull(photoFile)
    assertEquals("jpg", photoFile.extension)
  }

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

  @Test
  fun `verify navigation from Logo to Home to Camera`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val navHostFragment = activity.supportFragmentManager
          .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Navigate to Home
        navController.navigate(R.id.action_logoFragment_to_homeFragment)
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)

        // Navigate to Camera
        navController.navigate(R.id.action_homeFragment_to_cameraFragment)
        assertEquals(R.id.cameraFragment, navController.currentDestination?.id)

        // Navigate Up back to Home
        navController.navigateUp()
        assertEquals(R.id.homeFragment, navController.currentDestination?.id)
      }
    }
  }
}
