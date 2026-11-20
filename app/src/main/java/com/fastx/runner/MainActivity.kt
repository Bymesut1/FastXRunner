package com.fastx.runner

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var ui: UiView
    private lateinit var game: Game
    private lateinit var sound: SoundEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        game = Game(getSharedPreferences("fxr", MODE_PRIVATE))
        game.onExit = { runOnUiThread { finish() } }
        sound = SoundEngine(game)
        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(3)
        glView.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        glView.setRenderer(GameRenderer(this, game))
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        ui = UiView(this, game)
        val root = FrameLayout(this)
        root.addView(glView)
        root.addView(ui)
        setContentView(root)
        hideSystem()
    }

    @Suppress("DEPRECATION")
    private fun hideSystem() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        sound.start()
        hideSystem()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        sound.stop()
        game.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystem()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when (game.state) {
            State.RACING, State.COUNTDOWN -> game.pause()
            State.PAUSED -> game.resume()
            State.SETTINGS -> game.closeSettings()
            State.MENU -> super.onBackPressed()
            else -> game.toMenu()
        }
    }
}
