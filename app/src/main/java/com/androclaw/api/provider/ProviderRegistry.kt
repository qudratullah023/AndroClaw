package com.androclaw.api.provider

import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all available LLM providers.
 * Manages provider instances and model lookups.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val providers = mutableMapOf<String, LlmProvider>()

    init {
        val claude = ClaudeProvider(okHttpClient)
        val openai = OpenAIProvider(okHttpClient)
        val gemini = GeminiProvider(okHttpClient)

        // Groq uses OpenAI-compatible API
        val groq = OpenAIProvider(
            okHttpClient = okHttpClient,
            id = "groq",
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            supportedModels = listOf(
                ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B", 131072),
                ModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B", 131072),
                ModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B", 32768),
                ModelInfo("gemma2-9b-it", "Gemma 2 9B", 8192)
            )
        )

        // OpenRouter — live catalogue is loaded from GET /api/v1/models.
        val openRouter = CatalogOpenAIProvider(
            delegate = OpenAIProvider(
                okHttpClient = okHttpClient,
                id = "openrouter",
                displayName = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                supportedModels = listOf(
                    ModelInfo("google/gemini-4-hydrogen", "Gemini 4 Hydrogen", 1048576, supportsVision = true),
                    ModelInfo("google/gemini-4-carbon", "Gemini 4 Carbon", 1048576, supportsVision = true),
                    ModelInfo("google/gemini-3.8-flash", "Gemini 3.8 Flash", 1000000, supportsVision = true),
                    ModelInfo("anthropic/claude-fable-5.2", "Claude Fable 5.2", 1000000, supportsVision = true),
                    ModelInfo("meta/muse-ember", "Muse Ember", 1048576, supportsVision = true),
                    ModelInfo("deepseek/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", 1000000),
                    ModelInfo("z-ai/glm-5.3-flash", "GLM 5.3 Flash", 200000),
                    ModelInfo("thinkingmachines/inkling-2-small", "Inkling 2 Small", 1048576, supportsVision = true),
                    ModelInfo("baidu/ernie-5.5-flash-preview:free", "ERNIE 5.5 Flash Preview (Free)", 1024000),
                    ModelInfo("liquid/lfm-3-1.7b", "LiquidAI LFM3 1.7B", 262144)
                )
            ),
            okHttpClient = okHttpClient,
            id = "openrouter",
            displayName = "OpenRouter",
            catalogUrl = "https://openrouter.ai/api/v1/models"
        )

        // xKiro — OpenAI-compatible gateway with vendor/model IDs and live catalogue.
        val xkiro = CatalogOpenAIProvider(
            delegate = OpenAIProvider(
                okHttpClient = okHttpClient,
                id = "xkiro",
                displayName = "xKiro",
                baseUrl = "https://api.xkiro.com/v1",
                supportedModels = listOf(
                    ModelInfo("openai/gpt-5.6-sol", "GPT-5.6 Sol (xKiro)", 256000),
                    ModelInfo("anthropic/claude-opus-5", "Claude Opus 5 (xKiro)", 200000),
                    ModelInfo("z-ai/glm-5.2", "GLM 5.2 (xKiro)", 200000)
                )
            ),
            okHttpClient = okHttpClient,
            id = "xkiro",
            displayName = "xKiro",
            catalogUrl = "https://api.xkiro.com/v1/models"
        )

        register(claude)
        register(openai)
        register(gemini)
        register(groq)
        register(openRouter)
        register(xkiro)
    }

    fun register(provider: LlmProvider) {
        providers[provider.id] = provider
    }

    fun getProvider(id: String): LlmProvider? = providers[id]

    fun getAllProviders(): List<LlmProvider> = providers.values.toList()

    fun getProviderForModel(modelId: String): LlmProvider? {
        return providers.values.firstOrNull { provider ->
            provider.supportedModels.any { it.id == modelId }
        }
    }

    /**
     * Get all available models across all providers.
     * Returns pairs of (providerId, ModelInfo).
     */
    fun getAllModels(): List<Pair<String, ModelInfo>> {
        return providers.flatMap { (providerId, provider) ->
            provider.supportedModels.map { providerId to it }
        }
    }
}
