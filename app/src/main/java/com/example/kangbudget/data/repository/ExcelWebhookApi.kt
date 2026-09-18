package com.example.kangbudget.data.repository

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "ExcelWebhookApi"

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
 * The outcome of one webhook call.
 *
 * The distinction is what drives retry policy, and a plain `Boolean` could not express it:
 * Apps Script answers HTTP 200 for *every* logical failure (bad secret, missing tab, a caught
 * server-side exception), so the status code alone never separates "the sheet refused this row"
 * from "the request never got there". Only [TransportFailure] is worth retrying — a
 * [Rejected] payload will be rejected identically however many times it is resent.
 */
sealed interface WebhookResult {
    /** The script ran and reported `ok: true`. The row is in the sheet. */
    data object Delivered : WebhookResult

    /** The script answered and refused the op (`unauthorized`, `missing_tab`, `exception`, …). */
    data class Rejected(val error: String) : WebhookResult

    /** Nothing usable came back: no network, a timeout, an HTTP error, or a non-JSON body. */
    data class TransportFailure(val detail: String) : WebhookResult
}

/**
 * HTTP transport to the Google Apps Script Web App that backs `excel`-budgetType categories.
 *
 * A single configured endpoint (URL + shared secret) serves every category; the identifier
 * now selects the *tab* within the sheet, not the endpoint. Configuration comes from Firestore
 * `config/webhooks` at app start via [configure]. Every call is best-effort and never throws
 * into the caller — a failed send is reported as a [WebhookResult] so the repository can leave
 * the op on its outbox for retry.
 *
 * Transport is [HttpURLConnection] + `org.json`, both in the Android SDK, so this adds no
 * Gradle dependency and needs only the already-declared INTERNET permission.
 */
object ExcelWebhookApi {

    @Volatile private var url: String = ""
    @Volatile private var secret: String = ""
    @Volatile private var enabled: Boolean = false

    /**
     * Completes the first time [configure] runs, so callers can wait for the config load
     * instead of racing it. A transaction added in the first seconds after launch is dispatched
     * while `config/webhooks` is still being fetched (anonymous sign-in has to land first);
     * without this gate that op was dropped on the floor and only retried on the *next* cold
     * start, which is indistinguishable from the sync being broken.
     */
    private val configured = CompletableDeferred<Unit>()

    private const val TIMEOUT_MS = 10_000
    private const val MAX_REDIRECTS = 5

    /** Wire in the endpoint loaded from Firestore `config/webhooks`. */
    fun configure(url: String, secret: String, enabled: Boolean) {
        this.url = url.trim()
        this.secret = secret
        this.enabled = enabled
        Log.i(
            TAG,
            "Configured: enabled=$enabled, endpoint=${redact(this.url)}, " +
                "secretLength=${secret.length}"
        )
        if (!enabled) Log.w(TAG, "config/webhooks has enabled=false — nothing will reach the sheet")
        if (this.url.isBlank()) Log.w(TAG, "config/webhooks has a blank url — nothing will reach the sheet")
        configured.complete(Unit)
    }

    /** True only when the integration is switched on and an endpoint URL is present. */
    fun isEnabled(): Boolean = enabled && url.isNotBlank()

    /**
     * Suspends until [configure] has run at least once, then reports [isEnabled]. Returns false
     * if the config never arrives within [timeoutMs] — the caller should leave its op queued.
     */
    suspend fun awaitEnabled(timeoutMs: Long): Boolean {
        if (isEnabled()) return true
        withTimeoutOrNull(timeoutMs) { configured.await() }
        return isEnabled()
    }

    /** Add or edit a row. Idempotent server-side on [WebhookPayload.txId]. */
    suspend fun upsert(identifier: String, entry: WebhookPayload): WebhookResult {
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
    suspend fun delete(identifier: String, txId: String): WebhookResult {
        val body = baseBody("delete", identifier).put("txId", txId)
        return post(body)
    }

    /**
     * `GET ?op=ping` — one cheap round trip that proves the whole chain end to end: the /exec
     * URL resolves, the deployment is reachable without a Google login, and the shared secret
     * matches. Run once after [configure] so a broken integration names itself in logcat rather
     * than showing up later as rows that quietly never appear.
     */
    suspend fun ping(): WebhookResult = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext WebhookResult.TransportFailure("not configured")
        val query = "secret=${URLEncoder.encode(secret, "UTF-8")}&op=ping"
        val separator = if (url.contains('?')) "&" else "?"
        execute("$url$separator$query", payload = null)
    }

    private fun baseBody(op: String, identifier: String): JSONObject =
        JSONObject()
            .put("secret", secret)
            .put("op", op)
            .put("tab", identifier)

    private suspend fun post(body: JSONObject): WebhookResult = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext WebhookResult.TransportFailure("not configured")
        execute(url, body.toString())
    }

    /**
     * Runs one request, manually following Apps Script's 302 to script.googleusercontent.com.
     *
     * HttpURLConnection's automatic redirect handling does NOT reliably follow a redirect after
     * a POST body — it hands back the raw 302, which we'd misread as a failure and leave the op
     * on the outbox forever. So we follow it ourselves: the first hop carries the body (and runs
     * doPost server-side), and each redirect is fetched with GET until we reach the JSON result.
     *
     * [payload] null means the request is a GET from the start (used by [ping]).
     */
    private fun execute(initialUrl: String, payload: String?): WebhookResult {
        var targetUrl = initialUrl
        var method = if (payload != null) "POST" else "GET"
        var hops = 0
        while (true) {
            val connection = try {
                (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    instanceFollowRedirects = false
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }
            } catch (e: Exception) {
                return transportFailure("could not open ${redact(targetUrl)}", e)
            }
            try {
                if (method == "POST" && payload != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                }
                val code = try {
                    connection.responseCode
                } catch (e: IOException) {
                    return transportFailure("no response from ${redact(targetUrl)}", e)
                }

                if (code in 300..399) {
                    if (hops >= MAX_REDIRECTS) {
                        return transportFailure("more than $MAX_REDIRECTS redirects", null)
                    }
                    val location = connection.getHeaderField("Location")
                        ?: return transportFailure("HTTP $code with no Location header", null)
                    targetUrl = location
                    method = "GET" // the write already happened on the initial POST; just fetch the result
                    hops++
                    continue
                }

                val responseBody = runCatching {
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                }.getOrDefault("")

                if (code !in 200..299) {
                    return transportFailure("HTTP $code — ${responseBody.take(300)}", null)
                }

                // Apps Script's ContentService always answers HTTP 200, even for a logical
                // failure — the status code alone can never tell success from failure, only the
                // body can. A body that is not JSON at all is almost always Google's sign-in
                // page, i.e. the deployment is not "Anyone with the link".
                val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    ?: return transportFailure(
                        "response was not JSON (is the deployment set to \"Anyone with the " +
                            "link\"?) — ${responseBody.take(300)}",
                        null
                    )

                return if (json.optBoolean("ok", false)) {
                    Log.i(TAG, "Webhook delivered: $responseBody")
                    WebhookResult.Delivered
                } else {
                    val error = json.optString("error", "unknown")
                    Log.w(TAG, "Webhook rejected by Apps Script: $responseBody")
                    WebhookResult.Rejected(error)
                }
            } catch (e: Exception) {
                return transportFailure("request to ${redact(targetUrl)} failed", e)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun transportFailure(detail: String, cause: Exception?): WebhookResult.TransportFailure {
        val message = if (cause != null) "$detail: $cause" else detail
        Log.w(TAG, "Webhook transport failure: $message")
        return WebhookResult.TransportFailure(message)
    }

    /** Endpoint shape for logs, with the deployment id dropped so logcat never carries it. */
    private fun redact(raw: String): String = runCatching {
        val parsed = URL(raw)
        val path = parsed.path.replace(Regex("/s/[^/]+"), "/s/…")
        "${parsed.protocol}://${parsed.host}$path"
    }.getOrDefault(if (raw.isBlank()) "<blank>" else "<unparseable>")
}
