package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("相機", appName)
  }

  @Test
  fun `verify photo repository creates valid file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = PhotoRepository(context)
    val photoFile = repository.createNewPhotoFile()
    assertNotNull(photoFile)
    assertEquals("jpg", photoFile.extension)
  }
}
