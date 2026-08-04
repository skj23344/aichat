package com.coder.aichat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.coder.aichat.data.api.providers.AiProvider
import com.coder.aichat.data.api.providers.ProviderRegistry
import com.coder.aichat.data.local.entity.ConversationRow
import com.coder.aichat.databinding.ActivityMainBinding
import com.coder.aichat.ui.chat.ChatActivity
import com.coder.aichat.data.update.UpdateManager
import com.coder.aichat.ui.history.HistoryAdapter
import com.coder.aichat.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 主界面 — RikkaHub 式会话列表。
 * 打开即见历史会话，右下角 FAB 新建，顶部可搜索。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: HistoryAdapter
    private var fullList: List<ConversationRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupHistoryList()
        setupSearch()

        binding.fabNew.setOnClickListener { showProviderPicker() }
        checkForUpdateInBackground()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                if (query.isEmpty()) {
                    historyAdapter.submitList(fullList)
                    binding.emptyView.isVisible = fullList.isEmpty()
                } else {
                    val filtered = fullList.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.lastMessage?.contains(query, ignoreCase = true) == true
                    }
                    historyAdapter.submitList(filtered)
                    binding.emptyView.isVisible = filtered.isEmpty()
                }
            }
        })
    }

    /** 后台自动检查更新（配置了地址才检查） */
    private fun checkForUpdateInBackground() {
        val app = application as AiChatApp
        lifecycleScope.launch {
            val settings = app.settings
            val url = settings.getUpdateUrl()
            if (url.isBlank() || !settings.getUpdateAutoCheck()) return@launch
            val version = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
            } catch (_: Exception) { "0" }
            val info = app.updateChecker.check(url, version)
            if (info != null) {
                runOnUiThread {
                    UpdateManager.showUpdateDialog(this@MainActivity, lifecycleScope, info)
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupHistoryList() {
        historyAdapter = HistoryAdapter(::openConversation, ::showSessionActions)
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = historyAdapter

        lifecycleScope.launch {
            (application as AiChatApp).repository.getAllConversationsWithPreview().collect { list ->
                fullList = list
                // 若当前有搜索词则保持过滤
                val query = binding.etSearch.text?.toString()?.trim().orEmpty()
                if (query.isEmpty()) {
                    historyAdapter.submitList(list)
                } else {
                    historyAdapter.submitList(list.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.lastMessage?.contains(query, ignoreCase = true) == true
                    })
                }
                binding.emptyView.isVisible = list.isEmpty()
            }
        }
    }

    private fun openConversation(conv: ConversationRow) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_PROVIDER_ID, conv.providerId)
            putExtra(ChatActivity.EXTRA_MODEL_ID, conv.modelId)
            putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conv.id)
        }
        startActivity(intent)
    }

    /** 长按会话：置顶 / 重命名 / 删除 */
    private fun showSessionActions(conv: ConversationRow) {
        val pinLabel = if (conv.pinned) "📌 取消置顶" else "📌 置顶"
        val options = arrayOf(pinLabel, "✏️ 重命名", "🗑 删除")
        AlertDialog.Builder(this)
            .setTitle(conv.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch {
                        val pinned = (application as AiChatApp).repository.togglePin(conv.id)
                        Toast.makeText(
                            this@MainActivity,
                            if (pinned) "已置顶" else "已取消置顶",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> showRenameDialog(conv)
                    2 -> confirmDelete(conv)
                }
            }
            .show()
    }

    private fun showRenameDialog(conv: ConversationRow) {
        val editText = android.widget.EditText(this).apply {
            setText(conv.title)
            setSingleLine()
            setPadding(48, 32, 48, 32)
            setTextColor(0xFFE8E8F0.toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("重命名会话")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text?.toString()?.trim().orEmpty()
                if (newTitle.isNotEmpty()) {
                    lifecycleScope.launch {
                        (application as AiChatApp).repository.renameConversation(conv.id, newTitle)
                        Toast.makeText(this@MainActivity, "已重命名", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDelete(conv: ConversationRow) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(conv.title)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    (application as AiChatApp).repository.deleteConversation(conv.id)
                    Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /** 新建会话：选择厂商 */
    private fun showProviderPicker() {
        val providers = ProviderRegistry.providerList
        val names = providers.map { it.displayName }
        AlertDialog.Builder(this)
            .setTitle("选择 AI 厂商")
            .setItems(names.toTypedArray()) { _, which ->
                startChatWith(providers[which])
            }
            .show()
    }

    private fun startChatWith(provider: AiProvider) {
        // 需要 Key 的厂商未配置时提示去设置
        if (provider.requiresApiKey && provider.getApiKey().isBlank()) {
            Toast.makeText(
                this,
                "请先在设置中配置 ${provider.displayName} 的 API Key",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_PROVIDER_ID, provider.id)
            })
            return
        }

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_PROVIDER_ID, provider.id)
            putExtra(ChatActivity.EXTRA_MODEL_ID, provider.defaultModel)
        }
        startActivity(intent)
    }
}
