package com.buddy.wakeforge

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
        val context = holder.itemView.context
        holder.binding.timeText.text = timeFormat.format(event.timeInMillis)
        holder.binding.title.text = event.title
        val categoryLabel = event.category.name.lowercase().replaceFirstChar { it.uppercase() }
        val missionLabel = when (event.missionType) {
            MissionType.MATH -> "Math"
            MissionType.SHAKE -> "Shake"
            MissionType.CAMERA_MOTION -> "Camera (${formatDurationMinutes(event.cameraDurationMinutes)})"
        }
        holder.binding.missionText.text = "$categoryLabel · $missionLabel mission"

        val (badgeBg, icon, tint) = when (event.category) {
            Category.STUDENT -> Triple(R.drawable.bg_badge_student, R.drawable.ic_cat_student, R.color.student_tint)
            Category.GYM -> Triple(R.drawable.bg_badge_gym, R.drawable.ic_cat_gym, R.color.gym_tint)
            Category.GENERAL -> Triple(R.drawable.bg_badge_general, R.drawable.ic_cat_general, R.color.general_tint)
            Category.OFFICE -> Triple(R.drawable.bg_badge_office, R.drawable.ic_cat_office, R.color.office_tint)
        }
        holder.binding.categoryBadge.setBackgroundResource(badgeBg)
        holder.binding.categoryIcon.setImageResource(icon)
        holder.binding.categoryIcon.imageTintList = ContextCompat.getColorStateList(context, tint)

        holder.binding.enabledSwitch.setOnCheckedChangeListener(null)
        holder.binding.enabledSwitch.isChecked = event.isEnabled
        holder.binding.enabledSwitch.setOnCheckedChangeListener { _, checked ->
            onToggle(event, checked)
        }

        holder.binding.deleteIcon.setOnClickListener { onDelete(event) }
        holder.binding.editIcon.setOnClickListener { onEdit(event) }
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
