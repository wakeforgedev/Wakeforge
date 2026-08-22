package com.buddy.wakeforge

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Same reasoning as AlarmActivity: routine/event titles are personal, so keep
        // them out of screenshots, screen recordings, and the Recent Apps thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AlarmAdapter(
            onToggle = { event, checked -> onToggleAlarm(event, checked) },
            onDelete = { event -> onDeleteAlarm(event) }
        )
        binding.alarmList.layoutManager = LinearLayoutManager(this)
        binding.alarmList.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddAlarmDialog() }

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
            dao.delete(event)
        }
    }

    private fun showAddAlarmDialog() {
        val dialogBinding = DialogAddAlarmBinding.inflate(layoutInflater)

        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1) // sensible default: next minute
        }

        fun updateTimeButtonLabel() {
            dialogBinding.pickTimeButton.text = "Pick time: %02d:%02d".format(
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

        // Category picks a sensible default mission, but the user can override it.
        // MaterialButtonToggleGroup's listener fires for both the button being
        // unchecked and the one becoming checked, so filter to isChecked==true
        // or this would double-fire on every tap.
        dialogBinding.categoryGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.radioGym -> dialogBinding.missionGroup.check(R.id.radioShake)
                else -> dialogBinding.missionGroup.check(R.id.radioMath)
            }
        }

        fun updateCameraDurationLabel(minutes: Int) {
            dialogBinding.cameraDurationLabel.text = getString(R.string.camera_duration_label, formatDurationMinutes(minutes))
        }
        updateCameraDurationLabel(CAMERA_DURATION_OPTIONS_MIN[dialogBinding.cameraDurationSeek.progress])

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
        dialogBinding.missionGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (checkedId == R.id.radioCamera) {
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
            .setTitle(R.string.add_alarm)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = dialogBinding.inputTitle.text.toString().ifBlank { "Alarm" }
                val category = when {
                    dialogBinding.radioGym.isChecked -> Category.GYM
                    dialogBinding.radioGeneral.isChecked -> Category.GENERAL
                    else -> Category.STUDENT
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

                val newEvent = AlarmEvent(
                    title = title,
                    timeInMillis = calendar.timeInMillis,
                    category = category,
                    missionType = mission,
                    difficulty = difficulty,
                    isEnabled = true,
                    cameraDurationMinutes = cameraDurationMinutes
                )
                saveAndSchedule(newEvent)
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
