package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.settings.FreeModelEntry
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
 * The page carries two tables: the pricing table (a model is free when its
 * Input and Output cells read "Free") and the model-id table (the id used
 * when calling the inference endpoint). They are joined on the display name.
 */
class OpenCodeFreeModelsClient(
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun fetchFreeModels(): List<FreeModelEntry> {
        val body = httpClient.get(GO_PRICING_URL) {
            header(HttpHeaders.Accept, "text/html")
        }.bodyAsText()
        return parseFreeModels(body)
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

private data class PageTable(
    val header: List<String>,
    val rows: List<List<String>>,
)

private fun parseTables(html: String): List<PageTable> =
    TABLE_REGEX.findAll(html).map { tableMatch ->
        val rows = ROW_REGEX.findAll(tableMatch.value).map { it.groupValues[1] }.toList()
        val parsed = rows.map { row ->
            CELL_REGEX.findAll(row).map { cellText(it.groupValues[1]) }.toList()
        }
        PageTable(
            header = parsed.firstOrNull().orEmpty(),
            rows = parsed.drop(1),
        )
    }.toList()

/**
 * Loose join key: lowercase, parenthetical tier suffixes stripped, whitespace
 * collapsed. The pricing table labels token-tier/time-of-day variants
 * ("Qwen3.7 Plus (≤ 256K tokens)", "(Off-Peak)") that the id table omits.
 */
private fun normalizeModelName(name: String): String =
    name.substringBefore('(').trim().lowercase().replace(WHITESPACE_REGEX, " ")

internal fun parseFreeModels(html: String): List<FreeModelEntry> {
    val tables = parseTables(html)

    val pricingTable = tables.firstOrNull { table ->
        table.header.size >= 3 &&
            table.header[0].equals("Model", ignoreCase = true) &&
            table.header[1].equals("Input", ignoreCase = true) &&
            table.header[2].equals("Output", ignoreCase = true)
    } ?: error("OpenCode Go 定价页未找到模型价格表")

    val modelIdTable = tables.firstOrNull { table ->
        table.header.size >= 2 &&
            table.header[0].equals("Model", ignoreCase = true) &&
            table.header[1].equals("Model ID", ignoreCase = true)
    } ?: error("OpenCode Go 定价页未找到模型 ID 表")

    val modelIdsByName = modelIdTable.rows
        .mapNotNull { row ->
            if (row.size < 2) return@mapNotNull null
            val name = row[0]
            val id = row[1]
            if (name.isEmpty() || id.isEmpty()) null else name to id
        }
        .toMap()

    return pricingTable.rows.mapNotNull { row ->
        if (row.size < 3) return@mapNotNull null
        val name = row[0]
        if (name.isEmpty() || name.equals("Model", ignoreCase = true)) return@mapNotNull null
        val isFree = row[1].equals("Free", ignoreCase = true) &&
            row[2].equals("Free", ignoreCase = true)
        if (!isFree) return@mapNotNull null
        // Copying a display name that is not callable would be a silent failure,
        // so refuse to persist the list when any free model has no id.
        val modelId = modelIdsByName[name]
            ?: modelIdsByName.entries.firstOrNull {
                normalizeModelName(it.key) == normalizeModelName(name)
            }?.value
            ?: error("免费模型缺少可调用 ID：$name")
        FreeModelEntry(name = name, modelId = modelId)
    }.sortedBy { it.name }
}
