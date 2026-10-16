package com.kuyermqi.quotawidget.modelsdev

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Free-model catalog for OpenCode, read from the public models.dev directory.
 * Needs no credentials, so the list is available before signing in.
 *
 * The catalog is a single ~5.6MB document, so only the `opencode` provider is
 * decoded (unknown keys are skipped, keeping memory proportional to our model
 * subset) and callers are expected to cache the result.
 */
class ModelsDevClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    suspend fun fetchFreeModels(): List<String> {
        val body = httpClient.get(CATALOG_URL) {
            header(HttpHeaders.Accept, "application/json")
        }.bodyAsText()
        val catalog = runCatching {
            json.decodeFromString(ModelsDevCatalog.serializer(), body)
        }.getOrNull() ?: throw IllegalStateException("models.dev 返回无法解析")

        val models = catalog.opencode?.models.orEmpty()
        return models
            .filter { (_, model) -> model.isFree }
            .map { (id, model) -> model.name.ifBlank { id } }
            .sorted()
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val CATALOG_URL = "https://models.dev/api.json"
    }
}

@Serializable
private data class ModelsDevCatalog(
    val opencode: ModelsDevProvider? = null,
)

@Serializable
private data class ModelsDevProvider(
    val models: Map<String, ModelsDevModel> = emptyMap(),
)

@Serializable
internal data class ModelsDevModel(
    val name: String = "",
    /** Present (e.g. "deprecated") for retired entries kept for compatibility. */
    val status: String? = null,
    val cost: ModelsDevCost? = null,
) {
    val isFree: Boolean
        get() = status == null &&
            cost != null &&
            cost.input == 0.0 &&
            cost.output == 0.0
}

@Serializable
internal data class ModelsDevCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
)
