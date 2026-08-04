package com.coder.aichat.data.api.providers

/**
 * 供应商注册中心 — 管理所有 AI 厂商实例。
 * 新增厂商只需 register() 即可全局生效。
 */
object ProviderRegistry {
    private val _providers = mutableMapOf<String, AiProvider>()
    val providers: Map<String, AiProvider> get() = _providers.toMap()
    val providerList: List<AiProvider> get() = _providers.values.toList()

    fun register(provider: AiProvider) {
        _providers[provider.id] = provider
    }

    fun get(id: String): AiProvider? = _providers[id]

    fun initDefault() {
        register(OpenAiProvider())
        register(ClaudeProvider())
        register(GeminiProvider())
        register(DeepSeekProvider())
        register(GroqProvider())
        register(OllamaProvider())
        // 国内厂商
        register(KimiProvider())
        register(ZhipuProvider())
        register(QwenProvider())
        register(VolcengineProvider())
        register(MiniMaxProvider())
        // 自定义中转站
        register(CustomOpenAiProvider())
    }
}
