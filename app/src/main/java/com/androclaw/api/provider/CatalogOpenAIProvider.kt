package com.androclaw.api.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * OpenAI-compatible provider wrapper with live model discovery.
 * Works with gateways that expose the standard GET /models response.
 */
class CatalogOpenAIProvider(
    private val delegate: LlmProvider,
    private val okHttpClient: OkHttpClient,
    override val id: String,
    override val displayName: String,
    private val catalogUrl: String
) : LlmProvider by delegate {

    override suspend fun fetchModels(apiKey: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(catalogUrl)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty model catalogue response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("$displayName model catalogue error ${response.code}: $body"))
            }

            val data = JSONObject(body).optJSONArray("data")
                ?: return@withContext Result.failure(Exception("$displayName returned an unexpected model catalogue"))
            val models = mutableListOf<ModelInfo>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val architecture = item.optJSONObject("architecture")
                val modalities = architecture?.optJSONArray("input_modalities")
                val vision = modalities != null && (0 until modalities.length()).any {
                    modalities.optString(it).equals("image", ignoreCase = true)
                }
                models.add(ModelInfo(
                    id = id,
                    displayName = item.optString("name").takeIf { it.isNotBlank() } ?: id,
                    contextWindow = item.optInt("context_length", 0),
                    supportsVision = vision
                ))
            }
            if (models.isEmpty()) Result.failure(Exception("$displayName returned no usable models"))
            else Result.success(models.sortedBy { it.displayName.lowercase() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
