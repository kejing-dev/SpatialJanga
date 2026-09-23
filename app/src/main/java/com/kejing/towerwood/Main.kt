package com.kejing.towerwood

import com.kejing.towerwood.content.HomeVolume
import com.kejing.towerwood.ui.theme.TowerTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            TowerTheme {
                HomeVolume()
            }
        }
    }
