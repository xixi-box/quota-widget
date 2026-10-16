package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/**
 * Free models under the OpenCode Go plan, read from the official pricing page
 * at opencode.ai/v2/docs/console/go/. models.dev is not a reliable substitute:
 * it lags the official table (lists retired promos as live, misses new ones).
 *
 * A model counts as free when both its Input and Output cells read "Free";
 * such rows carry an unlimited monthly quota for a limited time.
 */
class OpenCodeFreeModelsClient(
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun fetchFreeModels(): List<String> {
        val body = httpClient.get(GO_PRICING_URL) {
            header(HttpHeaders.Accept, "text/html")
        }.bodyAsText()
        return parseFreeModelNames(body)
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val GO_PRICING_URL = "https://opencode.ai/v2/docs/console/go/"
    }
}

private val TABLE_REGEX = Regex("<table[\\s\\S]*?</table>")
private val ROW_REGEX = Regex("<tr[^>]*>([\\s\\S]*?)</tr>")
private val CELL_REGEX = Regex("<t[hd][^>]*>([\\s\\S]*?)</t[hd]>")
private val TAG_REGEX = Regex("<[^>]+>")
private val WHITESPACE_REGEX = Regex("\\s+")

private val HTML_ENTITIES = mapOf(
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#x27;" to "'",
    "&#39;" to "'",
    "&nbsp;" to " ",
)

private fun cellText(raw: String): String {
    var text = TAG_REGEX.replace(raw, " ")
    HTML_ENTITIES.forEach { (entity, value) -> text = text.replace(entity, value) }
    return WHITESPACE_REGEX.replace(text, " ").trim()
}

internal fun parseFreeModelNames(html: String): List<String> {
    fun cellsOf(fragment: String): List<String> =
        CELL_REGEX.findAll(fragment).map { cellText(it.groupValues[1]) }.toList()

    val names = linkedSetOf<String>()
    var pricingTables = 0
    for (table in TABLE_REGEX.findAll(html)) {
        val rows = ROW_REGEX.findAll(table.value).map { it.groupValues[1] }.toList()
        if (rows.isEmpty()) continue
        val header = cellsOf(rows.first())
        val isPricingTable = header.size >= 3 &&
            header[0].equals("Model", ignoreCase = true) &&
            header[1].equals("Input", ignoreCase = true) &&
            header[2].equals("Output", ignoreCase = true)
        if (!isPricingTable) continue
        pricingTables++
        for (row in rows.drop(1)) {
            val cells = cellsOf(row)
            if (cells.size < 3) continue
            val name = cells[0]
            if (name.isEmpty() || name.equals("Model", ignoreCase = true)) continue
            if (cells[1].equals("Free", ignoreCase = true) &&
                cells[2].equals("Free", ignoreCase = true)
            ) {
                names += name
            }
        }
    }
    // Zero free rows is a legitimate state (promos all ended), but a missing
    // table means the page layout changed — fail loudly so the last good cache
    // is retained instead of being overwritten with an empty list.
    check(pricingTables > 0) { "OpenCode Go 定价页未找到模型价格表" }
    return names.sorted()
}
