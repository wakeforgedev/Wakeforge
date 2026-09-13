package com.buddy.wakeforge

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.buddy.wakeforge.databinding.ActivityHistoryBinding
import com.buddy.wakeforge.databinding.ItemHistoryLogBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The "History" tab: a month calendar (green dot = at least one completed
 * mission that day, red dot = at least one alarm rang that day but was never
 * completed, no dot = no alarms that day) plus a detail list for whichever
 * day is selected. Backed by MissionLog rows written at two points:
 * AlarmRingService writes the "fired" row the moment an alarm actually
 * rings, and AlarmActivity flips it to completed on mission success — so a
 * ring with no completion shows up here as genuinely missed, not just absent.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var missionLogDao: MissionLogDao

    private val displayedMonth: Calendar = Calendar.getInstance()
    private val selectedDay: Calendar = Calendar.getInstance()
    private var monthLogs: List<MissionLog> = emptyList()

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val dayTitleFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dayCellViews = mutableListOf<Triple<TextView, View, Calendar>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        missionLogDao = AppDatabase.get(applicationContext).missionLogDao()

        binding.backButton.setOnClickListener { finish() }
        binding.prevMonth.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            loadMonth()
        }
        binding.nextMonth.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            loadMonth()
        }

        buildWeekdayHeader()
        loadMonth()
    }

    private fun buildWeekdayHeader() {
        binding.weekdayHeader.removeAllViews()
        binding.weekdayHeader.columnCount = 7
        val symbols = SimpleDateFormat("EEEEE", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        for (col in 0 until 7) {
            val label = TextView(this).apply {
                text = symbols.format(cal.time)
                gravity = Gravity.CENTER
                setTextColor(getColorCompat(R.color.text_muted))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(0, 1, GridLayout.FILL, 1f),
                GridLayout.spec(col, 1, GridLayout.FILL, 1f)
            )
            params.width = 0
            params.height = dp(28)
            binding.weekdayHeader.addView(label, params)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun loadMonth() {
        binding.monthLabel.text = monthFormat.format(displayedMonth.time)
        buildCalendarGrid()

        val (startMillis, endMillis) = monthRangeMillis(displayedMonth)
        lifecycleScope.launch {
            missionLogDao.observeBetween(startMillis, endMillis).collectLatest { logs ->
                monthLogs = logs
                applyDayStatuses()
                renderSelectedDayDetail()
            }
        }
    }

    private fun monthRangeMillis(month: Calendar): Pair<Long, Long> {
        val start = month.clone() as Calendar
        start.set(Calendar.DAY_OF_MONTH, 1)
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        val end = start.clone() as Calendar
        end.add(Calendar.MONTH, 1)
        end.add(Calendar.MILLISECOND, -1)
        return start.timeInMillis to end.timeInMillis
    }

    private fun buildCalendarGrid() {
        binding.calendarGrid.removeAllViews()
        dayCellViews.clear()

        val gridStart = displayedMonth.clone() as Calendar
        gridStart.set(Calendar.DAY_OF_MONTH, 1)
        val leadingBlanks = gridStart.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        gridStart.add(Calendar.DAY_OF_MONTH, -leadingBlanks)

        val today = Calendar.getInstance()
        val cursor = gridStart.clone() as Calendar
        val totalCells = 42 // 6 full weeks, enough for any month's leading/trailing blanks

        for (i in 0 until totalCells) {
            val cellCalendar = cursor.clone() as Calendar
            val inCurrentMonth = cellCalendar.get(Calendar.MONTH) == displayedMonth.get(Calendar.MONTH) &&
                cellCalendar.get(Calendar.YEAR) == displayedMonth.get(Calendar.YEAR)

            val cellView = FrameLayout(this)
            val dayNumber = TextView(this).apply {
                text = cellCalendar.get(Calendar.DAY_OF_MONTH).toString()
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(
                    getColorCompat(if (inCurrentMonth) R.color.text_primary else R.color.text_muted)
                )
            }
            val statusDot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(6), dp(6)).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(2)
                }
                visibility = View.GONE
            }

            val isToday = isSameDay(cellCalendar, today)
            val isSelected = isSameDay(cellCalendar, selectedDay)
            dayNumber.setBackgroundResource(
                when {
                    isSelected -> R.drawable.bg_day_selected
                    isToday -> R.drawable.bg_day_today
                    else -> 0
                }
            )
            if (isSelected) {
                dayNumber.setTextColor(getColorCompat(R.color.text_on_dark))
            }

            cellView.addView(dayNumber, FrameLayout.LayoutParams(dp(32), dp(32)).apply {
                gravity = Gravity.CENTER
            })
            cellView.addView(statusDot)
            cellView.setPadding(0, dp(4), 0, dp(4))
            cellView.isClickable = inCurrentMonth
            cellView.isFocusable = inCurrentMonth
            if (inCurrentMonth) {
                val dayCopy = cellCalendar.clone() as Calendar
                cellView.setOnClickListener {
                    selectedDay.time = dayCopy.time
                    buildCalendarGrid()
                    applyDayStatuses()
                    renderSelectedDayDetail()
                }
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(i / 7, 1, GridLayout.FILL, 1f),
                GridLayout.spec(i % 7, 1, GridLayout.FILL, 1f)
            )
            params.width = 0
            params.height = dp(40)
            binding.calendarGrid.addView(cellView, params)

            dayCellViews.add(Triple(dayNumber, statusDot, cellCalendar))
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun applyDayStatuses() {
        for ((_, dotView, cellCalendar) in dayCellViews) {
            val logsThatDay = monthLogs.filter { isSameDayMillis(it.scheduledAtMillis, cellCalendar) }
            when {
                logsThatDay.isEmpty() -> dotView.visibility = View.GONE
                logsThatDay.any { it.completed } -> {
                    dotView.setBackgroundResource(R.drawable.bg_dot_completed)
                    dotView.visibility = View.VISIBLE
                }
                else -> {
                    dotView.setBackgroundResource(R.drawable.bg_dot_missed)
                    dotView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun renderSelectedDayDetail() {
        binding.dayDetailTitle.text = dayTitleFormat.format(selectedDay.time)
        binding.dayDetailContainer.removeAllViews()

        val logsThatDay = monthLogs
            .filter { isSameDayMillis(it.scheduledAtMillis, selectedDay) }
            .sortedByDescending { it.scheduledAtMillis }

        if (logsThatDay.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.history_no_alarms_that_day)
                setTextColor(getColorCompat(R.color.text_muted))
                textSize = 13f
                setPadding(4, dp(4), 4, dp(4))
            }
            binding.dayDetailContainer.addView(empty)
            return
        }

        for (log in logsThatDay) {
            val itemBinding = ItemHistoryLogBinding.inflate(LayoutInflater.from(this), binding.dayDetailContainer, false)
            itemBinding.logTitle.text = log.alarmTitle
            val timeText = timeFormat.format(log.scheduledAtMillis)
            if (log.completed && log.completedAtMillis != null) {
                itemBinding.statusDot.setBackgroundResource(R.drawable.bg_dot_completed)
                itemBinding.logStatusLabel.text = getString(R.string.history_completed)
                itemBinding.logStatusLabel.setTextColor(getColorCompat(R.color.completed_green))
                val solvedSeconds = ((log.completedAtMillis - log.scheduledAtMillis) / 1000).coerceAtLeast(0)
                itemBinding.logSubtitle.text = "$timeText · ${getString(R.string.history_solved_in, formatSeconds(solvedSeconds))}"
            } else {
                itemBinding.statusDot.setBackgroundResource(R.drawable.bg_dot_missed)
                itemBinding.logStatusLabel.text = getString(R.string.history_missed)
                itemBinding.logStatusLabel.setTextColor(getColorCompat(R.color.danger))
                itemBinding.logSubtitle.text = "$timeText · ${getString(R.string.history_not_completed)}"
            }
            binding.dayDetailContainer.addView(itemBinding.root)
        }
    }

    private fun formatSeconds(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isSameDayMillis(millis: Long, day: Calendar): Boolean {
        val other = Calendar.getInstance()
        other.timeInMillis = millis
        return isSameDay(other, day)
    }

    private fun getColorCompat(colorRes: Int) = androidx.core.content.ContextCompat.getColor(this, colorRes)

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
