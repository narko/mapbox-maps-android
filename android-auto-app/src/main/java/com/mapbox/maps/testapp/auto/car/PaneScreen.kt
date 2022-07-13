package com.mapbox.maps.testapp.auto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template

class PaneScreen(carContext: CarContext) : Screen(carContext) {
  override fun onGetTemplate(): Template {
    val pane = Pane.Builder()
      .setLoading(true)
      .build()

    return PaneTemplate.Builder(pane)
      .setHeaderAction(Action.BACK)
      .build()
  }
}