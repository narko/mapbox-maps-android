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
import kotlin.math.roundToInt

internal class FpsManager {
  private var userDefinedRefreshRate = -1
  private var userDefinedRefreshPeriodNs = -1L

  private var deviceRefreshRate = -1
  private var deviceRefreshPeriodNs = -1L
  private var previousRefreshNs = -1L
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
      userDefinedRefreshPeriodNs = ONE_SECOND_NS / updatedMaxFps
      if (BuildConfig.DEBUG) {
        logI(
          TAG,
          "User set max FPS to $userDefinedRefreshRate (${userDefinedRefreshPeriodNs / 10.0.pow(6.0)} as refresh period)"
        )
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
    if (userDefinedRefreshPeriodNs == -1L && fpsChangedListener == null) {
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
    var proceedRendering = true
    if (userDefinedRefreshPeriodNs != -1L) {
      // TODO pacing
    }
    // we always increase choreographer tick by one + add number of skipped frames for consistent results
    choreographerTicks += skippedNow + 1
    return proceedRendering
  }

  fun postRender() {
    fpsChangedListener?.let { listener ->
      val frameRenderTimeNs = System.nanoTime() - preRenderTimeNs
      frameRenderTimeAccumulatedNs += frameRenderTimeNs
      // normally we update FPS counter once a second
      if (choreographerTicks >= userDefinedRefreshRate) {
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

  private fun calculateFps(listener: OnFpsChangedListener) {
    val droppedFps = choreographerSkips.toDouble() / choreographerTicks
    val fps = (1.0 - droppedFps) * deviceRefreshRate
    val averageRenderTimeNs = frameRenderTimeAccumulatedNs.toDouble() / choreographerTicks
    listener.onFpsChanged(fps)
    if (BuildConfig.DEBUG) {
      logW(
        TAG,
        "VSYNC based FPS is ${fps.roundToInt()}," +
          " average core rendering time is ${averageRenderTimeNs / 10.0.pow(6.0)} ms," +
          " missed $choreographerSkips out of $choreographerTicks VSYNC pulses"
      )
    }
    frameRenderTimeAccumulatedNs = 0L
    choreographerTicks = 0
    choreographerSkips = 0
  }

  @AnyThread
  fun destroy() {
    fpsChangedListener = null
  }

  private companion object {
    private const val TAG = "Mbgl-FpsAdjuster"
    private const val IDLE_TIMEOUT_MS = 50L
    private val ONE_SECOND_NS = 10.0.pow(9.0).toLong()
    private val ONE_MILLISECOND_NS = 10.0.pow(6.0).toLong()
  }
}