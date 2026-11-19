package com.amarjeet.motionspot

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private enum class State { IDLE, ARMING, WATCHING, ALARM }

    private var state = State.IDLE

    private lateinit var previewView: PreviewView
    private lateinit var overlay: BoxOverlayView
    private lateinit var status: TextView
    private lateinit var mainButton: Button
    private lateinit var testButton: Button
    private lateinit var sensitivityBar: SeekBar
    private lateinit var volumeBar: SeekBar
    private lateinit var saverCheck: CheckBox
    private lateinit var timerSpinner: Spinner
    private lateinit var prefs: SharedPreferences
    private lateinit var siren: SirenPlayer

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewBound: Boolean? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val detector = MotionDetector()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var detecting = false

    @Volatile
    private var lastAnalysisMs = 0L

    @Volatile
    private var currentBox = RectF(0.2f, 0.2f, 0.8f, 0.8f)

    private var testing = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                status.text = "Camera permission is needed. Allow it in the app settings."
            }
        }

    private val analyzer = ImageAnalysis.Analyzer { image ->
        try {
            val now = SystemClock.elapsedRealtime()
            if (detecting && now - lastAnalysisMs >= 200L) {
                lastAnalysisMs = now
                if (detector.analyze(image, currentBox)) {
                    detecting = false
                    runOnUiThread { onMotion() }
                }
            }
        } finally {
            image.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("motionspot", MODE_PRIVATE)
        siren = SirenPlayer(this)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)
        mainButton = findViewById(R.id.mainButton)
        testButton = findViewById(R.id.testButton)
        sensitivityBar = findViewById(R.id.sensitivityBar)
        volumeBar = findViewById(R.id.volumeBar)
        saverCheck = findViewById(R.id.saverCheck)
        timerSpinner = findViewById(R.id.timerSpinner)

        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        sensitivityBar.progress = prefs.getInt("sens", 60)
        volumeBar.progress = prefs.getInt("vol", 100)
        saverCheck.isChecked = prefs.getBoolean("saver", true)
        timerSpinner.setSelection(prefs.getInt("timer", 0))
        detector.sensitivity = sensitivityBar.progress / 100f
        if (prefs.contains("bl")) {
            currentBox = RectF(
                prefs.getFloat("bl", 0.2f),
                prefs.getFloat("bt", 0.2f),
                prefs.getFloat("br", 0.8f),
                prefs.getFloat("bb", 0.8f)
            )
        }
        overlay.box = RectF(currentBox)

        sensitivityBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                detector.sensitivity = progress / 100f
                prefs.edit().putInt("sens", progress).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt("vol", progress).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        saverCheck.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("saver", checked).apply()
        }

        timerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putInt("timer", pos).apply()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        overlay.onBoxChanged = { r ->
            currentBox = RectF(r)
            prefs.edit()
                .putFloat("bl", r.left)
                .putFloat("bt", r.top)
                .putFloat("br", r.right)
                .putFloat("bb", r.bottom)
                .apply()
            detector.reset()
        }

        mainButton.setOnClickListener {
            when (state) {
                State.IDLE -> beginArming()
                State.ARMING -> stopAll("Cancelled")
                State.WATCHING -> stopAll("Stopped watching")
                State.ALARM -> stopAll("Siren stopped")
            }
        }

        testButton.setOnClickListener { toggleTest() }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        updateUi()
    }

    override fun onStop() {
        super.onStop()
        if (state == State.ARMING || state == State.WATCHING || testing) {
            stopAll("Watching stopped because the app left the screen. Keep the app open.")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        siren.stop()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases(true)
            } catch (e: Exception) {
                status.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases(showPreview: Boolean) {
        val provider = cameraProvider ?: return
        if (previewBound == showPreview) return
        try {
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, analyzer)

            val useCases = ArrayList<UseCase>()
            useCases.add(analysis)
            if (showPreview) {
                val preview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build()
                    )
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                useCases.add(preview)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases.toTypedArray()
            )
            previewBound = showPreview
        } catch (e: Exception) {
            status.text = "Camera error: ${e.message}"
        }
    }

    private fun setBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    private fun beginArming() {
        if (cameraProvider == null) {
            status.text = "Camera not ready yet"
            return
        }
        if (testing) {
            siren.stop()
            testing = false
        }
        state = State.ARMING
        overlay.editable = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateUi()
        countdown(5)
    }

    private fun countdown(n: Int) {
        if (state != State.ARMING) return
        if (n <= 0) {
            beginWatching()
            return
        }
        status.text = "Starting in $n s. Move away from the area."
        handler.postDelayed({ countdown(n - 1) }, 1000L)
    }

    private fun beginWatching() {
        state = State.WATCHING
        detector.reset()
        overlay.active = true
        overlay.invalidate()
        if (saverCheck.isChecked) {
            bindUseCases(false)
            previewView.visibility = View.INVISIBLE
            setBrightness(0.02f)
        }
        status.text = "Watching the selected area"
        handler.postDelayed({
            if (state == State.WATCHING) detecting = true
        }, 1500L)
        val minutes = when (timerSpinner.selectedItemPosition) {
            1 -> 60
            2 -> 120
            3 -> 240
            else -> 0
        }
        if (minutes > 0) {
            handler.postDelayed({
                if (state == State.WATCHING) stopAll("Auto-stopped after the timer")
            }, minutes * 60_000L)
        }
        updateUi()
    }

    private fun onMotion() {
        if (state != State.WATCHING) return
        state = State.ALARM
        detecting = false
        handler.removeCallbacksAndMessages(null)
        setBrightness(-1f)
        previewView.visibility = View.VISIBLE
        bindUseCases(true)
        siren.start(volumeBar.progress.coerceAtLeast(10))
        status.text = "Motion detected! Siren is on."
        handler.postDelayed({
            if (state == State.ALARM) stopAll("Siren timed out after 2 minutes")
        }, 120_000L)
        updateUi()
    }

    private fun stopAll(message: String) {
        handler.removeCallbacksAndMessages(null)
        detecting = false
        siren.stop()
        testing = false
        state = State.IDLE
        setBrightness(-1f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        previewView.visibility = View.VISIBLE
        overlay.editable = true
        overlay.active = false
        overlay.invalidate()
        bindUseCases(true)
        status.text = message
        updateUi()
    }

    private fun toggleTest() {
        if (state != State.IDLE) return
        if (testing) {
            handler.removeCallbacksAndMessages(null)
            siren.stop()
            testing = false
            status.text = "Test stopped"
        } else {
            siren.start(volumeBar.progress.coerceAtLeast(10))
            testing = true
            status.text = "Testing siren for 4 seconds"
            handler.postDelayed({
                if (testing) {
                    siren.stop()
                    testing = false
                    status.text = "Test finished"
                    updateUi()
                }
            }, 4000L)
        }
        updateUi()
    }

    private fun updateUi() {
        val (label, color) = when (state) {
            State.IDLE -> Pair("Start watching", Color.parseColor("#2E7D32"))
            State.ARMING -> Pair("Cancel", Color.parseColor("#F57C00"))
            State.WATCHING -> Pair("Stop watching", Color.parseColor("#F57C00"))
            State.ALARM -> Pair("STOP SIREN", Color.parseColor("#C62828"))
        }
        mainButton.text = label
        ViewCompat.setBackgroundTintList(mainButton, ColorStateList.valueOf(color))
        testButton.text = if (testing) "Stop test" else "Test siren"
        testButton.isEnabled = state == State.IDLE
    }
}
