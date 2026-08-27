package com.buddy.wakeforge

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buddy.wakeforge.databinding.ItemAlarmBinding
import java.text.SimpleDateFormat
import java.util.Locale

class AlarmAdapter(
    private val onToggle: (AlarmEvent, Boolean) -> Unit,
    private val onDelete: (AlarmEvent) -> Unit,
    private val onEdit: (AlarmEvent) -> Unit
) : ListAdapter<AlarmEvent, AlarmAdapter.VH>(DIFF) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class VH(val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val event = getItem(position)
        holder.binding.timeText.text = timeFormat.format(event.timeInMillis)
        holder.binding.title.text = event.title
        val categoryLabel = event.category.name.lowercase().replaceFirstChar { it.uppercase() }
        val missionLabel = when (event.missionType) {
            MissionType.MATH -> "Math"
            MissionType.SHAKE -> "Shake"
            MissionType.CAMERA_MOTION -> "Camera (${formatDurationMinutes(event.cameraDurationMinutes)})"
        }
        holder.binding.missionText.text = "$categoryLabel · $missionLabel mission"

        val (badgeBg, emoji) = when (event.category) {
            Category.STUDENT -> R.drawable.bg_badge_student to "📚"
            Category.GYM -> R.drawable.bg_badge_gym to "💪"
            Category.GENERAL -> R.drawable.bg_badge_general to "⭐"
        }
        holder.binding.categoryBadge.setBackgroundResource(badgeBg)
        holder.binding.categoryEmoji.text = emoji

        holder.binding.enabledSwitch.setOnCheckedChangeListener(null)
        holder.binding.enabledSwitch.isChecked = event.isEnabled
        holder.binding.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            onToggle(event, checked)
        }

        holder.binding.deleteText.setOnClickListener { onDelete(event) }
        holder.binding.editText.setOnClickListener { onEdit(event) }
        // Tapping anywhere else on the card also opens edit — the pencil icon
        // is there for discoverability, this is for convenience.
        holder.itemView.setOnClickListener { onEdit(event) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AlarmEvent>() {
            override fun areItemsTheSame(a: AlarmEvent, b: AlarmEvent) = a.id == b.id
            override fun areContentsTheSame(a: AlarmEvent, b: AlarmEvent) = a == b
        }
    }
}
