package com.mapbox.maps.renderer

import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.os.postDelayed
import com.mapbox.maps.BuildConfig
import com.mapbox.maps.logI
import com.mapbox.maps.logW
import kotlin.math.pow

internal class FpsManager {
  private var userDefinedRefreshRate = -1
  private var userDefinedRatio = 0.0

  private var deviceRefreshRate = -1
  private var deviceRefreshPeriodNs = -1L
  private var previousRefreshNs = -1L
  private var previousDrawnFrameIndex = 0
  private var frameRenderTimeAccumulatedNs = 0L

  private var preRenderTimeNs = -1L
  private var choreographerTicks = 0
  private var choreographerSkips = 0

  @Volatile
  private var fpsChangedListener: OnFpsChangedListener? = null

  private var handler: Handler? = null

  @WorkerThread
  fun prepareHandler() {
    Looper.myLooper()?.let { looper ->
      handler = Handler(looper)
    } ?: logW(
      TAG,
      "Looper could not be found for Mapbox render thread! " +
        "Some FPS reports may be inaccurate."
    )
  }

  @WorkerThread
  fun setScreenRefreshRate(screenRefreshRate: Int) {
    deviceRefreshPeriodNs = ONE_SECOND_NS / screenRefreshRate
    deviceRefreshRate = screenRefreshRate
    if (userDefinedRefreshRate == -1) {
      userDefinedRefreshRate = screenRefreshRate
    } else {
      userDefinedRatio = userDefinedRefreshRate.toDouble() / deviceRefreshRate
      logI(TAG, "User defined ratio is $userDefinedRatio")
    }
    if (BuildConfig.DEBUG) {
      logI(
        TAG,
        "Device screen frequency is $screenRefreshRate," +
          " current refresh rate is $userDefinedRefreshRate"
      )
    }
  }

  @WorkerThread
  fun updateMaxFps(updatedMaxFps: Int) {
    if (userDefinedRefreshRate != updatedMaxFps) {
      userDefinedRefreshRate = updatedMaxFps
      if (BuildConfig.DEBUG) {
        logI(TAG, "User set max FPS to $userDefinedRefreshRate")
      }
    }
  }

  @AnyThread
  fun setFpsChangedListener(fpsChangedListener: OnFpsChangedListener?) {
    this.fpsChangedListener = fpsChangedListener
  }

  /**
   * Return true if rendering should happen this frame and false otherwise.
   *
   * @param frameTimeNs time value from Choreographer in [System.nanoTime] timebase.
   */
  fun preRender(frameTimeNs: Long): Boolean {
    // clear any scheduled task as new render call is about to happen
    handler?.removeCallbacksAndMessages(null)
    // no need to perform neither pacing nor FPS calculation when setMaxFps / setOnFpsChangedListener was called by the user
    if (userDefinedRatio == 0.0 && fpsChangedListener == null) {
      return true
    }
    preRenderTimeNs = System.nanoTime()
    var skippedNow = 0
    // check if we did miss VSYNC deadline meaning too much work was done in previous doFrame
    if (previousRefreshNs != -1L && frameTimeNs - previousRefreshNs > deviceRefreshPeriodNs + ONE_MILLISECOND_NS) {
      skippedNow =
        ((frameTimeNs - previousRefreshNs) / (deviceRefreshPeriodNs + ONE_MILLISECOND_NS)).toInt()
      choreographerSkips += skippedNow
      if (BuildConfig.DEBUG) {
        logW(
          TAG,
          "Skipped $skippedNow VSYNC pulses since last actual render," +
            " total skipped in measurement period $choreographerSkips / $choreographerTicks"
        )
      }
    }
    previousRefreshNs = frameTimeNs
    // we always increase choreographer tick by one + add number of skipped frames for consistent results
    choreographerTicks += skippedNow + 1
    if (userDefinedRatio != 0.0) {
      return performPacing()
    }
    return true
  }

  fun postRender() {
    fpsChangedListener?.let { listener ->
      val frameRenderTimeNs = System.nanoTime() - preRenderTimeNs
      frameRenderTimeAccumulatedNs += frameRenderTimeNs
      // normally we update FPS counter once a second
      if (choreographerTicks >= deviceRefreshRate) {
        calculateFps(listener)
      } else {
        // however to produce correct values we also update FPS after IDLE_TIMEOUT_MS
        // otherwise when updating the map after it was IDLE first update will report
        // huge delta between new frame and last frame (as we're using dirty rendering)
        handler?.postDelayed(IDLE_TIMEOUT_MS) {
          calculateFps(listener)
          previousRefreshNs = -1
        }
      }
      preRenderTimeNs = -1L
    }
  }

  private fun performPacing(): Boolean {
    val drawnFrameIndex = (choreographerTicks * userDefinedRatio).toInt()
    if (BuildConfig.DEBUG) {
      logI(
        TAG,
        "Performing pacing, current index=$drawnFrameIndex," +
          " previous drawn=$previousDrawnFrameIndex, proceed with rendering=${drawnFrameIndex > previousDrawnFrameIndex}"
      )
    }
    if (drawnFrameIndex > previousDrawnFrameIndex) {
      previousDrawnFrameIndex = drawnFrameIndex
      return true
    }
    choreographerSkips++
    return false
  }

  private fun calculateFps(listener: OnFpsChangedListener) {
    val droppedFps = choreographerSkips.toDouble() / choreographerTicks
    val fps = (1.0 - droppedFps) * deviceRefreshRate
    val averageRenderTimeNs = frameRenderTimeAccumulatedNs.toDouble() / choreographerTicks
    listener.onFpsChanged(fps)
    if (BuildConfig.DEBUG) {
      logW(
        TAG,
        "VSYNC based FPS is $fps," +
          " average core rendering time is ${averageRenderTimeNs / 10.0.pow(6.0)} ms," +
          " missed $choreographerSkips out of $choreographerTicks VSYNC pulses"
      )
    }
    previousDrawnFrameIndex = 0
    frameRenderTimeAccumulatedNs = 0L
    choreographerTicks = 0
    choreographerSkips = 0
  }

  @AnyThread
  fun destroy() {
    fpsChangedListener = null
  }

  private companion object {
    private const val TAG = "Mbgl-FpsManager"
    private const val IDLE_TIMEOUT_MS = 50L
    private val ONE_SECOND_NS = 10.0.pow(9.0).toLong()
    private val ONE_MILLISECOND_NS = 10.0.pow(6.0).toLong()
  }
}