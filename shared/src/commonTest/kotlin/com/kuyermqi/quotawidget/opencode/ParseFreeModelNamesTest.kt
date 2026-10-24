package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.settings.FreeModelEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParseFreeModelNamesTest {
    @Test
    fun parsesFreeRowsWithModelIds() {
        val html = """
            <html><body>
            <table>
              <tr><th>Model</th><th>Input</th><th>Output</th><th>Cached Read</th><th>Monthly limit</th></tr>
              <tr><td><bdi>GLM-5.3</bdi></td><td>$1.40</td><td>$4.40</td><td>$0.26</td><td>$15</td></tr>
              <tr><td><bdi>LongCat 2.5 Preview Free</bdi></td><td>Free</td><td>Free</td><td>Free</td><td>Unlimited <small>limited time</small></td></tr>
              <tr><td><bdi>Beam Free</bdi></td><td>Free</td><td>Free</td><td>Free</td><td>Unlimited <small>limited time</small></td></tr>
              <tr><td><bdi>Cat Jam</bdi></td><td>$0.10</td><td>$0.20</td><td>$0.002</td><td>$60</td></tr>
            </table>
            <table>
              <tr><th>Model</th><th>Model ID</th><th>Endpoint</th><th>AI SDK Package</th></tr>
              <tr><td>GLM-5.3</td><td>glm-5.3</td><td>https://example/v1</td><td>@ai-sdk/openai-compatible</td></tr>
              <tr><td>LongCat 2.5 Preview Free</td><td>longcat-2.5-preview-free</td><td>https://example/v1</td><td>@ai-sdk/openai-compatible</td></tr>
              <tr><td>Beam Free</td><td>beam-free</td><td>https://example/v1</td><td>@ai-sdk/openai-compatible</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        assertEquals(
            listOf(
                FreeModelEntry("Beam Free", "beam-free"),
                FreeModelEntry("LongCat 2.5 Preview Free", "longcat-2.5-preview-free"),
            ),
            parseFreeModels(html),
        )
    }

    @Test
    fun matchesIdWhenPricingNameCarriesTierSuffix() {
        val html = """
            <table>
              <tr><th>Model</th><th>Input</th><th>Output</th></tr>
              <tr><td>Qwen3.7 Plus (&lt;= 256K tokens) Free</td><td>Free</td><td>Free</td></tr>
            </table>
            <table>
              <tr><th>Model</th><th>Model ID</th><th>Endpoint</th></tr>
              <tr><td>Qwen3.7 Plus</td><td>qwen3.7-plus</td><td>https://example/v1</td></tr>
            </table>
        """.trimIndent()
        val entry = parseFreeModels(html).single()
        assertEquals("qwen3.7-plus", entry.modelId)
    }

    @Test
    fun throwsWhenAFreeModelHasNoCallableId() {
        val html = """
            <table>
              <tr><th>Model</th><th>Input</th><th>Output</th></tr>
              <tr><td>Beam Free</td><td>Free</td><td>Free</td></tr>
            </table>
            <table>
              <tr><th>Model</th><th>Model ID</th><th>Endpoint</th></tr>
              <tr><td>Other Model</td><td>other-model</td><td>https://example/v1</td></tr>
            </table>
        """.trimIndent()
        val error = assertFailsWith<IllegalStateException> { parseFreeModels(html) }
        assertEquals("免费模型缺少可调用 ID：Beam Free", error.message)
    }

    @Test
    fun throwsWhenIdTableIsMissing() {
        val html = """
            <table>
              <tr><th>Model</th><th>Input</th><th>Output</th></tr>
              <tr><td>Beam Free</td><td>Free</td><td>Free</td></tr>
            </table>
        """.trimIndent()
        val error = assertFailsWith<IllegalStateException> { parseFreeModels(html) }
        assertEquals("OpenCode Go 定价页未找到模型 ID 表", error.message)
    }

    @Test
    fun copyTextPrefersModelId() {
        val entry = FreeModelEntry("MiniMax M3.1 Flash Free", "minimax-m3.1-flash-free")
        assertEquals("minimax-m3.1-flash-free", entry.copyText)
    }

    @Test
    fun returnsEmptyWhenPricingTableHasNoFreeRows() {
        val html = """
            <table>
              <tr><th>Model</th><th>Input</th><th>Output</th></tr>
              <tr><td>GLM-5.3</td><td>$1.40</td><td>$4.40</td></tr>
            </table>
            <table>
              <tr><th>Model</th><th>Model ID</th><th>Endpoint</th></tr>
              <tr><td>GLM-5.3</td><td>glm-5.3</td><td>https://example/v1</td></tr>
            </table>
        """.trimIndent()
        assertEquals(emptyList(), parseFreeModels(html))
    }

    @Test
    fun throwsWhenNoPricingTablePresent() {
        assertFailsWith<IllegalStateException> {
            parseFreeModels("<html><body><p>maintenance</p></body></html>")
        }
    }
}
