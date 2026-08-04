package com.coder.aichat.ui.chat

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.coder.aichat.animation.ItemAnimators
import com.coder.aichat.data.api.dto.ChatMessage
import com.coder.aichat.data.api.dto.MessageRole
import com.coder.aichat.data.api.providers.AiProvider
import com.coder.aichat.databinding.ItemMessageAssistantBinding
import com.coder.aichat.databinding.ItemMessageUserBinding
import com.coder.aichat.databinding.ItemTypingBinding
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天适配器 — 用户消息 / AI 消息（Markdown 渲染）。
 * 长按 AI 消息可：复制 / 重新生成 / 翻译。
 */
class ChatAdapter(
    private val provider: AiProvider,
    private val onCopy: (String) -> Unit,
    private val onRegenerate: () -> Unit,
    private val onTranslate: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_TYPING = 2
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    private var markwon: Markwon? = null

    private val messages = mutableListOf<ChatMessage>()
    private var isTyping = false

    /** 不含打字指示器的真实消息数 */
    val messageCount: Int get() = messages.size

    override fun getItemCount(): Int = messages.size + if (isTyping) 1 else 0

    override fun getItemViewType(position: Int): Int = when {
        isTyping && position == messages.size -> TYPE_TYPING
        messages[position].role == MessageRole.USER -> TYPE_USER
        else -> TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // 初始化 Markdown 渲染器
        if (markwon == null) {
            markwon = Markwon.builder(parent.context.applicationContext).build()
        }
        return when (viewType) {
            TYPE_USER -> UserViewHolder(
                ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TYPING -> TypingViewHolder(
                ItemTypingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> AssistantViewHolder(
                ItemMessageAssistantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> holder.bind(messages[position])
            is AssistantViewHolder -> holder.bind(messages[position])
            is TypingViewHolder -> holder.start()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is AssistantViewHolder -> holder.stopThinking()
            is TypingViewHolder -> holder.stop()
        }
        super.onViewRecycled(holder)
    }

    // ── 数据操作 ──

    fun submitMessages(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    /** 流式更新最后一条 AI 消息内容 */
    fun updateLastMessage(id: String, newContent: String) {
        val index = messages.indexOfLast { it.id == id }
        if (index >= 0) {
            messages[index] = messages[index].copy(content = newContent)
            notifyItemChanged(index)
        }
    }

    fun setTyping(typing: Boolean) {
        if (isTyping == typing) return
        isTyping = typing
        if (typing) {
            notifyItemInserted(messages.size)
        } else {
            notifyItemRemoved(messages.size)
        }
    }

    // ── ViewHolder ──

    inner class UserViewHolder(
        private val binding: ItemMessageUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: ChatMessage) {
            binding.tvMessage.text = msg.content
            // 长按复制
            binding.tvMessage.setOnLongClickListener {
                onCopy(msg.content)
                true
            }
            if (bindingAdapterPosition == 0) {
                ItemAnimators.itemEnter(binding.root, 0)
            }
        }
    }

    inner class AssistantViewHolder(
        private val binding: ItemMessageAssistantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var thinkingAnimator: ValueAnimator? = null

        init {
            binding.tvAvatar.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(provider.brandColor)
            }
        }

        fun bind(msg: ChatMessage) {
            // 空内容 = 等待首个 token，显示打字动画
            if (msg.content.isBlank()) {
                startThinking()
            } else {
                stopThinking()
                // Markdown 渲染
                markwon?.setMarkdown(binding.tvMessage, msg.content)
            }
            binding.tvTime.text = timeFormat.format(Date(msg.timestamp))
            binding.btnCopy.setOnClickListener { onCopy(msg.content) }

            // 长按弹操作菜单：复制 / 重新生成 / 翻译
            binding.tvMessage.setOnLongClickListener {
                showActionsMenu(msg.content)
                true
            }
            ItemAnimators.itemEnter(binding.root, bindingAdapterPosition)
        }

        private fun showActionsMenu(content: String) {
            val options = arrayOf("复制", "重新生成", "翻译成中文")
            AlertDialog.Builder(binding.root.context)
                .setTitle("消息操作")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onCopy(content)
                        1 -> onRegenerate()
                        2 -> onTranslate(content)
                    }
                }
                .show()
        }

        private fun startThinking() {
            binding.tvMessage.text = ""
            val dots = arrayOf("", "•", "••", "•••")
            thinkingAnimator = ValueAnimator.ofInt(0, dots.size - 1).apply {
                duration = 700
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    binding.tvMessage.text = dots[it.animatedValue as Int]
                }
                start()
            }
        }

        fun stopThinking() {
            thinkingAnimator?.cancel()
            thinkingAnimator = null
        }
    }

    inner class TypingViewHolder(
        private val binding: ItemTypingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.tvAvatar.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(provider.brandColor)
            }
        }

        fun start() {
            ItemAnimators.typingDots(binding.dot1, binding.dot2, binding.dot3)
        }

        fun stop() {
            ItemAnimators.stopTypingDots(binding.dot1, binding.dot2, binding.dot3)
        }
    }
}
