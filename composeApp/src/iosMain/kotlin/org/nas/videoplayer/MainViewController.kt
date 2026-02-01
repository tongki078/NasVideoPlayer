package org.nas.videoplayer

import androidx.compose.ui.window.ComposeUIViewController
import org.nas.videoplayer.data.createDatabaseDriver

fun MainViewController() = ComposeUIViewController { 
    val driver = createDatabaseDriver()
    App(driver = driver) 
}
