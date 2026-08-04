package com.coder.aichat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coder.aichat.data.api.dto.ChatMessage
import com.coder.aichat.data.api.dto.MessageRole
import com.coder.aichat.data.local.SettingsDataStore
import com.coder.aichat.data.repository.ChatRepository
import com.coder.aichat.data.search.SearchEngine
import com.coder.aichat.data.search.SearchManager
import com.coder.aichat.data.search.toContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChatViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsDataStore,
    private val searchManager: SearchManager,
    val providerId: String,
    var modelId: String
) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isStreaming = MutableLiveData(false)
    val isStreaming: LiveData<Boolean> = _isStreaming

    /** 联网搜索中（UI 显示提示） */
    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    /** 联网搜索开关（聊天页可切换） */
    var searchEnabled: Boolean = false
        private set

    private val _systemPrompt = MutableLiveData("")
    val systemPrompt: LiveData<String> = _systemPrompt

    /** 当前角色扮演的人设（非空时优先于全局系统提示词） */
    var activeRolePrompt: String? = null

    /** 温度调试参数（角色扮演界面可调） */
    var temperature: Double = 0.7

    /** 当前会话 ID，首次发送时创建 */
    var conversationId: String? = null
        private set

    private var current = mutableListOf<ChatMessage>()
    private var streamJob: Job? = null
    private var loadingDone = false

    init {
        viewModelScope.launch {
            _systemPrompt.value = settings.getSystemPrompt()
            searchEnabled = settings.getSearchEnabled()
        }
    }

    /** 切换联网搜索开关 */
    fun toggleSearch() {
        searchEnabled = !searchEnabled
        viewModelScope.launch { settings.setSearchEnabled(searchEnabled) }
    }

    /** 从存储同步开关状态（onResume 时刷新） */
    suspend fun syncSearchEnabledFromStore() {
        searchEnabled = settings.getSearchEnabled()
    }

    fun loadMessages(existingConversationId: String) {
        if (loadingDone) return
        loadingDone = true
        conversationId = existingConversationId
        // 只取一次数据库快照，避免 Room 流的持续重发干扰流式输出状态
        viewModelScope.launch {
            val entities = repository.getMessages(existingConversationId).first()
            current = entities.map {
                ChatMessage(
                    role = if (it.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
                    content = it.content,
                    timestamp = it.timestamp,
                    id = it.id
                )
            }.toMutableList()
            _messages.value = current.toList()
        }
    }

    /** 发送用户消息 */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isStreaming.value == true) return

        viewModelScope.launch {
            // 懒创建会话
            if (conversationId == null) {
                val title = trimmed.take(20)
                conversationId = repository.createConversation(
                    title = title,
                    providerId = providerId,
                    modelId = modelId
                )
            }

            val userMsg = ChatMessage(role = MessageRole.USER, content = trimmed)
            current.add(userMsg)
            _messages.value = current.toList()
            repository.saveMessage(conversationId!!, userMsg)

            // 联网搜索：Bing 免费免 Key；Tavily/Exa 需 Key
            var searchContext: String? = null
            if (searchEnabled) {
                val engineId = settings.getSearchEngine()
                val engine = SearchEngine.fromId(engineId)
                val key = settings.getSearchApiKey()
                if (engine == SearchEngine.BING || key.isNotBlank()) {
                    _isSearching.value = true
                    val results = searchManager.search(engine, key, trimmed)
                    searchContext = results.toContext(trimmed)
                    _isSearching.value = false
                }
            }

            startStreaming(searchContext)
        }
    }

    /** 重新生成最后一条 AI 回复 */
    fun regenerate() {
        if (_isStreaming.value == true) return
        // 找到最后一条 AI 回复并移除
        val lastAssistantIdx = current.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (lastAssistantIdx < 0) return
        current.removeAt(lastAssistantIdx)
        _messages.value = current.toList()

        viewModelScope.launch { startStreaming() }
    }

    /** 一键翻译消息（作为新一轮对话发出） */
    fun translate(text: String, targetLang: String = "中文") {
        val instruction = "请将以下内容翻译成$targetLang，只输出翻译结果，不要附加解释：\n\n$text"
        send(instruction)
    }

    /** 流式请求公共逻辑（发送消息 / 重新生成共用） */
    private suspend fun startStreaming(extraContext: String? = null) {
        if (current.isEmpty()) return

        // 占位 AI 消息，流式填充
        val assistantMsg = ChatMessage(role = MessageRole.ASSISTANT, content = "")
        current.add(assistantMsg)
        _messages.value = current.toList()

        _isStreaming.value = true
        // 角色扮演人设优先，否则用全局系统提示词；并替换 Prompt 变量
        val basePrompt = resolvePromptVars(activeRolePrompt ?: settings.getSystemPrompt())
        // 联网搜索结果附加为额外上下文
        val systemPrompt = if (extraContext.isNullOrBlank()) basePrompt
            else "$basePrompt\n\n$extraContext"

        streamJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                repository.chatStream(
                    providerId = providerId,
                    modelId = modelId,
                    messages = current.filter { it.id != assistantMsg.id },
                    systemPrompt = systemPrompt.ifBlank { null },
                    temperature = temperature
                ).collect { token ->
                    sb.append(token)
                    current[current.lastIndex] = assistantMsg.copy(content = sb.toString())
                    _messages.value = current.toList()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动停止：不当作错误，交给 finally 清理
                throw e
            } catch (e: Exception) {
                sb.append("\n\n⚠️ 错误: ${e.message}")
                if (current.lastOrNull()?.id == assistantMsg.id) {
                    current[current.lastIndex] = assistantMsg.copy(content = sb.toString())
                    _messages.value = current.toList()
                }
            } finally {
                _isStreaming.value = false
                // 只有最后一条仍是本次的占位才处理，避免与 stopStreaming 竞争
                val last = current.lastOrNull()
                if (last?.id == assistantMsg.id) {
                    if (last.content.isNotBlank()) {
                        repository.saveMessage(conversationId!!, last)
                    } else {
                        current.removeAt(current.lastIndex)
                        _messages.value = current.toList()
                    }
                }
            }
        }
    }

    /** 替换系统提示词中的 {变量} */
    private fun resolvePromptVars(prompt: String): String {
        val now = Calendar.getInstance()
        val date = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(now.time)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
        val datetime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now.time)
        return prompt
            .replace("{date}", date)
            .replace("{time}", time)
            .replace("{datetime}", datetime)
            .replace("{model}", modelId)
            .replace("{provider}", providerId)
    }

    fun stopStreaming() {
        streamJob?.cancel()
        _isStreaming.value = false
        // 移除未完成的空占位
        if (current.isNotEmpty() && current.last().content.isBlank()) {
            current.removeAt(current.lastIndex)
            _messages.value = current.toList()
        }
    }

    fun clearCurrent() {
        streamJob?.cancel()
        current.clear()
        _messages.value = emptyList()
        conversationId = null
        loadingDone = false
    }
}
