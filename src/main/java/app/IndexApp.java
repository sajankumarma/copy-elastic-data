package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.RdpConfigLoader;
import constants.Constants;
import html.HtmlHelper;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.staticcontent.StaticContentService;
import json.JsonHelper;
import objects.IndexConfig;
import objects.Job;
import objects.ProcessResult;
import objects.RdpConfig;
import query.QueryHelper;
import rest.RestHelper;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class IndexApp {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PAGE_SIZE = 5_000;        // docs per from/size fetch page
    private static final int BATCH_PARALLELISM = 8;   // concurrent type workers

    public static void main(String[] args) {
        Config config = Config.create();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(IndexApp::routing)
                .port(8081)
                .build()
                .start();

        System.out.println("Server started at: http://localhost:" + server.port());
    }

    private static void routing(HttpRouting.Builder routing) {
        routing.addFilter((chain, req, res) -> {
            res.header(HeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            res.header(HeaderNames.VARY, "If-None-Match");
            chain.proceed();
        });

        routing.post("/save-config", IndexApp::handleConfigSave);
        routing.post("/process", IndexApp::handleProcess);
        routing.post("/create-entities", IndexApp::handleCreateEntities);
        routing.get("/progress", IndexApp::handleProgress);
        routing.get("/results", IndexApp::handleResults);
        routing.get("/show-config", IndexApp::handleShowConfig);

        routing.register("/",
                StaticContentService.builder("web")
                        .welcomeFileName("landing.html")
                        .build());

        routing.register("/config",
                StaticContentService.builder("web")
                        .welcomeFileName("config.html")
                        .build());

        routing.register("/index",
                StaticContentService.builder("web")
                        .welcomeFileName("index.html")
                        .build());

        routing.register("/create",
                StaticContentService.builder("web")
                        .welcomeFileName("create.html")
                        .build());
    }

    private static void handleConfigSave(ServerRequest req, ServerResponse res) {
        try {
            String json = req.content().as(String.class);
            JsonHelper.saveAsPrettyJson(json, Constants.RDP_SOURCE_CONFIG_FILE_PATH);

            res.header(HeaderNames.LOCATION, "/index");
            res.status(302).send();
        } catch (Exception e) {
            sendErrorPage(res, "Failed to save config", e);
        }
    }

    private static void handleProcess(ServerRequest req, ServerResponse res) {
        try {
            String json = req.content().as(String.class);
            JsonHelper.saveAsPrettyJson(json, Constants.RDP_CONFIG_FILE_PATH);

            Job job = JobManager.start(IndexApp::copyData);

            ObjectNode body = JSON.createObjectNode();
            body.put("jobId", job.getId());
            body.put("status", job.getStatus().name());
            res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
            res.status(202).send(JSON.writeValueAsString(body));
        } catch (Exception e) {
            try {
                ObjectNode body = JSON.createObjectNode();
                body.put("error", rootCauseMessage(e));
                res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
                res.status(500).send(JSON.writeValueAsString(body));
            } catch (Exception inner) {
                sendErrorPage(res, "Failed to start job", e);
            }
        }
    }

    private static void handleCreateEntities(ServerRequest req, ServerResponse res) {
        try {
            String json = req.content().as(String.class);
            JsonNode body = JsonHelper.asJson(json);

            String service = JsonHelper.textAt(body, "service", "entitymanagementservice");
            String entityKey = JsonHelper.textAt(body, "entityKey", "entity");
            JsonNode entities = body.get("entities");

            if (entities == null || !entities.isArray() || entities.isEmpty()) {
                ObjectNode err = JSON.createObjectNode();
                err.put("error", "No entities found in request payload");
                res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
                res.status(400).send(JSON.writeValueAsString(err));
                return;
            }

            Job job = JobManager.start(j -> createEntities(j, service, entityKey, entities));

            ObjectNode result = JSON.createObjectNode();
            result.put("jobId", job.getId());
            result.put("status", job.getStatus().name());
            res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
            res.status(202).send(JSON.writeValueAsString(result));
        } catch (Exception e) {
            try {
                ObjectNode err = JSON.createObjectNode();
                err.put("error", rootCauseMessage(e));
                res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
                res.status(500).send(JSON.writeValueAsString(err));
            } catch (Exception inner) {
                sendErrorPage(res, "Failed to start create job", e);
            }
        }
    }

    private static void handleProgress(ServerRequest req, ServerResponse res) {
        try {
            Job job = JobManager.current();
            ObjectNode body = JSON.createObjectNode();
            if (job == null) {
                body.put("status", "NONE");
                res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
                res.send(JSON.writeValueAsString(body));
                return;
            }
            body.put("id", job.getId());
            body.put("status", job.getStatus().name());
            body.put("currentLabel", job.getCurrentLabel());
            body.put("totalQueries", job.getTotalQueries());
            body.put("completedQueries", job.getCompletedQueries());
            body.put("totalDocs", job.getTotalDocs());
            body.put("docsProcessed", job.getDocsProcessed());
            body.put("docsCreated", job.getDocsCreated());
            body.put("docsUpdated", job.getDocsUpdated());
            body.put("docsNoop", job.getDocsNoop());
            body.put("docsErrored", job.getDocsErrored());
            body.put("docsNotFound", job.getDocsNotFound());
            body.put("startedAt", job.getStartedAt());
            body.put("elapsedMs", job.getElapsedMs());
            if (job.getFatalError() != null) {
                body.put("fatalError", job.getFatalError());
            }
            ArrayNode logArr = body.putArray("log");
            for (String line : job.snapshotLog()) {
                logArr.add(line);
            }
            res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
            res.send(JSON.writeValueAsString(body));
        } catch (Exception e) {
            sendErrorPage(res, "Failed to read progress", e);
        }
    }

    private static void handleResults(ServerRequest req, ServerResponse res) {
        try {
            Job job = JobManager.current();
            Map<String, ProcessResult> results = job == null ? new LinkedHashMap<>() : job.getResults();
            String fatal = job == null ? null : job.getFatalError();
            StringBuilder html = HtmlHelper.generateResultsHtml(results, fatal);
            res.headers().set(HeaderNames.CONTENT_TYPE, "text/html");
            res.send(html.toString());
        } catch (Exception e) {
            sendErrorPage(res, "Failed to render results", e);
        }
    }

    private static void handleShowConfig(ServerRequest req, ServerResponse res) {
        try {
            String json = Files.readString(Paths.get(Constants.RDP_SOURCE_CONFIG_FILE_PATH));
            JsonNode config = JsonHelper.asJson(json);

            String manageUrl = JsonHelper.textAt(config, "manageUrl", "Not set");
            String managePort = JsonHelper.textAt(config, "managePort", "Not set");
            String tenantId = JsonHelper.textAt(config, "tenantId", "Not set");
            JsonNode headers = config.get("headers");
            String authorization = JsonHelper.textAt(headers, "Authorization", "Not set");

            StringBuilder html = HtmlHelper.generateShowConfigHtml(manageUrl, managePort, tenantId, authorization);
            res.headers().set(HeaderNames.CONTENT_TYPE, "text/html");
            res.send(html.toString());
        } catch (Exception e) {
            sendErrorPage(res, "Failed to read source-config.json", e);
        }
    }

    private static void copyData(Job job) throws IOException {
        job.setCurrentLabel("Loading configuration");
        job.appendLog("Loading source/target config");
        RdpConfig sourceConfig = RdpConfigLoader.loadFromFile(Constants.RDP_SOURCE_CONFIG_FILE_PATH, RdpConfig.class);
        RdpConfig targetConfig = RdpConfigLoader.loadFromFile(Constants.RDP_TARGET_CONFIG_FILE_PATH, RdpConfig.class);
        requireConfig(sourceConfig, "source");
        requireConfig(targetConfig, "target");

        String esSearchUrl = String.format(Constants.ES_SEARCH_URL,
                sourceConfig.getManageUrl(),
                sourceConfig.getManagePort(),
                sourceConfig.getTenantId());
        IndexConfig indexData = RdpConfigLoader.loadFromFile(Constants.RDP_CONFIG_FILE_PATH, IndexConfig.class);

        if (indexData.getIndexes() == null || indexData.getIndexes().isEmpty()) {
            job.recordResult("(no indexes)", ProcessResult.errored("rdp-to-es-push.json has no indexes configured"));
            job.appendLog("No indexes configured — nothing to do");
            return;
        }

        // Phase 1: count every type/all so the progress bar has an accurate total
        job.setCurrentLabel("Counting documents…");
        job.appendLog("Phase 1 — counting across " + indexData.getIndexes().size() + " index(es)");

        record BatchWork(String sourceIndex, String targetIndex, String type /* null = all */, long totalDocs) {}
        List<BatchWork> batchWork = new ArrayList<>();
        int idQueryCount = 0;

        for (IndexConfig.Index index : indexData.getIndexes()) {
            String indexName = index.getIndexName();
            String srcTenantId = sourceConfig.getTenantId();
            srcTenantId = srcTenantId.contains("/") ? srcTenantId.split("/")[1] : srcTenantId;
            String sourceIndex = srcTenantId + readIndexOf(indexName);
            String targetIndex = targetConfig.getTenantId() + "_" + indexName;

            if (index.isCopyAll()) {
                long total = fetchCount(job, esSearchUrl, QueryHelper.generateCountQuery(sourceIndex),
                        sourceConfig.getHeaders(), indexName + " (all)");
                if (total < 0) continue;
                job.appendLog("  " + indexName + " (all): " + total + " docs");
                job.addTotalDocs(total);
                batchWork.add(new BatchWork(sourceIndex, targetIndex, null, total));
                continue;
            }

            if (index.getDataObjectTypes() != null) {
                for (String type : index.getDataObjectTypes()) {
                    long total = fetchCount(job, esSearchUrl,
                            QueryHelper.generateCountQueryByType(sourceIndex, type),
                            sourceConfig.getHeaders(), indexName + "/" + type);
                    if (total < 0) continue;
                    job.appendLog("  " + indexName + "/" + type + ": " + total + " docs");
                    job.addTotalDocs(total);
                    int batches = total == 0 ? 0 : (int) Math.ceil((double) total / PAGE_SIZE);
                    job.addTotalQueries(batches);
                    batchWork.add(new BatchWork(sourceIndex, targetIndex, type, total));
                }
            }

            if (index.getDataObjects() != null) {
                for (IndexConfig.DataObject doi : index.getDataObjects()) {
                    if (doi.getIds() == null) continue;
                    idQueryCount += doi.getIds().size();
                }
            }
        }

        job.addTotalQueries(idQueryCount);
        job.addTotalDocs(idQueryCount);
        job.appendLog("Phase 1 done — " + job.getTotalDocs() + " total doc(s), " + idQueryCount + " ID lookup(s) planned");

        // Phase 2: type-sharded parallel copy (agg → types → parallel workers)
        job.appendLog("Phase 2 — pageSize=" + PAGE_SIZE + ", workers=" + BATCH_PARALLELISM);
        ExecutorService pushPool = Executors.newFixedThreadPool(BATCH_PARALLELISM);

        try {
            for (BatchWork work : batchWork) {
                runBatchedPush(job, esSearchUrl, work.sourceIndex(), work.targetIndex(),
                        work.type(), work.totalDocs(), sourceConfig, targetConfig, pushPool);
            }

            // ID lookups (unchanged behaviour)
            for (IndexConfig.Index index : indexData.getIndexes()) {
                if (index.getDataObjects() == null) continue;
                String indexName = index.getIndexName();
                String srcTenantId = sourceConfig.getTenantId();
                srcTenantId = srcTenantId.contains("/") ? srcTenantId.split("/")[1] : srcTenantId;
                String sourceIndex = srcTenantId + readIndexOf(indexName);
                String targetIndex = targetConfig.getTenantId() + "_" + indexName;
                for (IndexConfig.DataObject doi : index.getDataObjects()) {
                    if (doi.getIds() == null) continue;
                    for (String id : doi.getIds()) {
                        String payload = QueryHelper.generateIdSearchQuery(id, sourceIndex);
                        runQuery(job, payload, targetIndex, id, sourceConfig, targetConfig, esSearchUrl, indexName + "/" + id);
                    }
                }
            }
        } finally {
            pushPool.shutdown();
            try { pushPool.awaitTermination(10, TimeUnit.MINUTES); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private static long fetchCount(Job job, String esSearchUrl, String countPayload,
                                   Map<String, String> headers, String label) {
        job.setCurrentLabel("Counting " + label);
        try {
            HttpResponse<String> r = RestHelper.sendPostRequest(esSearchUrl, countPayload, headers);
            if (r.statusCode() != 200) {
                job.appendLog("✗ count " + label + " — HTTP " + r.statusCode());
                return -1;
            }
            return JsonHelper.extractTotalHitsFromJson(r.body());
        } catch (Exception e) {
            job.appendLog("✗ count " + label + " — " + e.getMessage());
            return -1;
        }
    }

    /**
     * For copyAll (type==null): shards by first letter of the 'id' field (a-z).
     * adminservice strips 'sort' and 'aggs', but prefix queries work fine.
     * Each letter bucket has ≤27k docs in practice — safely under the 30k ceiling.
     * For a specific type: runs from/size pages directly in batchPool.
     */
    private static void runBatchedPush(Job job, String esSearchUrl, String sourceIndex, String targetIndex,
                                       String type, long totalDocs,
                                       RdpConfig sourceConfig, RdpConfig targetConfig,
                                       ExecutorService batchPool) {
        String label = sourceIndex + (type != null ? "/" + type : " (all)");
        if (totalDocs == 0) {
            job.appendLog("· " + label + " — 0 docs, skipping");
            return;
        }

        if (type == null) {
            // copyAll — shard by first letter of 'id' field (a-z) to stay under 30k per query
            job.appendLog("  copyAll — sharding " + sourceIndex + " by id prefix (a-z), " + totalDocs + " total docs");
            List<Future<?>> futures = new ArrayList<>();
            for (char c = 'a'; c <= 'z'; c++) {
                final String prefix = String.valueOf(c);
                futures.add(batchPool.submit(() ->
                        copyIdPrefixPages(job, esSearchUrl, sourceIndex, targetIndex, prefix, sourceConfig, targetConfig)
                ));
            }
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) { }
            }
        } else {
            // specific type — submit as a single worker task
            List<Future<?>> futures = new ArrayList<>();
            futures.add(batchPool.submit(() ->
                    copyTypePages(job, esSearchUrl, sourceIndex, targetIndex, type, totalDocs, sourceConfig, targetConfig)
            ));
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) { }
            }
        }
    }

    private static final int PREFIX_SAFE_LIMIT = 25_000;
    private static final int PREFIX_MAX_DEPTH  = 5;

    /** Fetches all docs whose 'id' starts with idPrefix.
     *  Counts first; if the bucket exceeds PREFIX_SAFE_LIMIT it sub-shards by the next character
     *  (up to PREFIX_MAX_DEPTH levels deep) so no single query ever approaches max_result_window. */
    private static void copyIdPrefixPages(Job job, String esSearchUrl, String sourceIndex, String targetIndex,
                                          String idPrefix,
                                          RdpConfig sourceConfig, RdpConfig targetConfig) {
        copyIdPrefixPages(job, esSearchUrl, sourceIndex, targetIndex, idPrefix, sourceConfig, targetConfig, 1);
    }

    private static void copyIdPrefixPages(Job job, String esSearchUrl, String sourceIndex, String targetIndex,
                                          String idPrefix,
                                          RdpConfig sourceConfig, RdpConfig targetConfig, int depth) {
        // Count first to decide whether to sub-shard or copy directly
        long count;
        try {
            String countPayload = QueryHelper.generateIdPrefixCountQuery(sourceIndex, idPrefix);
            HttpResponse<String> cr = RestHelper.sendPostRequest(esSearchUrl, countPayload, sourceConfig.getHeaders());
            count = cr.statusCode() == 200 ? JsonHelper.extractTotalHitsFromJson(cr.body()) : -1;
        } catch (Exception e) {
            count = -1;
        }

        if (count == 0) return; // nothing here

        if (count > PREFIX_SAFE_LIMIT && depth < PREFIX_MAX_DEPTH) {
            job.appendLog("  [id:" + idPrefix + "*] has " + count + " docs — sub-sharding to 2-char prefixes");
            for (char c = 'a'; c <= 'z'; c++) {
                copyIdPrefixPages(job, esSearchUrl, sourceIndex, targetIndex, idPrefix + c, sourceConfig, targetConfig, depth + 1);
            }
            for (char d = '0'; d <= '9'; d++) {
                copyIdPrefixPages(job, esSearchUrl, sourceIndex, targetIndex, idPrefix + d, sourceConfig, targetConfig, depth + 1);
            }
            return;
        }

        String label = sourceIndex + "[id:" + idPrefix + "*]";
        int from = 0;
        int page = 0;
        while (true) {
            page++;
            String payload = QueryHelper.generateIdPrefixDocsBatch(sourceIndex, idPrefix, from, PAGE_SIZE);
            job.setCurrentLabel("Copying " + label + " [page " + page + "]");

            HttpResponse<String> response;
            try {
                response = RestHelper.sendPostRequest(esSearchUrl, payload, sourceConfig.getHeaders());
            } catch (IOException e) {
                job.appendLog("✗ " + label + " page " + page + " — " + e.getMessage());
                job.incrementCompletedQueries();
                break;
            }
            if (response.statusCode() != 200) {
                job.appendLog("✗ " + label + " page " + page + " — HTTP " + response.statusCode());
                job.incrementCompletedQueries();
                break;
            }

            JsonNode hits;
            try {
                hits = JsonHelper.extractHitsSectionFromJson(response.body());
            } catch (Exception e) {
                job.appendLog("✗ " + label + " page " + page + " parse error — " + e.getMessage());
                job.incrementCompletedQueries();
                break;
            }

            if (hits == null || !hits.isArray() || hits.isEmpty()) {
                if (from > 0) job.appendLog("· " + label + " — done (" + from + " docs)");
                break;
            }

            from += hits.size();

            for (JsonNode hit : hits) {
                String docId = JsonHelper.textAt(hit, "_id", "(unknown)");
                String postUrl = RestHelper.buildPostUrl(targetConfig, targetIndex, docId);
                ProcessResult result;
                try {
                    String body = JsonHelper.prepareObjectForTarget(hit, sourceConfig, targetConfig);
                    HttpResponse<String> postResp = RestHelper.sendPostRequest(postUrl, body, targetConfig.getHeaders());
                    if (postResp.statusCode() >= 200 && postResp.statusCode() < 300) {
                        JsonNode parsed = JsonHelper.tryAsJson(postResp.body());
                        String resultText = JsonHelper.textAt(parsed, "result", null);
                        result = resultText != null ? ProcessResult.fromEsResult(resultText)
                                : ProcessResult.errored("no 'result' field: " + truncate(postResp.body()));
                    } else {
                        result = ProcessResult.errored("target ES HTTP " + postResp.statusCode() + ": " + truncate(postResp.body()));
                    }
                } catch (Exception e) {
                    result = ProcessResult.errored("push failed: " + rootCauseMessage(e));
                }
                job.recordResult(docId, result);
                if (result.getStatus() == ProcessResult.Status.ERRORED) {
                    job.appendLog("✗ " + docId + " — " + result.getMessage());
                }
            }
            job.incrementCompletedQueries();
        }
    }

    /** Fetches all docs of one type using from/size pages and pushes each to target. */
    private static void copyTypePages(Job job, String esSearchUrl, String sourceIndex, String targetIndex,
                                      String type, long totalDocs,
                                      RdpConfig sourceConfig, RdpConfig targetConfig) {
        String label = sourceIndex + "/" + type;
        int from = 0;
        int page = 0;
        while (from < totalDocs) {
            page++;
            int size = (int) Math.min(PAGE_SIZE, totalDocs - from);
            String payload = QueryHelper.generateTypeDocsBatch(sourceIndex, type, from, size);
            job.setCurrentLabel("Copying " + label + " [page " + page + ", from=" + from + "]");

            HttpResponse<String> response;
            try {
                response = RestHelper.sendPostRequest(esSearchUrl, payload, sourceConfig.getHeaders());
            } catch (IOException e) {
                job.appendLog("✗ " + label + " page " + page + " — " + e.getMessage());
                job.incrementCompletedQueries();
                break;
            }
            if (response.statusCode() != 200) {
                job.appendLog("✗ " + label + " page " + page + " — HTTP " + response.statusCode());
                job.incrementCompletedQueries();
                break;
            }

            JsonNode hits;
            try {
                hits = JsonHelper.extractHitsSectionFromJson(response.body());
            } catch (Exception e) {
                job.appendLog("✗ " + label + " page " + page + " parse error — " + e.getMessage());
                job.incrementCompletedQueries();
                break;
            }

            if (hits == null || !hits.isArray() || hits.isEmpty()) {
                job.appendLog("· " + label + " page " + page + " — no more docs");
                job.incrementCompletedQueries();
                break;
            }

            from += hits.size();

            for (JsonNode hit : hits) {
                String docId = JsonHelper.textAt(hit, "_id", "(unknown)");
                String postUrl = RestHelper.buildPostUrl(targetConfig, targetIndex, docId);
                ProcessResult result;
                try {
                    String body = JsonHelper.prepareObjectForTarget(hit, sourceConfig, targetConfig);
                    HttpResponse<String> postResp = RestHelper.sendPostRequest(postUrl, body, targetConfig.getHeaders());
                    if (postResp.statusCode() >= 200 && postResp.statusCode() < 300) {
                        JsonNode parsed = JsonHelper.tryAsJson(postResp.body());
                        String resultText = JsonHelper.textAt(parsed, "result", null);
                        result = resultText != null ? ProcessResult.fromEsResult(resultText)
                                : ProcessResult.errored("no 'result' field: " + truncate(postResp.body()));
                    } else {
                        result = ProcessResult.errored("target ES HTTP " + postResp.statusCode() + ": " + truncate(postResp.body()));
                    }
                } catch (Exception e) {
                    result = ProcessResult.errored("push failed: " + rootCauseMessage(e));
                }
                job.recordResult(docId, result);
                if (result.getStatus() == ProcessResult.Status.ERRORED) {
                    job.appendLog("✗ " + docId + " — " + result.getMessage());
                }
            }
            job.incrementCompletedQueries();
        }
    }

    private static void runQuery(Job job, String payload, String targetIndex, String optionalId,
                                 RdpConfig sourceConfig, RdpConfig targetConfig,
                                 String esSearchUrl, String contextLabel) {
        job.setCurrentLabel("Searching " + contextLabel);
        job.appendLog("→ search " + contextLabel);

        HttpResponse<String> response;
        try {
            response = RestHelper.sendPostRequest(esSearchUrl, payload, sourceConfig.getHeaders());
        } catch (IOException e) {
            String msg = "source ES search failed: " + e.getMessage();
            job.recordResult(contextLabel, ProcessResult.errored(msg));
            job.appendLog("✗ " + contextLabel + " — " + msg);
            job.incrementCompletedQueries();
            return;
        }

        if (response.statusCode() != 200) {
            String msg = "source ES returned HTTP " + response.statusCode() + ": " + truncate(response.body());
            job.recordResult(contextLabel, ProcessResult.errored(msg));
            job.appendLog("✗ " + contextLabel + " — " + msg);
            job.incrementCompletedQueries();
            return;
        }

        JsonNode hits;
        try {
            hits = JsonHelper.extractHitsSectionFromJson(response.body());
        } catch (Exception e) {
            String msg = "cannot parse source ES response: " + e.getMessage();
            job.recordResult(contextLabel, ProcessResult.errored(msg));
            job.appendLog("✗ " + contextLabel + " — " + msg);
            job.incrementCompletedQueries();
            return;
        }

        if (hits == null || !hits.isArray() || hits.isEmpty()) {
            String key = optionalId != null ? optionalId : contextLabel;
            job.recordResult(key, ProcessResult.notFound("no documents matched in source"));
            job.appendLog("· " + contextLabel + " — no documents found");
            job.incrementCompletedQueries();
            return;
        }

        int hitCount = hits.size();
        job.appendLog("· " + contextLabel + " — " + hitCount + " document(s) to push");

        int i = 0;
        for (JsonNode hit : hits) {
            i++;
            String id = optionalId != null ? optionalId : JsonHelper.textAt(hit, "_id", "(unknown id)");
            job.setCurrentLabel("Pushing " + id + " (" + i + "/" + hitCount + " of " + contextLabel + ")");
            String postUrl = RestHelper.buildPostUrl(targetConfig, targetIndex, id);
            ProcessResult result;
            try {
                String objectString = JsonHelper.prepareObjectForTarget(hit, sourceConfig, targetConfig);
                HttpResponse<String> postResponse = RestHelper.sendPostRequest(postUrl, objectString, targetConfig.getHeaders());

                if (postResponse.statusCode() >= 200 && postResponse.statusCode() < 300) {
                    String body = postResponse.body();
                    JsonNode parsed = JsonHelper.tryAsJson(body);
                    String resultText = JsonHelper.textAt(parsed, "result", null);
                    if (resultText == null) {
                        result = ProcessResult.errored("target accepted (HTTP " + postResponse.statusCode() + ") but response has no 'result' field: " + truncate(body));
                    } else {
                        result = ProcessResult.fromEsResult(resultText);
                    }
                } else {
                    result = ProcessResult.errored("target ES HTTP " + postResponse.statusCode() + ": " + truncate(postResponse.body()));
                }
            } catch (IOException e) {
                result = ProcessResult.errored("target ES push failed: " + e.getMessage());
            } catch (Exception e) {
                result = ProcessResult.errored("unexpected error pushing to target: " + rootCauseMessage(e));
            }
            job.recordResult(id, result);
            if (result.getStatus() == ProcessResult.Status.ERRORED) {
                job.appendLog("✗ " + id + " — " + result.getMessage());
            }
        }
        job.incrementCompletedQueries();
    }

    private static void createEntities(Job job, String service, String entityKey, JsonNode entities) throws IOException {
        job.setCurrentLabel("Loading configuration");
        RdpConfig sourceConfig = RdpConfigLoader.loadFromFile(Constants.RDP_SOURCE_CONFIG_FILE_PATH, RdpConfig.class);
        requireConfig(sourceConfig, "source");

        String createUrl = String.format(Constants.PLATFORM_CREATE_URL,
                sourceConfig.getManageUrl(),
                sourceConfig.getManagePort(),
                sourceConfig.getTenantId(),
                service);

        int total = entities.size();
        job.setTotalQueries(total);
        job.appendLog("Creating " + total + " object(s) via " + service + " (key=" + entityKey + ")");
        job.appendLog("URL: " + createUrl);

        int i = 0;
        for (JsonNode entity : entities) {
            i++;
            String id = JsonHelper.textAt(entity, "id", "entity-" + i);
            String type = JsonHelper.textAt(entity, "type", "unknown");
            String label = id + " (" + type + ")";
            job.setCurrentLabel("Creating " + label + " [" + i + "/" + total + "]");
            job.appendLog("→ create " + label);

            ObjectNode createBody = JSON.createObjectNode();
            createBody.set(entityKey, entity);
            String payload;
            try {
                payload = JSON.writeValueAsString(createBody);
            } catch (Exception e) {
                ProcessResult r = ProcessResult.errored("failed to serialize entity: " + e.getMessage());
                job.recordResult(id, r);
                job.appendLog("✗ " + label + " — " + r.getMessage());
                job.incrementCompletedQueries();
                continue;
            }

            ProcessResult result;
            try {
                java.net.http.HttpResponse<String> response = RestHelper.sendPostRequest(createUrl, payload, sourceConfig.getHeaders());
                result = parsePlatformCreateResponse(response, id);
            } catch (IOException e) {
                result = ProcessResult.errored("request failed: " + e.getMessage());
            } catch (Exception e) {
                result = ProcessResult.errored("unexpected error: " + rootCauseMessage(e));
            }

            job.recordResult(id, result);
            if (result.getStatus() == ProcessResult.Status.ERRORED) {
                job.appendLog("✗ " + label + " — " + result.getMessage());
            } else {
                job.appendLog("✓ " + label);
            }
            job.incrementCompletedQueries();
        }
    }

    private static ProcessResult parsePlatformCreateResponse(java.net.http.HttpResponse<String> response, String id) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ProcessResult.errored("HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        JsonNode parsed = JsonHelper.tryAsJson(response.body());
        if (parsed == null) {
            return ProcessResult.errored("non-JSON response (HTTP " + response.statusCode() + "): " + truncate(response.body()));
        }
        JsonNode respNode = parsed.get("response");
        if (respNode == null) {
            return ProcessResult.errored("unexpected response shape: " + truncate(response.body()));
        }
        String status = JsonHelper.textAt(respNode, "status", "");
        JsonNode messages = respNode.at("/statusDetail/messages");
        String firstMsg = (messages != null && messages.isArray() && !messages.isEmpty())
                ? JsonHelper.textAt(messages.get(0), "message", "")
                : "";
        if ("success".equalsIgnoreCase(status)) {
            return new ProcessResult(ProcessResult.Status.CREATED, firstMsg);
        }
        String detail = firstMsg.isBlank() ? ("status=" + status + ": " + truncate(response.body())) : firstMsg;
        return ProcessResult.errored(detail);
    }

    private static void requireConfig(RdpConfig cfg, String label) {
        if (cfg == null) {
            throw new IllegalStateException(label + " config could not be loaded");
        }
        if (cfg.getManageUrl() == null || cfg.getManageUrl().isBlank()) {
            throw new IllegalStateException(label + " config missing manageUrl");
        }
        if (cfg.getManagePort() == null || cfg.getManagePort().isBlank()) {
            throw new IllegalStateException(label + " config missing managePort");
        }
        if (cfg.getTenantId() == null || cfg.getTenantId().isBlank()) {
            throw new IllegalStateException(label + " config missing tenantId");
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return (msg == null || msg.isBlank()) ? cur.getClass().getSimpleName() : msg;
    }

    private static void sendErrorPage(ServerResponse res, String title, Throwable cause) {
        String detail = rootCauseMessage(cause);
        cause.printStackTrace();
        res.status(500);
        res.headers().set(HeaderNames.CONTENT_TYPE, "text/html");
        res.send(HtmlHelper.generateErrorHtml(title, detail).toString());
    }

    static String readIndexOf(String input) {
        if (input == null || !input.contains("index")) {
            return input;
        }
        return input.replaceFirst("index", "readindex");
    }
}