package cc.pixp.slipstream

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import cc.pixp.slipstream.ui.SessionApp
import cc.pixp.slipstream.ui.SlipstreamTheme

val LocalPipMode = staticCompositionLocalOf { false }
val LocalPlayerFullscreen = staticCompositionLocalOf { false }

class MainActivity : ComponentActivity() {
    private var pipMode by mutableStateOf(false)
    private var fullscreen by mutableStateOf(false)
    private var previousOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var pipActive=false
    private var videoBounds:Rect?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CompositionLocalProvider(LocalPipMode provides pipMode,LocalPlayerFullscreen provides fullscreen) { SlipstreamTheme { SessionApp() } } }
    }
    fun setPlayerFullscreen(value:Boolean) {
        if(fullscreen==value) return
        if(value) previousOrientation=requestedOrientation
        fullscreen=value
        requestedOrientation=if(value) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else previousOrientation
        val controller=androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
        controller.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if(value) controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    private fun pipParams(): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        setAspectRatio(Rational(16,9))
        videoBounds?.let {setSourceRectHint(it)}
        if(Build.VERSION.SDK_INT>=31) {setAutoEnterEnabled(pipActive);setSeamlessResizeEnabled(true)}
    }.build()
    fun updatePip(active:Boolean,bounds:Rect?=videoBounds) {
        pipActive=active
        if(!isInPictureInPictureMode) videoBounds=bounds
        if(packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) setPictureInPictureParams(pipParams())
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if(Build.VERSION.SDK_INT<31 && pipActive && !isInPictureInPictureMode && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode(pipParams())
        }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode:Boolean,newConfig:Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
        pipMode=isInPictureInPictureMode
    }
}

internal fun Context.playbackActivity(): MainActivity? {
    var current=this
    while(current is ContextWrapper) {
        if(current is MainActivity) return current
        current=current.baseContext
    }
    return current as? MainActivity
}
