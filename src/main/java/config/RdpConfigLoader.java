package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RdpConfigLoader {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T loadFromFile(String configFilePath, Class<T> clazz) throws IOException {
        // Read raw JSON content
        String configFileContent = Files.readString(Path.of(configFilePath));

        // Parse into JsonNode so we don’t need a base POJO
        JsonNode root = mapper.readTree(configFileContent);

        // Replace placeholders if nodes exist
        String sourceTenantId = root.path("source").path("tenantId").asText(null);
        String targetTenantId = root.path("target").path("tenantId").asText(null);

        if (sourceTenantId != null) {
            configFileContent = configFileContent.replace("{{SOURCE_TENANT_ID}}", sourceTenantId);
        }
        if (targetTenantId != null) {
            configFileContent = configFileContent.replace("{{TARGET_TENANT_ID}}", targetTenantId);
        }

        // Finally parse into the requested type
        return mapper.readValue(configFileContent, clazz);
    }
}
