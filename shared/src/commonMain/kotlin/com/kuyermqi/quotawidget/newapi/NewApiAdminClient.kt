package com.kuyermqi.quotawidget.newapi

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.NewApiAdminStats
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * NewAPI admin dashboard stats (数据看板): hourly per-model quota_data rows plus
 * realtime rpm/tpm. Requires an admin system access token (console → personal
 * settings) and the matching `New-Api-User` id header.
 */
class NewApiAdminClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchStats(
        baseUrl: String,
        adminToken: String,
        adminUserId: String,
        nowEpochMs: Long = currentTimeMillis(),
    ): NewApiAdminStats {
        val nowSec = nowEpochMs / 1000L
        // One month-range request covers both windows: rows are hourly, so the
        // day totals are the rows of the trailing 24h slice.
        val monthRows = getQuotaDataRows(
            baseUrl = baseUrl,
            adminToken = adminToken,
            adminUserId = adminUserId,
            startSec = nowSec - MONTH_RANGE_SEC,
            endSec = nowSec,
        )
        val currentHour = nowSec - (nowSec % 3600L)
        val daySeries = buildSeries(
            rows = monthRows.filter { it.createdAt >= currentHour - 23L * 3600L },
            fromSec = currentHour - 23L * 3600L,
            toSec = currentHour,
            bucketSec = 3600L,
            buckets = 24,
        )
        val dayStartSec = nowSec - DAY_RANGE_SEC
        val monthSeries = buildSeries(
            rows = monthRows,
            fromSec = nowSec - (MONTH_RANGE_SEC - DAY_RANGE_SEC),
            toSec = nowSec,
            bucketSec = DAY_RANGE_SEC,
            buckets = 30,
        )
        val dayRows = monthRows.filter { it.createdAt >= nowSec - DAY_RANGE_SEC }
        return NewApiAdminStats(
            tokensDay = dayRows.sumOf { it.tokenUsed },
            tokensMonth = monthRows.sumOf { it.tokenUsed },
            requestsDay = dayRows.sumOf { it.count },
            requestsMonth = monthRows.sumOf { it.count },
            daySeries = daySeries,
            monthSeries = monthSeries,
            updatedAtEpochMs = nowEpochMs,
        )
    }

    /**
     * Sums [NewApiQuotaDataRow.tokenUsed] into [buckets] fixed-size buckets.
     * Rows are hourly and `created_at` is hour-floored, so bucketing is exact.
     */
    internal fun buildSeries(
        rows: List<NewApiQuotaDataRow>,
        fromSec: Long,
        toSec: Long,
        bucketSec: Long,
        buckets: Int,
    ): List<Long> {
        val series = LongArray(buckets)
        for (row in rows) {
            val index = ((row.createdAt - fromSec) / bucketSec).toInt()
            if (index in series.indices) {
                series[index] += row.tokenUsed
            }
        }
        // Newest bucket ends at `toSec` (partial window still counts as the last point).
        return series.toList()
    }

    private suspend fun getQuotaDataRows(
        baseUrl: String,
        adminToken: String,
        adminUserId: String,
        startSec: Long,
        endSec: Long,
    ): List<NewApiQuotaDataRow> {
        val body = getAdminJson(
            url = quotaDataUrl(baseUrl, startSec, endSec),
            adminToken = adminToken,
            adminUserId = adminUserId,
        )
        val envelope = try {
            json.decodeFromString(NewApiQuotaDataEnvelope.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException("NewAPI 看板数据无法解析", e)
        }
        if (envelope.success != true) {
            throw IllegalStateException(
                envelope.message?.takeIf { it.isNotBlank() } ?: "查询 NewAPI 看板数据失败",
            )
        }
        return envelope.data
    }

    private suspend fun getLogStat(
        baseUrl: String,
        adminToken: String,
        adminUserId: String,
        startSec: Long,
        endSec: Long,
    ): NewApiLogStat {
        val body = getAdminJson(
            url = logStatUrl(baseUrl, startSec, endSec),
            adminToken = adminToken,
            adminUserId = adminUserId,
        )
        val envelope = try {
            json.decodeFromString(NewApiLogStatEnvelope.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException("NewAPI 实时指标无法解析", e)
        }
        if (envelope.success != true) {
            throw IllegalStateException(
                envelope.message?.takeIf { it.isNotBlank() } ?: "查询 NewAPI 实时指标失败",
            )
        }
        return envelope.data
    }

    private suspend fun getAdminJson(
        url: String,
        adminToken: String,
        adminUserId: String,
    ): String {
        val response = httpClient.get(url) {
            header(HttpHeaders.Authorization, adminToken)
            // Dashboard auth on current new-api rejects requests without this header.
            header(NEW_API_USER_HEADER, adminUserId)
            accept(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.Unauthorized ||
            response.status == HttpStatusCode.Forbidden
        ) {
            throw IllegalStateException("NewAPI 管理员鉴权失败，请检查访问令牌与用户 ID")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("查询 NewAPI 管理数据失败: ${response.status}")
        }
        return response.bodyAsText()
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val NEW_API_USER_HEADER = "New-Api-User"
        const val DAY_RANGE_SEC = 24L * 3600L
        const val MONTH_RANGE_SEC = 30L * DAY_RANGE_SEC

        fun normalizeBaseUrl(baseUrl: String): String =
            baseUrl.trim().trimEnd('/')

        fun quotaDataUrl(baseUrl: String, startSec: Long, endSec: Long): String =
            "${normalizeBaseUrl(baseUrl)}/api/data/?start_timestamp=$startSec&end_timestamp=$endSec"

        fun logStatUrl(baseUrl: String, startSec: Long, endSec: Long): String =
            "${normalizeBaseUrl(baseUrl)}/api/log/stat?start_timestamp=$startSec&end_timestamp=$endSec"
    }
}

@Serializable
data class NewApiQuotaDataEnvelope(
    val success: Boolean? = null,
    val message: String? = null,
    val data: List<NewApiQuotaDataRow> = emptyList(),
)

/** One hourly per-model aggregate row of the admin dashboard (`quota_data`). */
@Serializable
data class NewApiQuotaDataRow(
    @SerialName("model_name") val modelName: String? = null,
    /** Hour-floored Unix seconds. */
    @SerialName("created_at") val createdAt: Long = 0L,
    val count: Long = 0L,
    val quota: Long = 0L,
    @SerialName("token_used") val tokenUsed: Long = 0L,
)

@Serializable
data class NewApiLogStatEnvelope(
    val success: Boolean? = null,
    val message: String? = null,
    val data: NewApiLogStat = NewApiLogStat(),
)

@Serializable
data class NewApiLogStat(
    val quota: Long = 0L,
    val rpm: Long = 0L,
    val tpm: Long = 0L,
)
