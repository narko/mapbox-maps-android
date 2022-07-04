package com.mapbox.maps.renderer

import androidx.annotation.AnyThread
import com.mapbox.maps.renderer.egl.EGLCore

internal class FpsAdjuster(
  private val eglCore: EGLCore,
  private val renderThread: MapboxRenderThread,
) {

  private var userDefinedMaxFps: Int
  private var fpsChangedListener: OnFpsChangedListener? = null

  fun updateMaxFps(updatedMaxFps: Int) {

  }

  fun setFpsChangedListener(fpsChangedListener: OnFpsChangedListener) {

  }

  fun preRender() {

  }

  fun postRender() {
    fpsChangedListener?.let {
      val fps = 1E9 / (actualEndRenderTimeNanos - timeElapsed)
      if (timeElapsed != 0L) {
        it.onFpsChanged(fps)
      }
      timeElapsed = actualEndRenderTimeNanos
    }
  }

  @AnyThread
  fun destroy() {
    fpsChangedListener = null
  }

}