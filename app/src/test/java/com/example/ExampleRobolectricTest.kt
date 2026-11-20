package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.FileImportExportManager
import com.example.engine.ProjectStorageManager
import com.example.engine.SampleProjects
import com.example.model.AppUiMode
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
    assertEquals("Kodanima 2D", appName)
  }

  @Test
  fun `sample project serialization test`() {
    val demoProject = SampleProjects.createBouncingBallProject()
    assertTrue(demoProject.frames.size >= 8)

    val jsonString = FileImportExportManager.projectToJson(demoProject)
    assertNotNull(jsonString)
    assertTrue(jsonString.contains("Bouncing Ball"))

    val parsedProject = FileImportExportManager.projectFromJson(jsonString)
    assertEquals(demoProject.name, parsedProject.name)
    assertEquals(demoProject.frames.size, parsedProject.frames.size)
  }

  @Test
  fun `project storage manager save and load test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val demoProject = SampleProjects.createBouncingBallProject()
    val saved = ProjectStorageManager.saveProject(context, demoProject)
    assertTrue(saved)

    val allProjects = ProjectStorageManager.loadAllProjects(context)
    assertTrue(allProjects.isNotEmpty())

    val duplicated = ProjectStorageManager.duplicateProject(context, demoProject.id)
    assertNotNull(duplicated)
    assertTrue(duplicated!!.name.contains("Cópia"))
  }

  @Test
  fun `ui mode enum test`() {
    assertEquals("Aparência Mobile", AppUiMode.MOBILE.labelPt)
    assertEquals("Aparência PC (Desktop)", AppUiMode.PC.labelPt)
  }

  @Test
  fun `splash variants never repeat consecutively and assets exist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var previous = 0
    val seenFirstCycle = mutableSetOf<Int>()

    for (launch in 1..15) {
      com.example.ui.components.ColdStartSplashManager.resetForTesting()
      val picked = com.example.ui.components.ColdStartSplashManager.pickNextVariant(context)
      assertTrue("Variant $picked out of range", picked in 1..5)
      if (previous != 0) {
        assertTrue("Splash repeated consecutively: $picked", picked != previous)
      }
      if (launch <= 5) {
        seenFirstCycle.add(picked)
      }
      previous = picked
    }
    assertEquals("First 5 launches must cover all 5 splash artworks", setOf(1, 2, 3, 4, 5), seenFirstCycle)

    for (v in 1..5) {
      val name = if (v == 1) "splash/splash_poster_1.jpg" else "splash/splash_poster_$v.png"
      context.assets.open(name).use { stream ->
        assertTrue("Asset $name must be non-empty", stream.available() > 0)
      }
      val pcm = com.example.engine.SplashAudioEngine.synthesizePs2ChordPcm(v, 44100)
      assertTrue("Splash audio PCM for variant $v must be audible", pcm.any { kotlin.math.abs(it.toInt()) > 1000 })
    }
  }
}
