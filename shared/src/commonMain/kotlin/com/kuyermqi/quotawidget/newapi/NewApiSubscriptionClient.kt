package com.kuyermqi.quotawidget.newapi

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
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
 * NewAPI subscription pool balance via `GET /api/subscription/self`.
 * Uses dashboard auth (system access token + `New-Api-User` id), NOT the sk-
 * relay key — subscription billing never shows up in token quota endpoints.
 */
class NewApiSubscriptionClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchSubscription(
        baseUrl: String,
        accessToken: String,
        userId: String,
        quotaPerUsd: Long,
    ): QuotaSnapshot {
        val url = subscriptionSelfUrl(baseUrl)
        val response = try {
            httpClient.get(url) {
                header(HttpHeaders.Authorization, accessToken)
                header(NewApiAdminClient.NEW_API_USER_HEADER, userId)
                accept(ContentType.Application.Json)
            }
        } catch (e: ClientRequestException) {
            val status = e.response.status
            if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
                throw SessionExpiredException("NewAPI 访问令牌无效或已失效，请检查配置")
            }
            throw IllegalStateException("查询 NewAPI 订阅失败: $status", e)
        }
        if (response.status == HttpStatusCode.Unauthorized ||
            response.status == HttpStatusCode.Forbidden
        ) {
            throw SessionExpiredException("NewAPI 访问令牌无效或已失效，请检查配置")
        }
        val body = response.bodyAsText()
        val envelope = try {
            json.decodeFromString(NewApiSubscriptionEnvelope.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException("NewAPI 订阅数据无法解析", e)
        }
        if (envelope.success != true) {
            throw IllegalStateException(
                envelope.message?.takeIf { it.isNotBlank() } ?: "查询 NewAPI 订阅失败",
            )
        }
        val active = envelope.data?.subscriptions
            ?.map { it.subscription }
            ?.firstOrNull { it.status.equals("active", ignoreCase = true) }
            ?: throw IllegalStateException("当前没有生效中的 NewAPI 订阅")
        return toSnapshot(active, quotaPerUsd = quotaPerUsd)
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        fun subscriptionSelfUrl(baseUrl: String): String =
            "${NewApiAdminClient.normalizeBaseUrl(baseUrl)}/api/subscription/self"

        fun resolveQuotaPerUsd(quotaPerUsd: Long): Double =
            if (quotaPerUsd > 0L) {
                quotaPerUsd.toDouble()
            } else {
                DEFAULT_QUOTA_PER_USD.toDouble()
            }

        /** NewAPI quota formatting: two decimals, sub-cent non-zero shows <$0.01. */
        fun formatQuotaUsd(
            quotaUnits: Long,
            quotaPerUsd: Long = DEFAULT_QUOTA_PER_USD,
        ): String {
            val usd = quotaUnits.toDouble() / resolveQuotaPerUsd(quotaPerUsd)
            if (usd == 0.0) {
                return "\$0.00"
            }
            if (kotlin.math.abs(usd) < 0.01) {
                return if (usd < 0.0) "-<\$0.01" else "<\$0.01"
            }
            val cents = kotlin.math.round(usd * 100.0).toLong()
            val sign = if (cents < 0) "-" else ""
            val absCents = kotlin.math.abs(cents)
            val whole = absCents / 100
            val frac = (absCents % 100).toString().padStart(2, '0')
            return "$sign\$$whole.$frac"
        }

        fun usedPercent(amountTotal: Long, amountUsed: Long): Double? {
            if (amountTotal <= 0L) return null
            return (amountUsed.toDouble() / amountTotal.toDouble() * 100.0)
                .coerceIn(0.0, 100.0)
        }

        /** Periodic reset first, plan expiry as fallback; clamped to ≥ 0. */
        fun resetInSeconds(
            nextResetTimeSec: Long,
            endTimeSec: Long,
            nowEpochMs: Long,
        ): Long? {
            val target = when {
                nextResetTimeSec > 0L -> nextResetTimeSec
                endTimeSec > 0L -> endTimeSec
                else -> return null
            }
            return ((target * 1000L - nowEpochMs) / 1000L).coerceAtLeast(0L)
        }

        fun toSnapshot(
            subscription: NewApiUserSubscriptionDto,
            updatedAtEpochMs: Long = currentTimeMillis(),
            quotaPerUsd: Long = DEFAULT_QUOTA_PER_USD,
            nowEpochMs: Long = updatedAtEpochMs,
        ): QuotaSnapshot {
            val total = subscription.amountTotal
            val used = subscription.amountUsed.coerceAtLeast(0L)
            val remaining = (total - used).coerceAtLeast(0L)
            val windows = listOf(
                QuotaWindow(
                    kind = QuotaWindowKind.SUBSCRIPTION,
                    usedPercent = usedPercent(total, used),
                    resetInSec = resetInSeconds(
                        subscription.nextResetTime,
                        subscription.endTime,
                        nowEpochMs,
                    ),
                ),
            )
            return QuotaSnapshot(
                platformId = PlatformIds.NEW_API_SUBSCRIPTION,
                platformName = PlatformRegistry.displayName(PlatformIds.NEW_API_SUBSCRIPTION),
                windows = windows,
                primaryDisplay = formatQuotaUsd(remaining, quotaPerUsd),
                updatedAtEpochMs = updatedAtEpochMs,
                currency = CurrencyPreference.USD,
                totalBalance = (remaining.toDouble() / resolveQuotaPerUsd(quotaPerUsd))
                    .toString(),
                usedDisplay = formatQuotaUsd(used, quotaPerUsd),
            )
        }

        /** NewAPI convention: 500_000 quota = 1 USD. */
        const val DEFAULT_QUOTA_PER_USD = 500_000L
    }
}

@Serializable
data class NewApiSubscriptionEnvelope(
    val success: Boolean? = null,
    val message: String? = null,
    val data: NewApiSubscriptionData? = null,
)

@Serializable
data class NewApiSubscriptionData(
    /** Active subscriptions (expired ones ride in `all_subscriptions`). */
    val subscriptions: List<NewApiSubscriptionSummaryDto> = emptyList(),
)

@Serializable
data class NewApiSubscriptionSummaryDto(
    val subscription: NewApiUserSubscriptionDto = NewApiUserSubscriptionDto(),
)

@Serializable
data class NewApiUserSubscriptionDto(
    val id: Int = 0,
    @SerialName("plan_id") val planId: Int = 0,
    @SerialName("amount_total") val amountTotal: Long = 0L,
    @SerialName("amount_used") val amountUsed: Long = 0L,
    @SerialName("start_time") val startTime: Long = 0L,
    @SerialName("end_time") val endTime: Long = 0L,
    val status: String = "",
    @SerialName("next_reset_time") val nextResetTime: Long = 0L,
)
