package com.mapbox.maps.testapp.auto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

class SearchScreen(carContext: CarContext) : Screen(carContext) {

  override fun onGetTemplate(): Template {
    return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
      override fun onSearchTextChanged(searchText: String) {

      }

      override fun onSearchSubmitted(searchText: String) {

      }
    })
      .setHeaderAction(Action.BACK)
      .build()
  }
}