package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.ScanPhase
import com.example.model.ScanProgress
import com.example.ui.components.ScanProgressHeader
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun scan_progress_header_screenshot() {
    val progress = ScanProgress(
      phase = ScanPhase.COMPUTING_HASHES,
      processedCount = 42,
      totalCount = 100,
      reclaimableSizeBytes = 150_000_000L,
      currentFile = "sample_track.mp3"
    )

    composeTestRule.setContent {
      MaterialTheme {
        ScanProgressHeader(
          progress = progress,
          onStartScan = {},
          onAutoCleanAll = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/scan_progress_header.png")
  }
}

