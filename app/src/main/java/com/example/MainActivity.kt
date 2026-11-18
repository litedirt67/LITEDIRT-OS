package com.example

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.VirtualOsApp

class MainActivity : ComponentActivity() {

    private var inPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inPipMode = isInPictureInPictureMode

        setContent {
            VirtualOsApp(
                isInPictureInPictureMode = inPipMode,
                onEnterPictureInPicture = { triggerPictureInPicture() }
            )
        }
    }

    private fun triggerPictureInPicture() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .build()
        runCatching {
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode = isInPictureInPictureMode
    }
}
