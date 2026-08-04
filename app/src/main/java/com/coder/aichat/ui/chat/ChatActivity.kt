package com.coder.aichat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.coder.aichat.AiChatApp
import com.coder.aichat.R
import com.coder.aichat.data.api.providers.AiProvider
import com.coder.aichat.data.api.providers.ProviderRegistry
import com.coder.aichat.databinding.ActivityChatBinding
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROVIDER_ID = "provider_id"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var provider: AiProvider
    private var modelId: String = ""
    private var conversationId: String? = null

    private val viewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = application as AiChatApp
                return ChatViewModel(
                    repository = app.repository,
                    settings = app.settings,
                    memoryStore = app.memoryStore,
                    searchManager = app.searchManager,
                    providerId = provider.id,
                    modelId = modelId
                ) as T
            }
        }
    }

    private lateinit var adapter: ChatAdapter

    // TTS 语音朗读
    private var tts: TextToSpeech? = null
    private var isSpeaking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 从 SharedElement 过渡开始，进入即动画
        provider = ProviderRegistry.get(intent.getStringExtra(EXTRA_PROVIDER_ID) ?: "openai")
            ?: ProviderRegistry.get("openai")!!
        modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: provider.defaultModel
        conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 共享元素 Logo 过渡
        ViewCompat.setTransitionName(binding.tvProviderLogo, "provider_logo")

        setupToolbar()
        setupRecyclerView()
        setupQuickMessages()
        setupInput()
        observeViewModel()
        initTts()

        if (conversationId != null) {
            viewModel.loadMessages(conversationId!!)
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvProviderLogo.text = provider.displayName.take(1).uppercase()
        binding.tvProviderLogo.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(provider.brandColor)
        }
        binding.tvProviderName.text = provider.displayName
        updateModelLabel()

        // 点击模型名选择模型
        binding.llProviderInfo.setOnClickListener { showModelPicker() }

        // 角色扮演入口
        binding.toolbar.inflateMenu(R.menu.menu_chat)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_roleplay -> {
                    showRolePlayDialog()
                    true
                }
                R.id.action_export -> {
                    exportConversation()
                    true
                }
                else -> false
            }
        }
    }

    private fun showRolePlayDialog() {
        val store = (application as AiChatApp).rolePlayStore
        lifecycleScope.launch {
            val roles = store.getRoles()
            val activeId = store.getActiveRoleId()
            RolePlayDialog(this@ChatActivity, store, lifecycleScope, roles, activeId) { role ->
                viewModel.activeRolePrompt = role.prompt
                viewModel.temperature = role.temperature
                Toast.makeText(this@ChatActivity, "已应用角色：${role.name}", Toast.LENGTH_SHORT).show()
            }.show()
        }
    }

    private fun updateModelLabel() {
        val model = provider.models.firstOrNull { it.id == modelId } ?: provider.models.firstOrNull()
        binding.tvModelName.text = if (model != null) "${model.displayName} ▼" else "未配置模型 ▼"
    }

    private fun showModelPicker() {
        val modelNames = provider.models.map { it.displayName }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择模型")
            .setItems(modelNames.toTypedArray()) { _, which ->
                modelId = provider.models[which].id
                viewModel.modelId = modelId  // 同步到 ViewModel，后续发送用新模型
                updateModelLabel()
                lifecycleScope.launch {
                    (application as AiChatApp).settings.setSelectedModel(modelId)
                }
            }
            .create()
        dialog.show()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            provider,
            ::copyToClipboard,
            ::regenerateLast,
            ::translateMessage,
            ::speakMessage,
            ::shareMessage
        )
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter
        binding.recyclerMessages.itemAnimator = null // 手动控制动画更精确
    }

    /** 快捷消息 chips — 点击填入输入框 */
    private fun setupQuickMessages() {
        addQuickChip("📋 模板") { showTemplateDialog() }

        val quick = listOf("帮我写代码", "解释这段内容", "翻译成英文", "总结一下", "续写", "帮我起个标题")
        quick.forEach { msg ->
            addQuickChip(msg) {
                binding.etInput.setText(msg)
                binding.etInput.setSelection(msg.length)
                binding.etInput.requestFocus()
            }
        }
    }

    private fun addQuickChip(text: String, onClick: () -> Unit) {
        val chip = TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(44, 22, 44, 22)
            background = GradientDrawable().apply {
                cornerRadius = 44f
                setColor(0xFF2A2A45.toInt())
                setStroke(1, 0xFF3D3D55.toInt())
            }
            setTextColor(0xFFE8E8F0.toInt())
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 24 }
        binding.llQuickChips.addView(chip, lp)
    }

    /** 模板选择对话框 */
    private fun showTemplateDialog() {
        val templates = com.coder.aichat.data.PromptTemplates.all
        val names = templates.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择 Prompt 模板")
            .setItems(names) { _, which ->
                applyTemplate(templates[which])
            }
            .show()
    }

    private fun applyTemplate(t: com.coder.aichat.data.PromptTemplates.Template) {
        val et = binding.etInput
        val selection = et.text?.substring(et.selectionStart, et.selectionEnd).orEmpty()
        val content = if (selection.isNotEmpty()) selection else et.text?.toString().orEmpty()
        val prompt = t.prompt.replace("{content}", content)
        et.setText(prompt)
        et.setSelection(prompt.length)
        et.requestFocus()
    }

    /** 重新生成最后一条回复 */
    private fun regenerateLast() {
        if (viewModel.isStreaming.value == true) {
            Toast.makeText(this, "请等待当前回复完成", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.regenerate()
    }

    /** 翻译消息 */
    private fun translateMessage(text: String) {
        viewModel.translate(text)
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            if (viewModel.isStreaming.value == true) {
                viewModel.stopStreaming()
                binding.btnSend.setIconResource(R.drawable.ic_send)
            } else {
                val text = binding.etInput.text?.toString().orEmpty()
                if (text.isNotBlank()) {
                    sendMessage(text)
                }
            }
        }

        // 联网搜索开关
        binding.btnSearch.setOnClickListener {
            viewModel.toggleSearch()
            updateSearchButton()
        }
    }

    /** 更新搜索按钮高亮态 */
    private fun updateSearchButton() {
        if (viewModel.searchEnabled) {
            binding.btnSearch.background = getDrawable(R.drawable.bg_search_on)
            binding.btnSearch.imageTintList =
                android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        } else {
            binding.btnSearch.background = null
            binding.btnSearch.imageTintList =
                android.content.res.ColorStateList.valueOf(0xFFE8E8F0.toInt())
        }
    }

    private fun sendMessage(text: String) {
        binding.etInput.setText("")
        viewModel.send(text)
        binding.btnSend.setIconResource(R.drawable.ic_stop)
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { list ->
            val msgCount = adapter.messageCount
            when {
                // 新增消息（用户发送 / 初次加载）
                list.size > msgCount -> adapter.submitMessages(list)
                // 仅最后一条内容在流式增长 → 增量更新，不重建整个列表
                list.size == msgCount && msgCount > 0 -> adapter.updateLastMessage(
                    list.last().id, list.last().content
                )
                else -> adapter.submitMessages(list)
            }
            scrollToBottom()
        }
        viewModel.isStreaming.observe(this) { streaming ->
            if (streaming) scrollToBottom()
            binding.btnSend.setIconResource(if (streaming) R.drawable.ic_stop else R.drawable.ic_send)
        }
        viewModel.isSearching.observe(this) { searching ->
            binding.tvSearching.isVisible = searching
        }
    }

    private fun scrollToBottom() {
        binding.recyclerMessages.post {
            val count = binding.recyclerMessages.adapter?.itemCount ?: 0
            if (count > 0) binding.recyclerMessages.smoothScrollToPosition(count - 1)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI 回复", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    /** TTS 朗读 / 停止 */
    private fun speakMessage(text: String) {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            Toast.makeText(this, "已停止朗读", Toast.LENGTH_SHORT).show()
            return
        }
        val clean = text.replace(Regex("[`#*_\\[\\]|>~-]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "tts")
        isSpeaking = true
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(p0: String?) {}
            override fun onDone(p0: String?) { isSpeaking = false }
            override fun onError(p0: String?) { isSpeaking = false }
        })
        Toast.makeText(this, "正在朗读…", Toast.LENGTH_SHORT).show()
    }

    /** 分享单条消息 */
    private fun shareMessage(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "分享消息"))
    }

    /** 导出整个会话为文本 */
    private fun exportConversation() {
        val messages = viewModel.messages.value ?: return
        if (messages.isEmpty()) {
            Toast.makeText(this, "还没有消息可导出", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        sb.append("# ${provider.displayName} 对话\n")
        sb.append("模型：$modelId · ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())}\n\n")
        messages.forEach { msg ->
            val role = if (msg.role == com.coder.aichat.data.api.dto.MessageRole.USER) "🙋 我" else "🤖 AI"
            sb.append("**$role**\n${msg.content}\n\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${provider.displayName} 对话导出")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(intent, "导出会话"))
    }

    override fun onResume() {
        super.onResume()
        // 刷新模型信息（设置页可能改了 base url 等）
        val fresh = ProviderRegistry.get(provider.id)
        if (fresh != null) provider = fresh
        // 同步联网搜索开关状态
        lifecycleScope.launch {
            viewModel.syncSearchEnabledFromStore()
            updateSearchButton()
        }
    }

    override fun onDestroy() {
        viewModel.stopStreaming()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
