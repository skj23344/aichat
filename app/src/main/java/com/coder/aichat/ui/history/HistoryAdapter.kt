package com.coder.aichat.ui.history

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.coder.aichat.animation.ItemAnimators
import com.coder.aichat.data.api.providers.ProviderRegistry
import com.coder.aichat.data.local.entity.ConversationRow
import com.coder.aichat.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话列表适配器 — 显示标题、最后消息预览、模型标签、相对时间。
 */
class HistoryAdapter(
    private val onClick: (ConversationRow) -> Unit,
    private val onLongClick: (ConversationRow) -> Unit
) : ListAdapter<ConversationRow, HistoryAdapter.ConversationViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        return ConversationViewHolder(
            ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(
        private val binding: ItemConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ConversationRow) {
            val provider = ProviderRegistry.get(row.providerId)
            binding.tvLogo.text = (provider?.displayName ?: "AI").take(1).uppercase()
            binding.tvLogo.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(provider?.brandColor ?: 0xFF7C6FF7.toInt())
            }
            binding.tvTitle.text = (if (row.pinned) "📌 " else "") + row.title
            binding.tvTime.text = formatRelativeTime(row.updatedAt)

            // 最后一条消息预览
            val preview = row.lastMessage?.trim()?.replace("\n", " ")
            binding.tvPreview.text = preview?.takeIf { it.isNotEmpty() } ?: "暂无消息"

            // 模型标签
            val modelName = provider?.models?.firstOrNull { it.id == row.modelId }?.displayName
                ?: row.modelId
            binding.tvModel.text = modelName

            binding.root.setOnClickListener { onClick(row) }
            binding.root.setOnLongClickListener {
                onLongClick(row)
                true
            }
            ItemAnimators.itemEnter(binding.root, bindingAdapterPosition)
        }
    }

    companion object {
        private fun formatRelativeTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000 -> "刚刚"
                diff < 3_600_000 -> "${diff / 60_000}分钟前"
                diff < 86_400_000 -> "${diff / 3_600_000}小时前"
                diff < 172_800_000 -> "昨天"
                else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
            }
        }

        val Diff = object : DiffUtil.ItemCallback<ConversationRow>() {
            override fun areItemsTheSame(a: ConversationRow, b: ConversationRow) = a.id == b.id
            override fun areContentsTheSame(a: ConversationRow, b: ConversationRow) = a == b
        }
    }
}
