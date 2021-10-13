package com.mapbox.maps.plugin.viewport.state

/**
 * Observer that gets notified whenever [ViewportCameraState] changes.
 */
interface  ViewportCameraStateChangedObserver {
  /**
   * Called whenever [ViewportCameraState] changes.
   * @param viewportCameraState current states
   */
  fun onViewportCameraStateChanged(viewportCameraState: ViewportCameraState)
}