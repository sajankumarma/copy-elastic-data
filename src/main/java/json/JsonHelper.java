package json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import objects.RdpConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

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