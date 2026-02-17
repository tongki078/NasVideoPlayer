package org.nas.videoplayer

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation differently (gesture, etc.)
}
