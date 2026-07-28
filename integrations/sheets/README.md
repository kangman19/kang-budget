# KangBudget → Google Sheets sync

Transactions in `excel`-type categories are pushed to a Google Sheet automatically on
add / edit / delete. A single [Apps Script Web App](Code.gs) is the entire server: the
Android app POSTs to it, and the scheduled `sheet-organizer` Claude agent reads and writes
through the same URL. No OAuth, no GCP project, no service-account key in the APK — a shared
secret in every payload is the only auth.

```
 Android app                    Apps Script Web App                 Claude agent
 ───────────                    ───────────────────                 ────────────
 add/update ──POST upsert──┐                                   GET  ?op=export ──┐
 delete    ──POST delete───┼──> doPost / doGet ──> Sheet <────POST op=enrich ────┤
                           │        (LockService,            POST op=summary ────┘
   config/webhooks ────────┘         idempotent by txId,
   { url, secret, enabled }          self-sorting/totals)
```

**Idempotency is the whole design.** Every row carries the Firestore transaction id in
column A. `upsert` overwrites that row or appends if absent; `delete` removes it. Retries
and offline-outbox replays are therefore no-ops, not double-counted expenses.

---

## Deploy (do this by hand, once)

1. Create/open the Google Sheet → **Extensions → Apps Script**.
2. Paste the contents of [`Code.gs`](Code.gs). **This repo copy is the source of truth** —
   edit here, then re-paste.
3. Set `SECRET` at the top of the script to a long random string. Generate one with
   `openssl rand -hex 24` (or any password manager). It must match the secret in Firestore
   `config/webhooks` and in the agent's `.env`.
4. **Deploy → New deployment → Web app.** Execute as **Me**; Who has access
   **Anyone with the link**. Copy the `/exec` URL — it looks like
   `https://script.google.com/macros/s/AKfy…/exec`.
5. *(Optional)* In the editor, run `installOnChangeTrigger` once and authorize it. This only
   adds auto sort/totals when a **human** edits the sheet by hand; programmatic writes from
   the app and agent already self-organize.

> **Updating the live server:** any later edit needs
> **Deploy → Manage deployments → Edit (pencil) → New version → Deploy**. Just saving the
> script leaves the old code serving the `/exec` URL. The URL itself does not change across
> versions.

### ⚠️ Never commit the URL or the secret

Anyone with both can write to your sheet. Keep them out of git:

- The **Android** side reads them from Firestore `config/webhooks` (below), not from code.
- The **agent** side reads them from `integrations/sheets/.env` (gitignored). Copy the
  template:
  ```
  cp integrations/sheets/.env.example integrations/sheets/.env
  # then fill in SHEETS_WEBHOOK_URL and SHEETS_WEBHOOK_SECRET
  ```

---

## Firestore config the app reads

Seed one document in the Firebase console (Firestore) at **`config/webhooks`**:

| Field     | Type    | Example                                             |
|-----------|---------|-----------------------------------------------------|
| `url`     | string  | `https://script.google.com/macros/s/AKfy…/exec`     |
| `secret`  | string  | the same string as `SECRET` in `Code.gs`            |
| `enabled` | boolean | `true`                                              |

The app loads this once at startup (works offline via Firestore's cache) and passes it to
`ExcelWebhookApi.configure(...)`. Set `enabled: false` to kill the integration without a
code change.

---

## Column layout (per data tab)

One tab per category, named after the category's `webhookIdentifier`
(`"Car Fuel"` → `car-fuel`).

| Col | Field          | Owner  | Notes                                        |
|-----|----------------|--------|----------------------------------------------|
| A   | `txId`         | app    | Firestore transaction id — the idempotency key |
| B   | `date`         | app    | `YYYY-MM-DD`                                  |
| C   | `time`         | app    | `HH:mm`                                       |
| D   | `monthId`      | app    |                                              |
| E   | `categoryName` | app    |                                              |
| F   | `categoryType` | app    | `income` / `expense`                         |
| G   | `description`  | app    | free text                                    |
| H   | `amount`       | app    | number                                       |
| I   | `tag`          | agent  | preserved across app upserts                 |
| J   | `merchant`     | agent  | canonical merchant name                      |
| K   | `flag`         | agent  | e.g. `duplicate`, `anomaly`                  |

**Columns A–H are overwritten on every upsert; I–K are preserved** — the script re-reads and
keeps them so an app edit never wipes the agent's work. A `TOTAL` row is maintained at the
bottom of each tab (sum of column H), and rows are kept sorted by date descending. The
`Summary` tab is agent-owned and excluded from data operations.

---

## Payload reference

All requests carry `secret`. POST bodies are JSON (`Content-Type: application/json`).
Every response is JSON with an `ok` boolean.

### `POST` — `op: "upsert"`  (app: add / update)
```json
{
  "secret": "…", "op": "upsert",
  "txId": "abc123", "tab": "car-fuel",
  "date": "2026-07-25", "time": "14:30", "monthId": "2026-07",
  "categoryName": "Car Fuel", "categoryType": "expense",
  "description": "TESCO PETROL 3241", "amount": 62.40
}
```
→ `{ "ok": true, "action": "inserted" | "updated", "tab": "car-fuel" }`

### `POST` — `op: "delete"`  (app: delete)
```json
{ "secret": "…", "op": "delete", "txId": "abc123", "tab": "car-fuel" }
```
→ `{ "ok": true, "action": "deleted" | "noop_not_found" | "noop_no_tab" }`

### `POST` — `op: "enrich"`  (agent: write I–K only)
```json
{
  "secret": "…", "op": "enrich",
  "rows": [
    { "txId": "abc123", "tab": "car-fuel", "tag": "fuel", "merchant": "Tesco", "flag": "" }
  ]
}
```
Omit a field to leave that cell untouched. `tab` is optional (scopes the search).
→ `{ "ok": true, "updated": 1, "missing": [] }`

### `POST` — `op: "summary"`  (agent: replace Summary tab)
```json
{ "secret": "…", "op": "summary", "markdown": "# July\n- Fuel: £180 (+12%)" }
```
→ `{ "ok": true, "lines": 2 }`

### `GET` — `op=export`  (agent: read rows)
```
{URL}?secret=…&op=export[&tab=car-fuel][&since=2026-07-01]
```
→ `{ "ok": true, "count": 12, "rows": [ { "tab": "car-fuel", "txId": "…", … } ] }`

### `GET` — `op=ping`  (health check)
```
{URL}?secret=…&op=ping
```
→ `{ "ok": true, "pong": true }`

Errors: `{ "ok": false, "error": "unauthorized" | "bad_json" | "unknown_op" | "busy" |
"missing_tab" | "missing_txId" | "exception" }`.

---

## Smoke test (before touching Kotlin)

```bash
URL='https://script.google.com/macros/s/AKfy…/exec'
SECRET='…'

# upsert → row appears
curl -L -X POST "$URL" -H 'Content-Type: application/json' \
  -d "{\"secret\":\"$SECRET\",\"op\":\"upsert\",\"txId\":\"test-1\",\"tab\":\"test\",\"amount\":12.5,\"description\":\"probe\",\"date\":\"2026-07-25\"}"

# identical call → still exactly one row (idempotency)
curl -L -X POST "$URL" -H 'Content-Type: application/json' \
  -d "{\"secret\":\"$SECRET\",\"op\":\"upsert\",\"txId\":\"test-1\",\"tab\":\"test\",\"amount\":12.5,\"description\":\"probe\",\"date\":\"2026-07-25\"}"

# export → JSON
curl -L "$URL?secret=$SECRET&op=export"

# delete → row gone
curl -L -X POST "$URL" -H 'Content-Type: application/json' \
  -d "{\"secret\":\"$SECRET\",\"op\":\"delete\",\"txId\":\"test-1\",\"tab\":\"test\"}"

# wrong secret → rejected, nothing written
curl -L -X POST "$URL" -H 'Content-Type: application/json' \
  -d "{\"secret\":\"nope\",\"op\":\"upsert\",\"txId\":\"x\",\"tab\":\"test\",\"amount\":1}"
```

`-L` matters: Apps Script 302s to `script.googleusercontent.com`, and the POST body must
follow the redirect.

### PowerShell (Windows)

The bash snippet above does **not** work when pasted into PowerShell — the `\"` escapes and
`$VAR`/`&` handling differ, so the body is dropped (`411 Length Required`) and the URL is
mangled. Use `Invoke-RestMethod`, which builds the JSON, sets `Content-Length`, and follows
the 302 automatically:

```powershell
$URL = 'https://script.google.com/macros/s/AKfy.../exec'
$SECRET = '…'

# upsert -> action=inserted; repeat -> action=updated (idempotency, still one row)
$body = @{ secret=$SECRET; op='upsert'; txId='test-1'; tab='test'; amount=12.5; description='probe'; date='2026-07-25' } | ConvertTo-Json
Invoke-RestMethod -Uri $URL -Method Post -ContentType 'application/json' -Body $body

# export -> JSON (date should read "2026-07-25", NOT a shifted timestamp)
Invoke-RestMethod -Uri "$URL`?secret=$SECRET&op=export" | ConvertTo-Json -Depth 5

# delete -> action=deleted
$del = @{ secret=$SECRET; op='delete'; txId='test-1'; tab='test' } | ConvertTo-Json
Invoke-RestMethod -Uri $URL -Method Post -ContentType 'application/json' -Body $del

# wrong secret -> error=unauthorized
$bad = @{ secret='nope'; op='upsert'; txId='x'; tab='test'; amount=1 } | ConvertTo-Json
Invoke-RestMethod -Uri $URL -Method Post -ContentType 'application/json' -Body $bad
```

> Note: text columns are stored as plain text (`@` format) so date/monthId strings are never
> coerced to Date cells. If you see a date come back as `2026-07-24T21:00:00.000Z`, the tab
> was created before this format fix — delete the tab and let the script recreate it.
