package com.buddy.wakeforge

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.buddy.wakeforge.databinding.ActivityAlarmBinding
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * The full-screen mission UI. This is the whole point of the app: instead of
 * a generic "slide to dismiss," the user must complete a category-appropriate
 * challenge. Three missions ship here: MATH (any category), SHAKE
 * (accelerometer, no permission needed), and CAMERA_MOTION (opens the
 * camera for a user-configurable duration and watches for real movement).
 * Adding a future mission type is a new `when` branch here plus its own
 * small UI section — the surrounding plumbing (service, receiver,
 * scheduler) doesn't change.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null
    private var mathAnswer: Int = 0
    private var shakeTarget: Int = 15
    private var alarmId: Int = 0
    private var difficulty: Int = 0
    private var pulseAnimator: ValueAnimator? = null

    // Camera mission state
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var cameraCountDownTimer: CountDownTimer? = null
    private val motionDetectedInWindow = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        blockScreenshotsAndRecentsPreview()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Alarm"
        val categoryName = intent.getStringExtra(AlarmScheduler.EXTRA_CATEGORY) ?: Category.GENERAL.name
        val missionName = intent.getStringExtra(AlarmScheduler.EXTRA_MISSION) ?: MissionType.MATH.name
        difficulty = intent.getIntExtra(AlarmScheduler.EXTRA_DIFFICULTY, 0)
        val cameraDurationMinutes = intent.getIntExtra(AlarmScheduler.EXTRA_CAMERA_DURATION_MINUTES, 10)
        val category = Category.valueOf(categoryName)
        val mission = MissionType.valueOf(missionName)

        applyCategoryTheme(category)
        binding.alarmTitle.text = title
        startGentlePulse(binding.alarmTitle)

        when (mission) {
            MissionType.MATH -> setUpMathMission(difficulty)
            MissionType.SHAKE -> setUpShakeMission(difficulty)
            MissionType.CAMERA_MOTION -> setUpCameraMission(difficulty, cameraDurationMinutes)
        }
    }

    /**
     * Each category gets its own gradient mood rather than one generic alarm
     * screen — this is the cheapest, highest-impact thing to make the app
     * feel designed rather than default: energetic warm gradient for Gym,
     * focused cool gradient for Student, calm teal for General.
     */
    private fun applyCategoryTheme(category: Category) {
        val (gradientRes, watermarkEmoji) = when (category) {
            Category.STUDENT -> R.drawable.gradient_student to "📚"
            Category.GYM -> R.drawable.gradient_gym to "💪"
            Category.GENERAL -> R.drawable.gradient_general to "⭐"
            Category.OFFICE -> R.drawable.gradient_office to "💼"
        }
        binding.root.background = ContextCompat.getDrawable(this, gradientRes)
        binding.categoryWatermark.text = watermarkEmoji
    }

    private fun startGentlePulse(target: View) {
        pulseAnimator = ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0.55f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    /**
     * Security/privacy: without FLAG_SECURE, the alarm screen (which shows the
     * event title, and for the Camera mission a live camera feed) would appear
     * in screenshots, screen recordings, and the Recent Apps thumbnail. This
     * blocks all three at the OS level.
     */
    private fun blockScreenshotsAndRecentsPreview() {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun resetMissionViews() {
        binding.mathProblem.visibility = View.GONE
        binding.mathAnswer.visibility = View.GONE
        binding.mathAnswerRule.visibility = View.GONE
        binding.mathSubmit.visibility = View.GONE
        binding.shakeProgress.visibility = View.GONE
        binding.shakeBar.visibility = View.GONE
        binding.cameraPreview.visibility = View.GONE
        binding.cameraActivityBar.visibility = View.GONE
        binding.cameraTimeRemaining.visibility = View.GONE
    }

    // ---- MATH mission ----

    private fun setUpMathMission(difficulty: Int) {
        resetMissionViews()
        binding.mathProblem.visibility = View.VISIBLE
        binding.mathAnswer.visibility = View.VISIBLE
        binding.mathAnswerRule.visibility = View.VISIBLE
        binding.mathSubmit.visibility = View.VISIBLE

        val maxValue = when (difficulty) {
            0 -> 20
            1 -> 100
            else -> 500
        }
        val a = Random.nextInt(2, maxValue)
        val b = Random.nextInt(2, maxValue)
        val useSubtraction = Random.nextBoolean()
        mathAnswer = if (useSubtraction) a - b else a + b
        binding.mathProblem.text = if (useSubtraction) "$a − $b" else "$a + $b"

        binding.mathSubmit.setOnClickListener {
            val entered = binding.mathAnswer.text.toString().toIntOrNull()
            if (entered == mathAnswer) {
                completeMissionAndDismiss()
            } else {
                binding.mathAnswer.error = getString(R.string.wrong_answer)
            }
        }
    }

    // ---- SHAKE mission ----

    private fun setUpShakeMission(difficulty: Int) {
        resetMissionViews()
        binding.shakeProgress.visibility = View.VISIBLE
        binding.shakeBar.visibility = View.VISIBLE

        shakeTarget = 10 + difficulty * 10
        binding.shakeBar.max = shakeTarget
        binding.shakeProgress.text = getString(R.string.shake_progress, 0, shakeTarget)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector { count ->
            runOnUiThread {
                binding.shakeBar.progress = count
                binding.shakeProgress.text = getString(R.string.shake_progress, count, shakeTarget)
                if (count >= shakeTarget) {
                    completeMissionAndDismiss()
                }
            }
        }
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager?.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    // ---- CAMERA mission ----
    // Opens the camera for up to `durationMinutes` (user-configurable when the
    // alarm was created) and watches for real movement via CameraMotionAnalyzer.
    // Once enough active movement has accumulated (target scales with
    // difficulty), the mission completes. If the whole duration elapses
    // without enough confirmed movement, the camera shuts off and control
    // hands to a Math mission instead of leaving the user stuck ringing
    // forever with a dead camera check.

    private fun setUpCameraMission(difficulty: Int, durationMinutes: Int) {
        resetMissionViews()

        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            binding.missionInstruction.text = getString(R.string.camera_permission_needed)
            setUpMathMission(difficulty)
            return
        }

        binding.missionInstruction.text = getString(R.string.camera_instruction)
        binding.cameraPreview.visibility = View.VISIBLE
        binding.cameraActivityBar.visibility = View.VISIBLE
        binding.cameraTimeRemaining.visibility = View.VISIBLE

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()

        val requiredActiveMillis = when (difficulty) {
            0 -> 10_000L
            1 -> 20_000L
            else -> 30_000L
        }
        val totalDurationMillis = durationMinutes.coerceAtLeast(1) * 60_000L
        startCameraCountdown(totalDurationMillis, requiredActiveMillis)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                // Camera unavailable for some reason (in use elsewhere, hardware
                // issue, etc.) — don't strand the user on a dead camera screen.
                binding.missionInstruction.text = getString(R.string.camera_permission_needed)
                setUpMathMission(difficulty)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val executor = cameraExecutor ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, CameraMotionAnalyzer { isActive ->
            if (isActive) motionDetectedInWindow.set(true)
        })

        val cameraSelector = try {
            if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        } catch (e: Exception) {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, analysis)
        } catch (e: Exception) {
            binding.missionInstruction.text = getString(R.string.camera_permission_needed)
            setUpMathMission(difficulty)
        }
    }

    private fun startCameraCountdown(totalDurationMillis: Long, requiredActiveMillis: Long) {
        var accumulatedActiveMillis = 0L
        binding.cameraActivityBar.max = 100

        cameraCountDownTimer = object : CountDownTimer(totalDurationMillis, CAMERA_TICK_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                if (motionDetectedInWindow.getAndSet(false)) {
                    accumulatedActiveMillis += CAMERA_TICK_INTERVAL_MS
                }
                val progress = ((accumulatedActiveMillis * 100) / requiredActiveMillis).toInt().coerceAtMost(100)
                binding.cameraActivityBar.progress = progress
                binding.cameraTimeRemaining.text =
                    getString(R.string.camera_time_remaining, formatMillisAsClock(millisUntilFinished))

                if (accumulatedActiveMillis >= requiredActiveMillis) {
                    stopCameraMission()
                    completeMissionAndDismiss()
                }
            }

            override fun onFinish() {
                // Time's up without enough confirmed movement — release the
                // camera and fall back to a mission that can't get stuck.
                stopCameraMission()
                binding.missionInstruction.text = getString(R.string.camera_timeout_fallback)
                setUpMathMission(difficulty)
            }
        }.start()
    }

    private fun formatMillisAsClock(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun stopCameraMission() {
        cameraCountDownTimer?.cancel()
        cameraCountDownTimer = null
        cameraProvider?.unbindAll()
    }

    // ---- shared completion path ----

    private fun completeMissionAndDismiss() {
        pulseAnimator?.cancel()
        stopCameraMission()
        AlarmRingService.stopRinging(this)
        logCompletionForStreakAndXp()
        finish()
    }

    private fun logCompletionForStreakAndXp() {
        val xp = StreakCalculator.xpForDifficulty(difficulty)
        lifecycleScope.launch {
            val dao = AppDatabase.get(applicationContext).missionLogDao()
            // Normally there's an open (fired-but-not-completed) row AlarmRingService
            // created the moment this alarm started ringing — mark that one done rather
            // than inserting a second row, so History doesn't double-count this alarm.
            val open = dao.getLatestOpenLog(alarmId)
            if (open != null) {
                dao.update(
                    open.copy(
                        completed = true,
                        completedAtMillis = System.currentTimeMillis(),
                        xpEarned = xp
                    )
                )
            } else {
                dao.insert(
                    MissionLog(
                        alarmEventId = alarmId,
                        alarmTitle = binding.alarmTitle.text?.toString() ?: "Alarm",
                        scheduledAtMillis = System.currentTimeMillis(),
                        completedAtMillis = System.currentTimeMillis(),
                        completed = true,
                        xpEarned = xp
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        pulseAnimator?.cancel()
        sensorManager?.unregisterListener(shakeDetector)
        stopCameraMission()
        cameraExecutor?.shutdown()
        super.onDestroy()
    }

    // Block the back button — the whole point is you can't dismiss without
    // finishing the mission. Users can still use the physical volume-down
    // to silence sound if they're truly stuck; that's a deliberate MVP
    // escape hatch worth revisiting once real users test this.
    override fun onBackPressed() {
        // no-op by design
    }

    companion object {
        private const val CAMERA_TICK_INTERVAL_MS = 500L
    }
}
