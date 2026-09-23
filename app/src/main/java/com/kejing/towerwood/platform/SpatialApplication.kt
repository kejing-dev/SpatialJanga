package com.kejing.towerwood.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.kejing.towerwood.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
