package com.coder.aichat.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.coder.aichat.AiChatApp
import com.coder.aichat.R
import com.coder.aichat.data.api.providers.AiModel
import com.coder.aichat.data.api.providers.AiProvider
import com.coder.aichat.data.api.providers.CustomOpenAiProvider
import com.coder.aichat.data.api.providers.ProviderRegistry
import com.coder.aichat.data.update.UpdateManager
import com.coder.aichat.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROVIDER_ID = "provider_id"
    }

    private lateinit var binding: ActivitySettingsBinding
    private var currentProvider: AiProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupProviderSelector()
        setupModelFetch()
        setupSaveActions()
        setupSearchSection()
        setupUpdateSection()

        // 显示版本号
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "1.1" }
        binding.tvVersion.text = "Nexa AI v$version · 多厂商智能中枢"

        // 预选中厂商（从主界面跳转）
        val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)
        if (providerId != null) {
            val index = ProviderRegistry.providerList.indexOfFirst { it.id == providerId }
            if (index >= 0) {
                binding.spinnerProvider.setText(ProviderRegistry.providerList[index].displayName, false)
                currentProvider = ProviderRegistry.providerList[index]
                loadProviderConfig()
            }
        }
    }

    private fun setupProviderSelector() {
        val providers = ProviderRegistry.providerList
        val names = providers.map { it.displayName }
        binding.spinnerProvider.setSimpleItems(names.toTypedArray())
        binding.spinnerProvider.setOnItemClickListener { _, _, position, _ ->
            currentProvider = providers[position]
            loadProviderConfig()
        }
    }

    private fun loadProviderConfig() {
        val provider = currentProvider ?: return
        lifecycleScope.launch {
            val settings = (application as AiChatApp).settings
            val apiKey = settings.getApiKey(provider.id)
            val baseUrl = settings.getBaseUrl(provider.id)

            binding.etApiKey.setText(apiKey)
            binding.etBaseUrl.setText(baseUrl.ifBlank { provider.defaultBaseUrl() })
            // 本地服务不需要 key，置灰提示
            binding.layoutApiKey.isEnabled = provider.requiresApiKey
            if (!provider.requiresApiKey) {
                binding.etApiKey.setText("无需 API Key")
            }

            // 中转站：显示模型管理区并载入已存模型
            val isCustom = provider is CustomOpenAiProvider
            binding.layoutModels.isVisible = isCustom
            if (isCustom) {
                val custom = provider as CustomOpenAiProvider
                binding.etModels.setText(settings.getCustomModels().joinToString("\n"))
                binding.btnFetchModels.isEnabled = true
            }
        }
    }

    private fun setupModelFetch() {
        binding.btnFetchModels.setOnClickListener {
            val provider = currentProvider as? CustomOpenAiProvider ?: return@setOnClickListener
            binding.btnFetchModels.isEnabled = false
            binding.btnFetchModels.text = "拉取中…"
            lifecycleScope.launch {
                val models = provider.fetchModels()
                // 拉取结果填充模型输入框
                val hasError = models.any { it.id.startsWith("__error__") }
                if (hasError) {
                    val err = models.firstOrNull { it.id.startsWith("__error__") }
                    binding.etModels.setText(err?.description.orEmpty())
                    Toast.makeText(this@SettingsActivity, "拉取失败，请检查 Base URL 与 Key", Toast.LENGTH_SHORT).show()
                } else {
                    binding.etModels.setText(models.joinToString("\n") { it.id })
                    Toast.makeText(this@SettingsActivity, "拉取到 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
                }
                binding.btnFetchModels.isEnabled = true
                binding.btnFetchModels.text = "从接口拉取模型"
            }
        }
    }

    /** 联网搜索配置 */
    private fun setupSearchSection() {
        val engines = com.coder.aichat.data.search.SearchEngine.entries
        binding.spinnerSearchEngine.setSimpleItems(engines.map { it.displayName }.toTypedArray())
        binding.spinnerSearchEngine.setOnItemClickListener { _, _, position, _ ->
            lifecycleScope.launch {
                (application as AiChatApp).settings.setSearchEngine(engines[position].id)
            }
        }

        binding.btnSaveSearch.setOnClickListener {
            val key = binding.etSearchKey.text?.toString().orEmpty().trim()
            lifecycleScope.launch {
                val settings = (application as AiChatApp).settings
                settings.setSearchApiKey(key)
                settings.setSearchEnabled(binding.switchSearch.isChecked)
                Toast.makeText(this@SettingsActivity, "搜索配置已保存", Toast.LENGTH_SHORT).show()
            }
        }

        // 加载已有配置
        lifecycleScope.launch {
            val settings = (application as AiChatApp).settings
            val engineId = settings.getSearchEngine()
            val idx = engines.indexOfFirst { it.id == engineId }
            if (idx >= 0) binding.spinnerSearchEngine.setText(engines[idx].displayName, false)
            binding.etSearchKey.setText(settings.getSearchApiKey())
            binding.switchSearch.isChecked = settings.getSearchEnabled()
        }
    }

    /** 软件更新配置 */
    private fun setupUpdateSection() {
        lifecycleScope.launch {
            val settings = (application as AiChatApp).settings
            binding.etUpdateUrl.setText(settings.getUpdateUrl())
            binding.switchUpdateAuto.isChecked = settings.getUpdateAutoCheck()
        }

        binding.btnCheckUpdate.setOnClickListener {
            val url = binding.etUpdateUrl.text?.toString()?.trim().orEmpty()
            lifecycleScope.launch {
                val settings = (application as AiChatApp).settings
                settings.setUpdateUrl(url)
                settings.setUpdateAutoCheck(binding.switchUpdateAuto.isChecked)
                if (url.isBlank()) {
                    Toast.makeText(this@SettingsActivity, "请先填写更新检查地址", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                binding.btnCheckUpdate.isEnabled = false
                binding.btnCheckUpdate.text = "检查中…"
                val version = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
                } catch (_: Exception) { "0" }
                val info = (application as AiChatApp).updateChecker.check(url, version)
                binding.btnCheckUpdate.isEnabled = true
                binding.btnCheckUpdate.text = "立即检查更新"
                if (info != null) {
                    UpdateManager.showUpdateDialog(this@SettingsActivity, lifecycleScope, info)
                } else {
                    Toast.makeText(this@SettingsActivity, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSaveActions() {
        binding.btnSaveProvider.setOnClickListener {
            val provider = currentProvider ?: return@setOnClickListener
            val apiKey = binding.etApiKey.text?.toString().orEmpty().trim()
            val baseUrl = binding.etBaseUrl.text?.toString().orEmpty().trim()

            lifecycleScope.launch {
                val settings = (application as AiChatApp).settings
                if (provider.requiresApiKey) {
                    settings.setApiKey(provider.id, apiKey)
                    provider.setApiKey(apiKey)
                }
                // 只要非空就持久化（包括填回默认值，避免残留旧自定义 URL）
                if (baseUrl.isNotBlank()) {
                    settings.setBaseUrl(provider.id, baseUrl)
                }
                provider.setBaseUrl(baseUrl)

                // 中转站：保存模型列表
                if (provider is CustomOpenAiProvider) {
                    val modelIds = binding.etModels.text?.toString()
                        ?.lineSequence()?.map { it.trim() }?.filter { it.isNotBlank() }?.toList()
                        .orEmpty()
                    settings.setCustomModels(modelIds)
                    provider.setModels(modelIds.map { AiModel(it, it) })
                }
                Toast.makeText(this@SettingsActivity, "已保存", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveGeneral.setOnClickListener {
            val prompt = binding.etSystemPrompt.text?.toString().orEmpty()
            lifecycleScope.launch {
                (application as AiChatApp).settings.setSystemPrompt(prompt)
                Toast.makeText(this@SettingsActivity, "已保存", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearAll.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_clear_all))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    lifecycleScope.launch {
                        val repo = (application as AiChatApp).repository
                        repo.getAllConversations().first().forEach { repo.deleteConversation(it.id) }
                        Toast.makeText(this@SettingsActivity, "已清空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 加载系统提示词
        lifecycleScope.launch {
            val prompt = (application as AiChatApp).settings.getSystemPrompt()
            binding.etSystemPrompt.setText(prompt)
        }
    }
}
