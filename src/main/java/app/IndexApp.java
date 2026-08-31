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
import java.util.LinkedHashMap;
import java.util.Map;

public class IndexApp {
    private static final ObjectMapper JSON = new ObjectMapper();

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

        int totalQueries = countQueries(indexData);
        job.setTotalQueries(totalQueries);
        job.appendLog("Planned " + totalQueries + " source queries across " + indexData.getIndexes().size() + " index(es)");

        for (IndexConfig.Index index : indexData.getIndexes()) {
            String indexName = index.getIndexName();
            String sourceTenantIdString = sourceConfig.getTenantId();
            String sourceTenantId = sourceTenantIdString.contains("/") ? sourceTenantIdString.split("/")[1] : sourceTenantIdString;
            String sourceIndex = sourceTenantId + readIndexOf(indexName);
            String targetIndex = targetConfig.getTenantId() + "_" + indexName;

            if (index.getDataObjectTypes() != null) {
                for (String dataObjectType : index.getDataObjectTypes()) {
                    String payload = QueryHelper.generateTypeSearchQuery(sourceIndex, dataObjectType);
                    String label = indexName + "/" + dataObjectType;
                    runQuery(job, payload, targetIndex, null, sourceConfig, targetConfig, esSearchUrl, label);
                }
            }

            if (index.getDataObjects() != null) {
                for (IndexConfig.DataObject dataObjectId : index.getDataObjects()) {
                    if (dataObjectId.getIds() == null) continue;
                    for (String id : dataObjectId.getIds()) {
                        String payload = QueryHelper.generateIdSearchQuery(id, sourceIndex);
                        runQuery(job, payload, targetIndex, id, sourceConfig, targetConfig, esSearchUrl, indexName + "/" + id);
                    }
                }
            }
        }
    }

    private static int countQueries(IndexConfig indexData) {
        int count = 0;
        for (IndexConfig.Index index : indexData.getIndexes()) {
            if (index.getDataObjectTypes() != null) count += index.getDataObjectTypes().size();
            if (index.getDataObjects() != null) {
                for (IndexConfig.DataObject doi : index.getDataObjects()) {
                    if (doi.getIds() != null) count += doi.getIds().size();
                }
            }
        }
        return count;
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