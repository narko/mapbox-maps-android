package com.mapbox.maps.renderer

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.mapbox.maps.logW
import com.mapbox.maps.renderer.MapboxRenderThread.Companion.TAG
import kotlin.math.pow

internal class FpsAdjuster {
  private var screenRefreshRate = -1
  private var currentMaxFps = -1
  private var screenRefreshIntervalNs = -1L
  private val expectedFrameRenderIntervalNs: Double
    get() = ONE_SECOND_NS / currentMaxFps
  private var lastFrameTimeNs = -1L

  private var totalFrameCounter = 0

  @Volatile
  private var fpsChangedListener: OnFpsChangedListener? = null

  @WorkerThread
  fun setScreenRefreshRate(screenRefreshRate: Int) {
    this.screenRefreshRate = screenRefreshRate
    this.screenRefreshIntervalNs = (ONE_SECOND_NS / screenRefreshRate).toLong()
    if (this.currentMaxFps == -1) {
      this.currentMaxFps = screenRefreshRate
    }
  }

  @WorkerThread
  fun updateMaxFps(updatedMaxFps: Int) {
    if (currentMaxFps != updatedMaxFps) {
      currentMaxFps = updatedMaxFps
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
    // check if we did miss VSYNC deadline meaning too much work was done in previous doFrame
    val maxPossibleFrameTimeNs = lastFrameTimeNs + screenRefreshIntervalNs + ONE_MILLISECOND_NS
    if (lastFrameTimeNs != -1L && maxPossibleFrameTimeNs >= frameTimeNs) {
      logW(TAG, "Most likely last frame was dropped, render thread is doing too much work!")
    }
    lastFrameTimeNs = frameTimeNs
    // perform frame pacing
    var proceedRendering = true


    // increase frame count and notify listener if needed
    totalFrameCounter++
    if (totalFrameCounter == currentMaxFps) {
      fpsChangedListener?.onFpsChanged(totalFrameCounter.toDouble())
      totalFrameCounter = 0
    }
    return proceedRendering
  }

  @AnyThread
  fun destroy() {
    fpsChangedListener = null
  }

  private companion object {
    private val ONE_SECOND_NS = 10.0.pow(9.0)
    private val ONE_MILLISECOND_NS = 10.0.pow(6.0)
  }
}