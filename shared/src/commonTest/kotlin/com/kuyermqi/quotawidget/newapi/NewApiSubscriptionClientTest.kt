package com.kuyermqi.quotawidget.newapi

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewApiSubscriptionClientTest {
    /** Real response shape captured from a live new-api subscription endpoint. */
    private val liveBody = """
        {
          "data": {
            "all_subscriptions": [],
            "billing_preference": "subscription_only",
            "subscriptions": [
              {"subscription": {
                "id": 17, "user_id": 1, "plan_id": 1,
                "amount_total": 90000000, "amount_used": 2920000,
                "start_time": 1786369516, "end_time": 1789047916,
                "status": "active", "source": "admin",
                "last_reset_time": 0, "next_reset_time": 0
              }}
            ]
          },
          "message": "",
          "success": true
        }
    """.trimIndent()

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): NewApiSubscriptionClient = NewApiSubscriptionClient(
        httpClient = HttpClient(
            MockEngine(MockEngineConfig().apply { addHandler(handler) }),
        ),
    )

    @Test
    fun subscriptionSelfUrl_appendsPath() {
        assertEquals(
            "https://nw.example.com/api/subscription/self",
            NewApiSubscriptionClient.subscriptionSelfUrl("https://nw.example.com/"),
        )
    }

    @Test
    fun fetchSubscription_mapsActiveSubscription() = runTest {
        var sawAuth: String? = null
        var sawUser: String? = null
        val snapshot = client { request ->
            sawAuth = request.headers[HttpHeaders.Authorization]
            sawUser = request.headers[NewApiAdminClient.NEW_API_USER_HEADER]
            respondOk(liveBody)
        }.fetchSubscription(
            baseUrl = "http://host",
            accessToken = "tok",
            userId = "1",
            quotaPerUsd = 500_000L,
        )

        assertEquals("tok", sawAuth)
        assertEquals("1", sawUser)
        assertEquals(QuotaWindowKind.SUBSCRIPTION, snapshot.windows.single().kind)
        assertEquals("$174.16", snapshot.primaryDisplay)
        assertEquals("$5.84", snapshot.usedDisplay)
        // 2920000 / 90000000 ≈ 3.24%
        assertEquals(3.2444, snapshot.windows.single().usedPercent!!, absoluteTolerance = 0.001)
    }

    @Test
    fun fetchSubscription_throwsWithoutActiveSubscription() = runTest {
        val client = client {
            respondOk("""{"success":true,"message":"","data":{"subscriptions":[]}}""")
        }
        val error = runCatching {
            client.fetchSubscription("http://host", "tok", "1", 500_000L)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue("订阅" in (error?.message ?: ""))
    }

    @Test
    fun fetchSubscription_throwsOnAuthFailure() = runTest {
        val client = client {
            respondError(HttpStatusCode.Unauthorized)
        }
        val error = runCatching {
            client.fetchSubscription("http://host", "bad", "1", 500_000L)
        }.exceptionOrNull()
        assertTrue(error is com.kuyermqi.quotawidget.domain.SessionExpiredException)
    }

    @Test
    fun usedPercent_nullWhenPoolEmpty() {
        assertNull(NewApiSubscriptionClient.usedPercent(0L, 0L))
        assertEquals(50.0, NewApiSubscriptionClient.usedPercent(1_000_000L, 500_000L))
    }

    @Test
    fun resetInSeconds_prefersPeriodicResetThenExpiry() {
        val now = 1_788_300_000_000L
        assertEquals(
            3600L,
            NewApiSubscriptionClient.resetInSeconds(
                nextResetTimeSec = now / 1000L + 3600L,
                endTimeSec = now / 1000L + 100L,
                nowEpochMs = now,
            ),
        )
        assertEquals(
            100L,
            NewApiSubscriptionClient.resetInSeconds(
                nextResetTimeSec = 0L,
                endTimeSec = now / 1000L + 100L,
                nowEpochMs = now,
            ),
        )
        assertNull(
            NewApiSubscriptionClient.resetInSeconds(0L, 0L, now),
        )
    }

    @Test
    fun decode_liveJsonShape() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val envelope = json.decodeFromString(NewApiSubscriptionEnvelope.serializer(), liveBody)
        val sub = envelope.data?.subscriptions?.single()?.subscription
        assertEquals(90_000_000L, sub?.amountTotal)
        assertEquals("active", sub?.status)
    }
}
