package com.amarjeet.motionspot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val root = findViewById<View>(R.id.splashRoot)
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(600L).start()
        root.setOnClickListener { openMain() }
    }

    override fun onStart() {
        super.onStart()
        handler.postDelayed({ openMain() }, 2200L)
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    private fun openMain() {
        if (launched) return
        launched = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
