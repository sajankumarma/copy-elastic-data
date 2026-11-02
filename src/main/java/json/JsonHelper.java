package json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import objects.RdpConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonHelper {
   private static final ObjectMapper mapper = new ObjectMapper();

   public static JsonNode asJson(String json) throws JsonProcessingException {
      if (json == null || json.isBlank()) {
         throw new JsonProcessingException("Empty/null response body, cannot parse as JSON") {};
      }
      return mapper.readTree(json);
   }

   public static JsonNode tryAsJson(String json) {
      if (json == null || json.isBlank()) {
         return null;
      }
      try {
         return mapper.readTree(json);
      } catch (JsonProcessingException e) {
         return null;
      }
   }

   public static JsonNode extractHitsSectionFromJson(String responseBody) throws JsonProcessingException {
      JsonNode root = asJson(responseBody);
      JsonNode hits = root.at("/response/adminObjects/0/data/jsonData/hits/hits");
      return (hits.isMissingNode() || hits.isNull()) ? null : hits;
   }

   public static long extractTotalHitsFromJson(String responseBody) throws JsonProcessingException {
      JsonNode root = asJson(responseBody);
      JsonNode totalNode = root.at("/response/adminObjects/0/data/jsonData/hits/total/value");
      if (!totalNode.isMissingNode() && !totalNode.isNull()) {
         return totalNode.longValue();
      }
      // Older ES: total is a plain number
      JsonNode totalLegacy = root.at("/response/adminObjects/0/data/jsonData/hits/total");
      if (!totalLegacy.isMissingNode() && !totalLegacy.isNull() && totalLegacy.isNumber()) {
         return totalLegacy.longValue();
      }
      return 0;
   }

   /** Parses the terms aggregation buckets from an essearch response.
    *  Returns a list of (typeName, docCount) pairs. */
   public static List<Map.Entry<String, Long>> extractTypesFromAggregation(String responseBody) throws JsonProcessingException {
      List<Map.Entry<String, Long>> result = new ArrayList<>();
      JsonNode root = asJson(responseBody);
      JsonNode buckets = root.at("/response/adminObjects/0/data/jsonData/aggregations/types/buckets");
      if (buckets.isMissingNode() || !buckets.isArray()) return result;
      for (JsonNode bucket : buckets) {
         String key = bucket.path("key").asText(null);
         long count = bucket.path("doc_count").asLong(0);
         if (key != null && count > 0) {
            result.add(Map.entry(key, count));
         }
      }
      return result;
   }

   public static List<String> extractIdsFromHits(JsonNode hits) {
      List<String> ids = new ArrayList<>();
      if (hits == null || !hits.isArray()) return ids;
      for (JsonNode hit : hits) {
         JsonNode id = hit.get("_id");
         if (id != null && !id.isNull()) ids.add(id.asText());
      }
      return ids;
   }

   public static String extractSearchAfterValueFromLastHit(JsonNode hits) {
      if (hits == null || !hits.isArray() || hits.isEmpty()) return null;
      JsonNode lastHit = hits.get(hits.size() - 1);
      JsonNode sortArr = lastHit.get("sort");
      if (sortArr != null && sortArr.isArray() && !sortArr.isEmpty()) {
         return sortArr.get(0).asText(null);
      }
      JsonNode id = lastHit.get("_id");
      return id != null ? id.asText(null) : null;
   }

   /** Returns the raw JSON representation of the first sort value (number stays a number,
    *  string gets its quotes). Use this to embed directly in search_after:[...]. */
   public static String extractSearchAfterJsonFromLastHit(JsonNode hits) {
      if (hits == null || !hits.isArray() || hits.isEmpty()) return null;
      JsonNode lastHit = hits.get(hits.size() - 1);
      JsonNode sortArr = lastHit.get("sort");
      if (sortArr != null && sortArr.isArray() && !sortArr.isEmpty()) {
         return sortArr.get(0).toString(); // e.g. 12345 or "\"abc\""
      }
      return null;
   }

   public static String prepareObjectForTarget(JsonNode hit, RdpConfig sourceConfig, RdpConfig targetConfig) {
      JsonNode object = hit.get("_source");
      if (object == null || object.isNull() || object.isMissingNode()) {
         throw new IllegalArgumentException("Hit is missing _source field: " + hit);
      }
      String sourceTenantId = sourceConfig.getTenantId();
      String targetTenantId = targetConfig.getTenantId();
      String json = object.toPrettyString();
      if (sourceTenantId != null && targetTenantId != null) {
         json = json.replaceAll(sourceTenantId, targetTenantId);
      }
      return json;
   }

   public static String textAt(JsonNode node, String fieldName, String fallback) {
      if (node == null) {
         return fallback;
      }
      JsonNode value = node.get(fieldName);
      if (value == null || value.isNull() || value.isMissingNode()) {
         return fallback;
      }
      return value.asText(fallback);
   }

   public static void saveAsPrettyJson(String json, String filePath) throws IOException {
      if (json == null || json.isBlank()) {
         throw new IOException("Refusing to save empty JSON to " + filePath);
      }
      JsonNode jsonNode = mapper.readTree(json);
      String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
      Files.writeString(
            Paths.get(filePath),
            prettyJson,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
      );
   }
}