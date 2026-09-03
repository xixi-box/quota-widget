package com.kuyermqi.quotawidget.zhipu

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GLM Coding Plan usage via the undocumented community quota endpoint
 * (same one as cc-switch): `GET /api/monitor/usage/quota/limit`.
 * Auth is the bare API key without a Bearer prefix.
 * Read-only, so a breaking change degrades to an error, never to data loss.
 */
class ZhipuUsageClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchUsage(apiKey: String): QuotaSnapshot {
        val response = try {
            httpClient.get(USAGE_URL) {
                // BigModel coding-plan quota endpoint expects the raw key, no Bearer prefix.
                header(HttpHeaders.Authorization, apiKey)
                accept(ContentType.Application.Json)
            }
        } catch (e: ClientRequestException) {
            val status = e.response.status
            if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
                throw SessionExpiredException("智谱 API Key 无效或已失效，请检查配置")
            }
            throw IllegalStateException("查询智谱用量失败: $status", e)
        }
        if (response.status == HttpStatusCode.Unauthorized ||
            response.status == HttpStatusCode.Forbidden
        ) {
            throw SessionExpiredException("智谱 API Key 无效或已失效，请检查配置")
        }
        val body = response.bodyAsText()
        if (body.isBlank()) {
            throw IllegalStateException("智谱返回为空")
        }
        val envelope = try {
            json.decodeFromString(ZhipuUsageEnvelope.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException("智谱返回无法解析", e)
        }
        if (envelope.code != 200 && envelope.success != true) {
            val message = envelope.msg?.takeIf { it.isNotBlank() } ?: "查询智谱用量失败"
            throw IllegalStateException(message)
        }
        val data = envelope.data ?: throw IllegalStateException("智谱返回数据为空")
        val windows = toWindows(data.limits)
        if (windows.isEmpty()) {
            throw IllegalStateException("无法解析智谱用量窗口")
        }
        return QuotaSnapshot(
            platformId = PlatformIds.ZHIPU,
            platformName = PlatformRegistry.displayName(PlatformIds.ZHIPU),
            windows = windows,
            primaryDisplay = formatUsagePrimaryDisplay(windows),
            updatedAtEpochMs = currentTimeMillis(),
        )
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val USAGE_URL = "https://open.bigmodel.cn/api/monitor/usage/quota/limit"

        /**
         * Quota entries of interest; other types (e.g. TIME_LIMIT MCP quota) are ignored.
         * The type name drifted across plan generations (TOKENS_LIMIT vs CREDIT_LIMIT),
         * so both are accepted and the window is anchored on `unit` only.
         */
        fun isUsageLimit(type: String?): Boolean =
            type != null && (
                type.equals("TOKENS_LIMIT", ignoreCase = true) ||
                    type.equals("CREDIT_LIMIT", ignoreCase = true)
                )

        /** `unit: 3` → 5h rolling window; `unit: 6` → weekly window. */
        fun classifyUnit(unit: Int?): QuotaWindowKind? = when (unit) {
            3 -> QuotaWindowKind.FIVE_HOUR
            6 -> QuotaWindowKind.WEEKLY
            else -> null
        }

        fun toWindows(
            limits: List<ZhipuLimitDto>,
            nowEpochMs: Long = currentTimeMillis(),
        ): List<QuotaWindow> {
            val mapped = linkedMapOf<QuotaWindowKind, QuotaWindow>()
            for (limit in limits) {
                if (!isUsageLimit(limit.type)) continue
                val kind = classifyUnit(limit.unit) ?: continue
                val usedPercent = limit.percentage ?: continue
                mapped[kind] = QuotaWindow(
                    kind = kind,
                    usedPercent = usedPercent,
                    resetInSec = resetInSeconds(limit.nextResetTime, nowEpochMs),
                )
            }
            return listOfNotNull(
                mapped[QuotaWindowKind.FIVE_HOUR],
                mapped[QuotaWindowKind.WEEKLY],
                mapped[QuotaWindowKind.MONTHLY],
            )
        }

        /** `nextResetTime` is epoch milliseconds; clamp negatives (clock skew) to 0. */
        fun resetInSeconds(nextResetTimeEpochMs: Long?, nowEpochMs: Long): Long? {
            if (nextResetTimeEpochMs == null || nextResetTimeEpochMs <= 0L) return null
            return ((nextResetTimeEpochMs - nowEpochMs) / 1000L).coerceAtLeast(0L)
        }
    }
}

@Serializable
data class ZhipuUsageEnvelope(
    val code: Int? = null,
    val msg: String? = null,
    val success: Boolean? = null,
    val data: ZhipuUsageData? = null,
)

@Serializable
data class ZhipuUsageData(
    val limits: List<ZhipuLimitDto> = emptyList(),
    /** Plan tier, e.g. "lite" / "pro" / "max". */
    val level: String? = null,
)

@Serializable
data class ZhipuLimitDto(
    val type: String? = null,
    val unit: Int? = null,
    val number: Int? = null,
    val usage: Double? = null,
    val currentValue: Double? = null,
    val remaining: Double? = null,
    /** Already the used percent (0-100). */
    val percentage: Double? = null,
    /** Epoch milliseconds. */
    val nextResetTime: Long? = null,
)
