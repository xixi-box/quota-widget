package com.kuyermqi.quotawidget.opencode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParseFreeModelNamesTest {
    @Test
    fun parsesFreeRowsFromGoPricingTables() {
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
              <tr><th>Plan</th><th>Price</th></tr>
              <tr><td>Go</td><td>$20 / mo</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        assertEquals(
            listOf("Beam Free", "LongCat 2.5 Preview Free"),
            parseFreeModelNames(html),
        )
    }

    @Test
    fun deduplicatesModelsListedInMultiplePricingTables() {
        val pricingTable = """
            <table>
              <tr><th>Model</th><th>Input</th><th>Output</th></tr>
              <tr><td>Beam Free</td><td>Free</td><td>Free</td></tr>
            </table>
        """.trimIndent()
        assertEquals(listOf("Beam Free"), parseFreeModelNames(pricingTable + pricingTable))
    }

    @Test
    fun returnsEmptyWhenPricingTableHasNoFreeRows() {
        val html = """
            <table>
              <tr><th>Model</th><th>Input</th><th>Output</th></tr>
              <tr><td>GLM-5.3</td><td>$1.40</td><td>$4.40</td></tr>
            </table>
        """.trimIndent()
        assertEquals(emptyList(), parseFreeModelNames(html))
    }

    @Test
    fun throwsWhenNoPricingTablePresent() {
        assertFailsWith<IllegalStateException> {
            parseFreeModelNames("<html><body><p>maintenance</p></body></html>")
        }
    }
}
