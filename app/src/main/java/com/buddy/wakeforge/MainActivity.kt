package com.buddy.wakeforge

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buddy.wakeforge.databinding.ActivityMainBinding
import com.buddy.wakeforge.databinding.DialogAddAlarmBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter
    private val dao by lazy { AppDatabase.get(applicationContext).alarmDao() }
    private val missionLogDao by lazy { AppDatabase.get(applicationContext).missionLogDao() }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    // Requested proactively as soon as the user picks the Camera mission, so
    // permission is (ideally) already settled well before the alarm ever
    // fires — AlarmActivity still re-checks and falls back gracefully if not.
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, getString(R.string.camera_permission_needed), Toast.LENGTH_LONG).show()
            }
        }

    // Fed by whichever add/edit dialog is currently open, since the ringtone
    // picker launcher has to be registered once up front here (Android's
    // Activity Result API requires that), not created fresh per-dialog.
    private var pendingRingtonePickCallback: ((Uri?) -> Unit)? = null

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            pendingRingtonePickCallback?.invoke(uri)
            pendingRingtonePickCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Same reasoning as AlarmActivity: routine/event titles are personal, so keep
        // them out of screenshots, screen recordings, and the Recent Apps thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AlarmAdapter(
            onToggle = { event, checked -> onToggleAlarm(event, checked) },
            onDelete = { event -> onDeleteAlarm(event) },
            onEdit = { event -> showAddAlarmDialog(event) }
        )
        binding.alarmList.layoutManager = LinearLayoutManager(this)
        binding.alarmList.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddAlarmDialog() }
        binding.navHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        lifecycleScope.launch {
            dao.observeAll().collect { events ->
                adapter.submitList(events)
                binding.emptyState.visibility =
                    if (events.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        lifecycleScope.launch {
            missionLogDao.observeAll().collect { logs ->
                val streak = StreakCalculator.currentStreakDays(logs)
                val xp = StreakCalculator.totalXp(logs)
                binding.streakText.text = getString(R.string.streak_label, streak)
                binding.xpText.text = getString(R.string.xp_label, xp)
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureExactAlarmPermission(): Boolean {
        if (!AlarmScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(
                this,
                "Please allow Wakeforge to schedule exact alarms so your alarms fire on time.",
                Toast.LENGTH_LONG
            ).show()
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
            return false
        }
        return true
    }

    private fun onToggleAlarm(event: AlarmEvent, enabled: Boolean) {
        lifecycleScope.launch {
            val updated = event.copy(isEnabled = enabled)
            dao.update(updated)
            if (enabled) {
                if (ensureExactAlarmPermission()) AlarmScheduler.schedule(this@MainActivity, updated)
            } else {
                AlarmScheduler.cancel(this@MainActivity, updated)
            }
        }
    }

    private fun onDeleteAlarm(event: AlarmEvent) {
        lifecycleScope.launch {
            AlarmScheduler.cancel(this@MainActivity, event)
            // Fix: deleting an alarm only cancelled its *future* schedule —
            // if this exact alarm was already ringing at the moment you hit
            // delete, the foreground service kept playing because nothing
            // ever told it to stop. Passing event.id means this only ever
            // silences THIS alarm, never a different one that might be
            // legitimately ringing at the same time.
            AlarmRingService.stopRinging(this@MainActivity, event.id)
            dao.delete(event)
        }
    }

    private fun updateAndReschedule(event: AlarmEvent) {
        lifecycleScope.launch {
            AlarmScheduler.cancel(this@MainActivity, event)
            dao.update(event)
            if (event.isEnabled && ensureExactAlarmPermission()) {
                AlarmScheduler.schedule(this@MainActivity, event)
            }
        }
    }

    /**
     * Shared dialog for both adding a new alarm and editing an existing one.
     * Pass [existing] to pre-fill every field and save via update+reschedule
     * instead of insert — this is the entire "tap an alarm to edit it"
     * feature; AlarmAdapter's onEdit calls this the same way the FAB does.
     */
    private fun showAddAlarmDialog(existing: AlarmEvent? = null) {
        val dialogBinding = DialogAddAlarmBinding.inflate(layoutInflater)

        val calendar = Calendar.getInstance().apply {
            if (existing != null) {
                timeInMillis = existing.timeInMillis
            } else {
                add(Calendar.MINUTE, 1) // sensible default: next minute
            }
        }
        var selectedRingtoneUri: String? = existing?.ringtoneUri

        fun updateTimeButtonLabel() {
            dialogBinding.pickTimeButton.text = "%02d:%02d".format(
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)
            )
        }
        updateTimeButtonLabel()

        dialogBinding.pickTimeButton.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1) // rolls to tomorrow if time already passed today
                    }
                    updateTimeButtonLabel()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        fun updateSoundButtonLabel() {
            val name = selectedRingtoneUri?.let { uriString ->
                try {
                    RingtoneManager.getRingtone(this, Uri.parse(uriString))?.getTitle(this)
                } catch (e: Exception) {
                    null
                }
            } ?: getString(R.string.default_alarm_sound)
            dialogBinding.pickSoundButton.text = getString(R.string.sound_label, name)
        }
        updateSoundButtonLabel()

        dialogBinding.pickSoundButton.setOnClickListener {
            val currentUri = selectedRingtoneUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
            pendingRingtonePickCallback = { uri ->
                // null comes back if the user picked "Default alarm sound" —
                // that's exactly what we want selectedRingtoneUri to become.
                selectedRingtoneUri = uri?.toString()
                updateSoundButtonLabel()
            }
            ringtonePickerLauncher.launch(pickerIntent)
        }

        fun updateCameraDurationLabel(minutes: Int) {
            dialogBinding.cameraDurationLabel.text = getString(R.string.camera_duration_label, formatDurationMinutes(minutes))
        }

        fun updateDifficultyLabel(progress: Int) {
            val labelRes = when (progress) {
                0 -> R.string.difficulty_easy
                1 -> R.string.difficulty_medium
                else -> R.string.difficulty_hard
            }
            dialogBinding.difficultyLabel.text = getString(labelRes)
        }

        // Pre-fill from the alarm being edited BEFORE any listeners below are
        // attached — otherwise checking "Gym" would fire the category
        // listener and silently force the mission back to "Shake" even if
        // this alarm was saved with a different, deliberately-chosen mission.
        if (existing != null) {
            dialogBinding.inputTitle.setText(existing.title)
            dialogBinding.categoryGroup.check(
                when (existing.category) {
                    Category.GYM -> R.id.radioGym
                    Category.GENERAL -> R.id.radioGeneral
                    Category.STUDENT -> R.id.radioStudent
                    Category.OFFICE -> R.id.radioOffice
                }
            )
            dialogBinding.missionGroup.check(
                when (existing.missionType) {
                    MissionType.SHAKE -> R.id.radioShake
                    MissionType.CAMERA_MOTION -> R.id.radioCamera
                    MissionType.MATH -> R.id.radioMath
                }
            )
            dialogBinding.difficultySeek.progress = existing.difficulty
            val cameraIndex = CAMERA_DURATION_OPTIONS_MIN.indexOf(existing.cameraDurationMinutes)
                .let { if (it >= 0) it else 2 }
            dialogBinding.cameraDurationSeek.progress = cameraIndex
            dialogBinding.cameraDurationGroup.visibility =
                if (existing.missionType == MissionType.CAMERA_MOTION) android.view.View.VISIBLE else android.view.View.GONE
        }
        updateDifficultyLabel(dialogBinding.difficultySeek.progress)
        updateCameraDurationLabel(CAMERA_DURATION_OPTIONS_MIN[dialogBinding.cameraDurationSeek.progress])

        // Category picks a sensible default mission, but the user can override it.
        // (Attached after pre-fill — see comment above.)
        dialogBinding.categoryGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.radioGym -> dialogBinding.missionGroup.check(R.id.radioShake)
                else -> dialogBinding.missionGroup.check(R.id.radioMath)
            }
        }

        dialogBinding.difficultySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateDifficultyLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialogBinding.cameraDurationSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val index = progress.coerceIn(0, CAMERA_DURATION_OPTIONS_MIN.lastIndex)
                updateCameraDurationLabel(CAMERA_DURATION_OPTIONS_MIN[index])
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Selecting Camera reveals the "how long should it stay open" setting,
        // and requests camera permission right away rather than waiting until
        // an alarm fires (a much better moment to ask than over the lock screen).
        dialogBinding.missionGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.firstOrNull() == R.id.radioCamera) {
                dialogBinding.cameraDurationGroup.visibility = android.view.View.VISIBLE
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            } else {
                dialogBinding.cameraDurationGroup.visibility = android.view.View.GONE
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) R.string.edit_alarm else R.string.add_alarm)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.inputTitle.text.toString().ifBlank { "Alarm" }
                val category = when {
                    dialogBinding.radioGym.isChecked -> Category.GYM
                    dialogBinding.radioStudent.isChecked -> Category.STUDENT
                    dialogBinding.radioOffice.isChecked -> Category.OFFICE
                    else -> Category.GENERAL
                }
                val mission = when {
                    dialogBinding.radioCamera.isChecked -> MissionType.CAMERA_MOTION
                    dialogBinding.radioShake.isChecked -> MissionType.SHAKE
                    else -> MissionType.MATH
                }
                val difficulty = dialogBinding.difficultySeek.progress
                val cameraDurationIndex = dialogBinding.cameraDurationSeek.progress
                    .coerceIn(0, CAMERA_DURATION_OPTIONS_MIN.lastIndex)
                val cameraDurationMinutes = CAMERA_DURATION_OPTIONS_MIN[cameraDurationIndex]

                if (existing != null) {
                    val updated = existing.copy(
                        title = title,
                        timeInMillis = calendar.timeInMillis,
                        category = category,
                        missionType = mission,
                        difficulty = difficulty,
                        cameraDurationMinutes = cameraDurationMinutes,
                        ringtoneUri = selectedRingtoneUri
                    )
                    updateAndReschedule(updated)
                } else {
                    val newEvent = AlarmEvent(
                        title = title,
                        timeInMillis = calendar.timeInMillis,
                        category = category,
                        missionType = mission,
                        difficulty = difficulty,
                        isEnabled = true,
                        cameraDurationMinutes = cameraDurationMinutes,
                        ringtoneUri = selectedRingtoneUri
                    )
                    saveAndSchedule(newEvent)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveAndSchedule(event: AlarmEvent) {
        lifecycleScope.launch {
            val id = dao.insert(event).toInt()
            val saved = event.copy(id = id)
            if (ensureExactAlarmPermission()) {
                AlarmScheduler.schedule(this@MainActivity, saved)
            }
        }
    }

    companion object {
        // Seek bar positions 0..6 map to these minute values — this is the
        // actual "how long should the camera stay on" setting from the request,
        // capped at the requested 1-hour maximum.
        private val CAMERA_DURATION_OPTIONS_MIN = intArrayOf(1, 5, 10, 15, 20, 30, 60)
    }
}
