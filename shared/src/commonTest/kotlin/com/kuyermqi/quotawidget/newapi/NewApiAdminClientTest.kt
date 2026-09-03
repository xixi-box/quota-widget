package com.kuyermqi.quotawidget.newapi

import com.kuyermqi.quotawidget.domain.formatTokenCount
import com.kuyermqi.quotawidget.domain.averageRpmDay
import com.kuyermqi.quotawidget.domain.averageTpmDay
import com.kuyermqi.quotawidget.domain.formatNewApiAdminRate
import com.kuyermqi.quotawidget.domain.formatRequestCount
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewApiAdminClientTest {
    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): NewApiAdminClient = NewApiAdminClient(
        httpClient = HttpClient(
            MockEngine(MockEngineConfig().apply { addHandler(handler) }),
        ),
    )

    @Test
    fun quotaDataUrl_buildsWithTimestamps() {
        assertEquals(
            "https://nw.example.com/api/data/?start_timestamp=100&end_timestamp=200",
            NewApiAdminClient.quotaDataUrl("https://nw.example.com/", 100L, 200L),
        )
        assertEquals(
            "http://host/api/log/stat?start_timestamp=1&end_timestamp=2",
            NewApiAdminClient.logStatUrl("http://host", 1L, 2L),
        )
    }

    @Test
    fun fetchStats_sumsDayAndMonthAndSendsAuthHeaders() = runTest {
        val nowSec = 1_788_300_000L
        val dayStart = nowSec - NewApiAdminClient.DAY_RANGE_SEC
        var sawAuth: String? = null
        var sawUser: String? = null
        var requestCount = 0

        val stats = client { request ->
            requestCount += 1
            sawAuth = request.headers[HttpHeaders.Authorization]
            sawUser = request.headers[NewApiAdminClient.NEW_API_USER_HEADER]
            if ("/api/data/" in request.url.toString()) {
                respondOk(
                    """
                    {"success":true,"message":"","data":[
                      {"model_name":"a","created_at":${dayStart + 3600},"count":10,"quota":500000,"token_used":1000},
                      {"model_name":"b","created_at":${dayStart + 7200},"count":5,"quota":250000,"token_used":2000},
                      {"model_name":"a","created_at":${dayStart - 3600},"count":99,"quota":1000000,"token_used":9000}
                    ]}
                    """.trimIndent(),
                )
            } else {
                respondOk(
                    """{"success":true,"message":"","data":{"quota":6235000,"rpm":3,"tpm":456}}""",
                )
            }
        }.fetchStats(
            baseUrl = "http://host",
            adminToken = "tok",
            adminUserId = "1",
            nowEpochMs = nowSec * 1000L,
        )

        assertEquals("tok", sawAuth)
        assertEquals("1", sawUser)
        assertEquals(1, requestCount)
        // The two rows inside the trailing 24h slice.
        assertEquals(3000L, stats.tokensDay)
        assertEquals(15L, stats.requestsDay)
        assertEquals(12000L, stats.tokensMonth)
        assertEquals(114L, stats.requestsMonth)
        assertEquals("0.01", formatNewApiAdminRate(stats.averageRpmDay()))
        assertEquals("2.08", formatNewApiAdminRate(stats.averageTpmDay()))
        // Hourly buckets covering the trailing 24h (now is hour-aligned):
        // the two in-range rows land in buckets 0 and 1.
        assertEquals(24, stats.daySeries.size)
        assertEquals(1000L, stats.daySeries[0])
        assertEquals(2000L, stats.daySeries[1])
        assertEquals(3000L, stats.daySeries.sum())
        assertEquals(30, stats.monthSeries.size)
        assertEquals(12000L, stats.monthSeries.sum())
    }

    @Test
    fun fetchStats_throwsOnAuthFailure() = runTest {
        val client = client {
            respondError(HttpStatusCode.Unauthorized)
        }
        val error = runCatching {
            client.fetchStats("http://host", "bad", "1")
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue("鉴权失败" in (error?.message ?: ""))
    }

    @Test
    fun fetchStats_throwsOnFailureEnvelope() = runTest {
        val client = client { request ->
            if ("/api/data/" in request.url.toString()) {
                respondOk("""{"success":false,"message":"no permission"}""")
            } else {
                respondOk("""{"success":true,"message":"","data":{"quota":0,"rpm":0,"tpm":0}}""")
            }
        }
        val error = runCatching {
            client.fetchStats("http://host", "tok", "1")
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("no permission", error?.message)
    }

    @Test
    fun formatTokenCount_usesCompactSuffixes() {
        assertEquals("1234", formatTokenCount(1234L))
        assertEquals("12.3K", formatTokenCount(12_300L))
        assertEquals("29.1M", formatTokenCount(29_086_040L))
        assertEquals("3.5B", formatTokenCount(3_460_228_948L))
    }

    @Test
    fun dayAverageRates_matchDashboardCalculation() {
        val stats = com.kuyermqi.quotawidget.domain.NewApiAdminStats(
            tokensDay = 66_270_442L,
            requestsDay = 1_384L,
        )

        assertEquals("0.96", formatNewApiAdminRate(stats.averageRpmDay()))
        assertEquals("46,021.14", formatNewApiAdminRate(stats.averageTpmDay()))
        assertEquals("1,384", formatRequestCount(stats.requestsDay))
    }
}
