package com.example.kangbudget.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The full A–H app-owned row for one transaction (see integrations/sheets/Code.gs column
 * layout). The Apps Script decides append-vs-overwrite from [txId], so the same payload
 * serves both "add" and "edit" — both are `upsert`.
 */
data class WebhookPayload(
    val txId: String,
    val date: String,
    val time: String,
    val monthId: String,
    val categoryName: String,
    val categoryType: String,
    val description: String,
    val amount: Double
)

/**
 * HTTP transport to the Google Apps Script Web App that backs `excel`-budgetType categories.
 *
 * A single configured endpoint (URL + shared secret) serves every category; the identifier
 * now selects the *tab* within the sheet, not the endpoint. Configuration comes from Firestore
 * `config/webhooks` at app start via [configure]. Every call is best-effort and never throws
 * into the caller — a failed send returns `false` so the repository can leave the op on its
 * outbox for retry.
 *
 * Transport is [HttpURLConnection] + `org.json`, both in the Android SDK, so this adds no
 * Gradle dependency and needs only the already-declared INTERNET permission.
 */
object ExcelWebhookApi {

    @Volatile private var url: String = ""
    @Volatile private var secret: String = ""
    @Volatile private var enabled: Boolean = false

    private const val TIMEOUT_MS = 10_000
    private const val MAX_REDIRECTS = 5

    /** Wire in the endpoint loaded from Firestore `config/webhooks`. */
    fun configure(url: String, secret: String, enabled: Boolean) {
        this.url = url.trim()
        this.secret = secret
        this.enabled = enabled
    }

    /** True only when the integration is switched on and an endpoint URL is present. */
    fun isEnabled(): Boolean = enabled && url.isNotBlank()

    /** Add or edit a row. Idempotent server-side on [WebhookPayload.txId]. */
    suspend fun upsert(identifier: String, entry: WebhookPayload): Boolean {
        val body = baseBody("upsert", identifier)
            .put("txId", entry.txId)
            .put("date", entry.date)
            .put("time", entry.time)
            .put("monthId", entry.monthId)
            .put("categoryName", entry.categoryName)
            .put("categoryType", entry.categoryType)
            .put("description", entry.description)
            .put("amount", entry.amount)
        return post(body)
    }

    /** Remove the row carrying [txId] from the [identifier] tab. */
    suspend fun delete(identifier: String, txId: String): Boolean {
        val body = baseBody("delete", identifier).put("txId", txId)
        return post(body)
    }

    private fun baseBody(op: String, identifier: String): JSONObject =
        JSONObject()
            .put("secret", secret)
            .put("op", op)
            .put("tab", identifier)

    private suspend fun post(body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext false
        runCatching { postFollowingRedirects(url, body.toString()) }.getOrDefault(false)
    }

    /**
     * POSTs the payload, then manually follows Apps Script's 302 to script.googleusercontent.com.
     *
     * HttpURLConnection's automatic redirect handling does NOT reliably follow a redirect after
     * a POST body — it hands back the raw 302, which we'd misread as a failure and leave the op
     * on the outbox forever. So we follow it ourselves: the first hop is the POST (which runs
     * doPost server-side), and each redirect is fetched with GET until we reach the JSON result.
     */
    private fun postFollowingRedirects(initialUrl: String, payload: String): Boolean {
        var targetUrl = initialUrl
        var method = "POST"
        var hops = 0
        while (true) {
            val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                if (method == "POST") {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                if (code in 300..399 && hops < MAX_REDIRECTS) {
                    val location = connection.getHeaderField("Location") ?: return false
                    targetUrl = location
                    method = "GET" // the write already happened on the initial POST; just fetch the result
                    hops++
                    continue
                }
                // Drain so the socket can be released cleanly.
                runCatching { connection.inputStream.use { it.readBytes() } }
                return code in 200..299
            } finally {
                connection.disconnect()
            }
        }
    }
}
