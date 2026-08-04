package com.coder.aichat.ui.provider

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.coder.aichat.animation.ItemAnimators
import com.coder.aichat.data.api.providers.AiProvider
import com.coder.aichat.databinding.ItemProviderBinding

class ProviderAdapter(
    private val onClick: (AiProvider, android.view.View) -> Unit
) : ListAdapter<AiProvider, ProviderAdapter.ProviderViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderViewHolder {
        val binding = ItemProviderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProviderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProviderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProviderViewHolder(
        private val binding: ItemProviderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(provider: AiProvider) {
            binding.tvProviderName.text = provider.displayName
            binding.tvModel.text = provider.models.firstOrNull { it.isDefault }?.displayName
                ?: provider.models.firstOrNull()?.displayName
                ?: if (provider.id == "custom") "待配置模型" else provider.defaultModel
            binding.tvLogo.text = provider.displayName.take(1).uppercase()

            // 品牌色圆形 Logo
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(provider.brandColor)
            binding.tvLogo.background = bg

            // 卡片进入动画
            ItemAnimators.itemEnter(binding.root, bindingAdapterPosition)

            // 按压 + 点击
            binding.root.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> ItemAnimators.press(v)
                    android.view.MotionEvent.ACTION_UP -> ItemAnimators.release(v)
                }
                false
            }
            binding.root.setOnClickListener {
                onClick(provider, binding.tvLogo)
            }
        }
    }

    companion object {
        val Diff = object : DiffUtil.ItemCallback<AiProvider>() {
            override fun areItemsTheSame(a: AiProvider, b: AiProvider) = a.id == b.id
            override fun areContentsTheSame(a: AiProvider, b: AiProvider) = a == b
        }
    }
}
