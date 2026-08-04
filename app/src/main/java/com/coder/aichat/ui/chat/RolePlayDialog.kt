package com.coder.aichat.ui.chat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.coder.aichat.data.local.RolePlay
import com.coder.aichat.data.local.RolePlayStore
import com.coder.aichat.databinding.DialogRoleplayBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 角色扮演对话框 — 预设角色 + 自定义编辑 + 温度调试。
 */
class RolePlayDialog(
    private val context: Context,
    private val store: RolePlayStore,
    private val scope: CoroutineScope,
    initialRoles: List<RolePlay>,
    initialActiveId: String,
    private val onApply: (RolePlay) -> Unit
) {

    private val roles = initialRoles.toMutableList()
    private var activeId = initialActiveId
    private var isSaving = false

    private lateinit var binding: DialogRoleplayBinding
    private var dialog: AlertDialog? = null

    private val selectedRole: RolePlay
        get() = roles.firstOrNull { it.id == activeId } ?: roles.first()

    fun show() {
        binding = DialogRoleplayBinding.inflate(android.view.LayoutInflater.from(context))
        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()
        dialog?.setCanceledOnTouchOutside(true)
        dialog?.show()

        // 初始加载选中角色
        loadRoleToUi(selectedRole)

        // 温度调节
        binding.seekTemperature.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvTemperature.text = formatTemperature(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // 新建角色
        binding.btnAddRole.setOnClickListener {
            val role = RolePlay(
                id = java.util.UUID.randomUUID().toString(),
                name = "新角色${roles.size + 1}",
                prompt = "",
                temperature = 0.7
            )
            roles.add(role)
            activeId = role.id
            renderChips()
            loadRoleToUi(role)
            binding.etPrompt.requestFocus()
        }

        // 删除角色（保留至少 1 个）
        binding.btnDeleteRole.setOnClickListener {
            if (roles.size <= 1) {
                binding.btnDeleteRole.isEnabled = false
                return@setOnClickListener
            }
            val idx = roles.indexOfFirst { it.id == activeId }
            roles.removeAt(idx)
            activeId = roles[(idx - 1).coerceAtLeast(0)].id
            renderChips()
            loadRoleToUi(selectedRole)
        }

        // 应用角色
        binding.btnApplyRole.setOnClickListener {
            if (isSaving) return@setOnClickListener
            isSaving = true

            val current = selectedRole
            val updated = current.copy(
                prompt = binding.etPrompt.text?.toString().orEmpty().trim(),
                temperature = binding.seekTemperature.progress / 100.0
            )
            val idx = roles.indexOfFirst { it.id == current.id }
            if (idx >= 0) roles[idx] = updated

            scope.launch {
                store.saveRoles(roles)
                store.setActiveRoleId(updated.id)
                isSaving = false
                onApply(updated)
                dialog?.dismiss()
            }
        }

        renderChips()
    }

    private fun loadRoleToUi(role: RolePlay) {
        binding.etPrompt.setText(role.prompt)
        val progress = (role.temperature * 100).toInt().coerceIn(0, 150)
        binding.seekTemperature.progress = progress
        binding.tvTemperature.text = formatTemperature(progress)
    }

    private fun formatTemperature(progress: Int): String =
        String.format(java.util.Locale.getDefault(), "%.1f", progress / 100.0)

    private fun renderChips() {
        binding.llChips.removeAllViews()
        roles.forEach { role ->
            val chip = TextView(context).apply {
                text = role.name
                textSize = 13f
                isClickable = true
                setPadding(48, 24, 48, 24)
                gravity = Gravity.CENTER
                val isActive = role.id == activeId
                background = GradientDrawable().apply {
                    cornerRadius = 44f
                    if (isActive) setColor(0xFF7C6FF7.toInt())
                    else setColor(0xFF2A2A45.toInt())
                    if (!isActive) setStroke(1, 0xFF3D3D55.toInt())
                }
                setTextColor(if (isActive) Color.WHITE else 0xFFE8E8F0.toInt())
                setOnClickListener {
                    activeId = role.id
                    loadRoleToUi(role)
                    renderChips()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 24 }
            binding.llChips.addView(chip, lp)
        }
    }
}
