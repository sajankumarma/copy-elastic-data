# RDP → ES

A self-contained Java tool for copying data from a Riversand Data Platform (RDP) Elasticsearch cluster to a target Elasticsearch cluster. It provides a web UI for configuration, job building, and real-time progress monitoring.

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Browser (Web UI)                                │
│                                                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  ┌──────────┐  │
│  │ landing.html │  │  config.html │  │  index.html   │  │create.html│ │
│  │  (Dashboard) │  │ (Source cfg) │  │ (Copy job UI) │  │(Bulk API) │ │
│  └──────┬───────┘  └──────┬───────┘  └──────┬────────┘  └────┬─────┘  │
└─────────┼─────────────────┼────────────────┼────────────────┼─────────┘
          │                 │                │                │
          │           HTTP REST API (port 8081)               │
          ▼                 ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Helidon SE WebServer                                │
│                                                                         │
│  GET  /                → landing.html (Dashboard)                       │
│  GET  /config          → config.html                                    │
│  GET  /index           → index.html (Copy job builder)                  │
│  GET  /create          → create.html (Bulk entity creator)              │
│  POST /save-config     → persist source-config.json                     │
│  POST /process         → start ES→ES copy job (async, returns jobId)   │
│  POST /create-entities → start platform bulk-create job (async)         │
│  GET  /progress        → live job status (polled every 700 ms)         │
│  GET  /results         → rendered HTML results table                   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          IndexApp (orchestrator)                        │
│                                                                         │
│  copyData()                                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ Phase 1 — Count                                                  │   │
│  │   For each index × type: QueryHelper.generateCountQueryByType() │   │
│  │   → REST call to source ES → record totalDocs                   │   │
│  └──────────────────────────────────┬──────────────────────────────┘   │
│                                     │                                   │
│  ┌──────────────────────────────────▼──────────────────────────────┐   │
│  │ Phase 2 — Parallel Batch Copy (8 worker threads)                 │   │
│  │                                                                  │   │
│  │  copyAll mode           │  type mode          │  ID mode         │   │
│  │  Shard by id[0] (a-z)  │  from/size pages    │  _id match       │   │
│  │  Sub-shard if >25k     │  PAGE_SIZE = 5000   │  single query    │   │
│  │  per prefix bucket     │  per page           │  per document    │   │
│  └──────────────────────────────────┬──────────────────────────────┘   │
│                                     │                                   │
│  createEntities()                   │                                   │
│  ┌──────────────────────────────────▼──────────────────────────────┐   │
│  │  POST each entity to Platform API (entitymanagementservice)     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
             ┌───────────────────────┴───────────────────────┐
             │                                               │
             ▼                                               ▼
┌────────────────────────┐                   ┌──────────────────────────┐
│  Source RDP / ES       │                   │  Target Elasticsearch    │
│                        │                   │                          │
│  {manageUrl}:          │                   │  {manageUrl}:            │
│  {managePort}/         │                   │  {managePort}/           │
│  {tenantId}/           │                   │  {tenantId}_             │
│  adminservice/api/     │                   │  {indexName}/            │
│  1.1/dynamicservice    │                   │  _doc/{id}               │
│                        │                   │                          │
│  Indexes:              │                   │  Derived index names:    │
│  · entityindex         │                   │  · tenantId_entityindex  │
│  · entitymanagemodel.. │                   │  · tenantId_entitymana.. │
│  · configurationindex  │                   │  · etc.                  │
│  · entitygovernindex   │                   │                          │
│  · genericobjectindex  │                   │                          │
│  · requestobjectindex  │                   │                          │
└────────────────────────┘                   └──────────────────────────┘
```

---

## Component Overview

| Component | Purpose |
|---|---|
| `IndexApp` | Helidon WebServer bootstrap, HTTP routing, copy job and entity-create orchestration |
| `JobManager` | Single-job executor (`newSingleThreadExecutor`); holds the current `Job` in an `AtomicReference` |
| `Job` | Mutable job state — status, counters (created / updated / errored / notFound), rolling activity log |
| `QueryHelper` | Builds all ES query payloads — count, type-batch, id-prefix, id-lookup, types-aggregation |
| `RestHelper` / `RestClient` | Java 11 `HttpClient` wrappers for source ES search calls and target ES index calls |
| `JsonHelper` | Extracts hits/total from ES responses, rewrites tenant ID in document bodies before push |
| `IndexConfig` | Deserialised shape of `rdp-to-es-push.json` — list of indexes, each with types and/or IDs |
| `RdpConfig` | Deserialised shape of `source-config.json` / `target-config.json` — URL, port, tenant, headers |
| `HtmlHelper` | Server-side HTML generator for the results table and error pages |
| Web UI | Four static pages served from the JAR classpath (`web/`) via Helidon StaticContentService |

---

## Copy Job: How It Works

```
User submits rdp-to-es-push.json
          │
          ▼
Phase 1 — Count queries (size: 0)
  · Per index × type  →  source ES count  →  totalDocs, totalQueries
          │
          ▼
Phase 2 — Parallel workers (BATCH_PARALLELISM = 8)
          │
          ├── copyAll = true
          │     Shard by first letter of 'id' field (a–z, 26 parallel tasks)
          │     If any prefix bucket > 25 000 docs → sub-shard to 2-char prefixes
          │     (recursive, up to depth 5, then fallback to from/size paging)
          │
          ├── dataObjectTypes = [type1, type2, …]
          │     For each type: paginate with from/size (PAGE_SIZE = 5 000)
          │
          └── dataObjects = [{ ids: [id1, id2] }]
                For each ID: single _id match query
          │
          ▼
Each hit → JsonHelper.prepareObjectForTarget()
            (rewrites tenantId in document body)
         → POST to target ES _doc/{id}
         → record created / updated / noop / errored
          │
          ▼
Job status → DONE or FAILED
Browser polls /progress every 700 ms → live counters + activity log
```

---

## Configuration Files

### `source-config.json`
Connection settings for the **source** RDP environment.

```json
{
  "manageUrl": "http://pimapiproxy.example.com",
  "managePort": "8000",
  "tenantId": "rdpprna26/ecolabuat",
  "headers": {
    "x-rdp-version": "8.1",
    "x-rdp-clientId": "rdpclient",
    "x-rdp-tenantId": "{{SOURCE_TENANT_ID}}",
    "x-rdp-userId": "system_user",
    "x-rdp-userRoles": "[\"admin\"]",
    "Content-Type": "application/json",
    "Authorization": "<token>"
  }
}
```

### `target-config.json`
Connection settings for the **target** Elasticsearch cluster (same shape as `source-config.json`).

### `rdp-to-es-push.json`
Defines **what** to copy. Built interactively in the UI or edited manually.

```json
{
  "indexes": [
    {
      "indexName": "entityindex",
      "copyAll": false,
      "dataObjectTypes": ["product", "sku"],
      "dataObjects": [
        { "dataObjectType": "customerSite", "ids": ["abc123"] }
      ]
    }
  ]
}
```

| Field | Description |
|---|---|
| `indexName` | Logical index name. Source index resolved as `{srcTenant}_{name}readindex`; target as `{tgtTenant}_{name}`. |
| `copyAll` | Copy every document; shards by `id` field prefix to avoid ES `max_result_window`. |
| `dataObjectTypes` | Copy all documents of these `do.dt.kw` types using paginated from/size queries. |
| `dataObjects` | Copy individual document IDs. |

---

## Web UI Pages

| URL | Page | Purpose |
|---|---|---|
| `/` | Dashboard (`landing.html`) | Job history and navigation hub |
| `/config` | Config (`config.html`) | Enter source RDP connection details; saved to `source-config.json` |
| `/index` | Copy Job (`index.html`) | Build a copy job: pick index, types, IDs; run with live progress bar |
| `/create` | Create (`create.html`) | Paste a JSON array of entities and bulk-create them via the Platform API |

---

## Build & Run

**Prerequisites:** Java 21, Maven 3.x

```bash
# Build fat JAR
mvn package -q

# Run
java -jar target/rdp-to-es-1.0-SNAPSHOT.jar

# Server starts at http://localhost:8081
```

Alternatively, launch directly from the IDE (`app.IndexApp.main`).

Config files (`source-config.json`, `target-config.json`, `rdp-to-es-push.json`) are read from the classpath on first load and **written back to disk** when saved through the UI, so changes persist across restarts.

---

## Key Design Decisions

- **Single job at a time.** `JobManager` rejects a second start if a job is already `RUNNING`.
- **8 concurrent workers.** `BATCH_PARALLELISM = 8`; controlled by a fixed thread pool inside each `runBatchedPush` call.
- **Page size 5 000.** `PAGE_SIZE = 5_000` docs per ES search request.
- **copyAll sharding.** RDP's `adminservice` does not support `sort` or deep pagination. `copyAll` shards by the first letter of the `id` field (a–z), then recursively sub-shards any bucket larger than `PREFIX_SAFE_LIMIT = 25 000` up to `PREFIX_MAX_DEPTH = 5` characters deep.
- **Tenant ID rewrite.** `JsonHelper.prepareObjectForTarget` replaces the source `tenantId` inside every document's body before pushing to the target cluster.
- **Index name convention.** Source uses `{tenantSlug}_{name}readindex`; target uses `{tenantId}_{name}`.
