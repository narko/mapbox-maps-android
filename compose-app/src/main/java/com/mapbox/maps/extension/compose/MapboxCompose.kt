package com.mapbox.maps.extension.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.NoOpUpdate
import coil.compose.rememberAsyncImagePainter
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.StyleContract
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.mapbox.maps.plugin.gestures.generated.GesturesSettingsInterface

@Composable
fun MapboxMap(
  modifier: Modifier = Modifier,
  mapInitOptions: MapInitOptions = MapInitOptions(LocalContext.current),
  styleExtension: StyleContract.StyleExtension? = null,
  update: (MapView) -> Unit = NoOpUpdate
) {
  if (LocalInspectionMode.current) {
    PlaceHolder(modifier, mapInitOptions)
  } else {
    AndroidView(
      factory = { context ->
        MapView(context, mapInitOptions)
      },
      modifier = modifier,
    ) { mapView ->
      mapView.apply(update)
      styleExtension?.let {
        mapView.getMapboxMap().loadStyle(it)
      }
    }
  }
}

@Composable
internal fun PlaceHolder(
  modifier: Modifier,
  mapInitOptions: MapInitOptions,
  placeholderColor: Color = Color(0xFFD2D2D9),
  strokeWidthInDp: Int = 5,
  strokeColor: Color = Color.Blue
) {
  var size by remember { mutableStateOf(Size(100f, 100f)) }
  Canvas(modifier = modifier.background(placeholderColor)) {
    size = this.size
    val canvasWidth = size.width
    val canvasHeight = size.height
    drawRect(
      color = strokeColor,
      size = size,
      style = Stroke(width = strokeWidthInDp.dp.toPx())
    )
    drawLine(
      start = Offset(x = canvasWidth, y = 0f),
      end = Offset(x = 0f, y = canvasHeight),
      strokeWidth = strokeWidthInDp.dp.toPx(),
      color = strokeColor
    )
    drawLine(
      start = Offset(x = 0f, y = 0f),
      end = Offset(x = canvasWidth, y = canvasHeight),
      strokeWidth = strokeWidthInDp.dp.toPx(),
      color = strokeColor
    )
  }
  Image(
    painter = rememberAsyncImagePainter(
      "https://api.mapbox.com/styles/v1/${(mapInitOptions.styleUri ?: Style.MAPBOX_STREETS).removePrefix("mapbox://styles/") }/static/${mapInitOptions.cameraOptions?.center?.longitude() ?: 0},${mapInitOptions.cameraOptions?.center?.latitude() ?: 0},${mapInitOptions.cameraOptions?.zoom ?: 0},${mapInitOptions.cameraOptions?.bearing ?: 0},${mapInitOptions.cameraOptions?.pitch ?: 0}/${size.width.toInt().coerceIn(1, 1280)}x${size.height.toInt().coerceIn(1, 1280)}?access_token=${mapInitOptions.resourceOptions.accessToken}"
    ),
    contentDescription = null,
    modifier = modifier
  )
}

fun GesturesSettingsInterface.applySettings(gesturesSettings: GesturesSettings) {
  this.updateSettings {
    rotateEnabled = gesturesSettings.rotateEnabled
    pinchToZoomEnabled = gesturesSettings.pinchToZoomEnabled
    scrollEnabled = gesturesSettings.scrollEnabled
    simultaneousRotateAndPinchToZoomEnabled =
      gesturesSettings.simultaneousRotateAndPinchToZoomEnabled
    pitchEnabled = gesturesSettings.pitchEnabled
    scrollMode = gesturesSettings.scrollMode
    doubleTapToZoomInEnabled = gesturesSettings.doubleTapToZoomInEnabled
    doubleTouchToZoomOutEnabled = gesturesSettings.doubleTouchToZoomOutEnabled
    quickZoomEnabled = gesturesSettings.quickZoomEnabled
    focalPoint = gesturesSettings.focalPoint
    pinchToZoomDecelerationEnabled = gesturesSettings.pinchToZoomDecelerationEnabled
    rotateDecelerationEnabled = gesturesSettings.rotateDecelerationEnabled
    scrollDecelerationEnabled = gesturesSettings.scrollDecelerationEnabled
    increaseRotateThresholdWhenPinchingToZoom =
      gesturesSettings.increaseRotateThresholdWhenPinchingToZoom
    increasePinchToZoomThresholdWhenRotating =
      gesturesSettings.increasePinchToZoomThresholdWhenRotating
    zoomAnimationAmount = gesturesSettings.zoomAnimationAmount
    pinchScrollEnabled = gesturesSettings.pinchScrollEnabled
  }
}