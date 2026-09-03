package com.kuyermqi.quotawidget.zhipu

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZhipuUsageClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Real Lite-plan response shape (CREDIT_LIMIT entries, epoch-ms reset). */
    private val liteBody = """
        {
          "code": 200,
          "msg": "操作成功",
          "data": {
            "limits": [
              {"type":"CREDIT_LIMIT","unit":3,"number":5,"usage":2000,"currentValue":122,
               "remaining":1877,"percentage":6,"nextResetTime":1788328012881},
              {"type":"CREDIT_LIMIT","unit":6,"number":1,"usage":10000,"currentValue":2878,
               "remaining":7121,"percentage":28,"nextResetTime":1788586975998}
            ],
            "level": "lite"
          },
          "success": true
        }
    """.trimIndent()

    @Test
    fun decode_litePlanResponse() {
        val envelope = json.decodeFromString(ZhipuUsageEnvelope.serializer(), liteBody)
        assertEquals(200, envelope.code)
        assertEquals("lite", envelope.data?.level)
        assertEquals(2, envelope.data?.limits?.size)
        assertEquals(6.0, envelope.data?.limits?.first()?.percentage)
    }

    @Test
    fun decode_tokensLimitTypeShape() {
        val body = """
            {
              "code": 200,
              "success": true,
              "data": {
                "limits": [
                  {"type":"TOKENS_LIMIT","unit":3,"percentage":42.5,"nextResetTime":1788328012881}
                ]
              }
            }
        """.trimIndent()
        val envelope = json.decodeFromString(ZhipuUsageEnvelope.serializer(), body)
        val windows = ZhipuUsageClient.toWindows(envelope.data?.limits.orEmpty())
        assertEquals(1, windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, windows[0].kind)
        assertEquals(42.5, windows[0].usedPercent)
    }

    @Test
    fun toWindows_mapsUnit3And6ToFiveHourAndWeekly() {
        val limits = listOf(
            ZhipuLimitDto(type = "CREDIT_LIMIT", unit = 3, percentage = 6.0, nextResetTime = 1_788_328_012_881L),
            ZhipuLimitDto(type = "CREDIT_LIMIT", unit = 6, percentage = 28.0, nextResetTime = 1_788_586_975_998L),
        )
        val now = 1_788_300_000_000L
        val windows = ZhipuUsageClient.toWindows(limits, nowEpochMs = now)

        assertEquals(2, windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, windows[0].kind)
        assertEquals(6.0, windows[0].usedPercent)
        assertEquals((1_788_328_012_881L - now) / 1000L, windows[0].resetInSec)
        assertEquals(QuotaWindowKind.WEEKLY, windows[1].kind)
        assertEquals(28.0, windows[1].usedPercent)
    }

    @Test
    fun toWindows_ignoresUnknownTypesAndUnits() {
        val limits = listOf(
            ZhipuLimitDto(type = "TIME_LIMIT", unit = 3, percentage = 10.0),
            ZhipuLimitDto(type = "CREDIT_LIMIT", unit = 9, percentage = 10.0),
            ZhipuLimitDto(type = "CREDIT_LIMIT", unit = 6, percentage = 30.0),
        )
        val windows = ZhipuUsageClient.toWindows(limits)
        assertEquals(1, windows.size)
        assertEquals(QuotaWindowKind.WEEKLY, windows[0].kind)
    }

    @Test
    fun toWindows_nullPercentageSkipsEntry() {
        val limits = listOf(ZhipuLimitDto(type = "CREDIT_LIMIT", unit = 3, percentage = null))
        assertTrue(ZhipuUsageClient.toWindows(limits).isEmpty())
    }

    @Test
    fun resetInSeconds_convertsEpochMsAndClampsNegative() {
        val now = 1_788_300_000_000L
        assertEquals(
            28_012L,
            ZhipuUsageClient.resetInSeconds(1_788_328_012_881L, now),
        )
        assertEquals(0L, ZhipuUsageClient.resetInSeconds(now - 5_000L, now))
        assertNull(ZhipuUsageClient.resetInSeconds(null, now))
    }

    @Test
    fun isUsageLimit_acceptsKnownTypeNames() {
        assertTrue(ZhipuUsageClient.isUsageLimit("CREDIT_LIMIT"))
        assertTrue(ZhipuUsageClient.isUsageLimit("tokens_limit"))
        assertFalse(ZhipuUsageClient.isUsageLimit("TIME_LIMIT"))
        assertFalse(ZhipuUsageClient.isUsageLimit(null))
    }

    @Test
    fun classifyUnit_mapsKnownUnitsOnly() {
        assertEquals(QuotaWindowKind.FIVE_HOUR, ZhipuUsageClient.classifyUnit(3))
        assertEquals(QuotaWindowKind.WEEKLY, ZhipuUsageClient.classifyUnit(6))
        assertNull(ZhipuUsageClient.classifyUnit(5))
        assertNull(ZhipuUsageClient.classifyUnit(null))
    }
}
