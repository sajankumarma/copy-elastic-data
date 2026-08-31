package rest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import objects.RdpConfig;

public class RestHelper {
   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
   private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

   private static final HttpClient client = HttpClient.newBuilder()
         .connectTimeout(CONNECT_TIMEOUT)
         .build();

   public static HttpResponse<String> sendPostRequest(String url, String body, Map<String, String> headers) throws IOException {
      String encodedUrl = url.replace(" ", "%20");
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(encodedUrl))
            .headers(flattenHeaders(headers))
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
      try {
         return client.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (ConnectException e) {
         throw new IOException("Cannot connect to " + encodedUrl + " (is the service running?): " + e.getMessage(), e);
      } catch (HttpConnectTimeoutException e) {
         throw new IOException("Connect timeout (" + CONNECT_TIMEOUT.toSeconds() + "s) reaching " + encodedUrl, e);
      } catch (HttpTimeoutException e) {
         throw new IOException("Request timeout (" + REQUEST_TIMEOUT.toSeconds() + "s) calling " + encodedUrl, e);
      } catch (IOException e) {
         throw new IOException("Network error calling " + encodedUrl + ": " + e.getMessage(), e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException("Request interrupted while calling " + encodedUrl, e);
      }
   }

   private static String[] flattenHeaders(Map<String, String> headers) {
      if (headers == null || headers.isEmpty()) {
         return new String[0];
      }
      return headers.entrySet().stream()
            .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
            .toArray(String[]::new);
   }

   public static String buildPostUrl(RdpConfig targetConfig, String targetIndex, String id) {
      String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
      return String.format("%s:%s/%s/_doc/%s",
            targetConfig.getManageUrl(),
            targetConfig.getManagePort(),
            targetIndex,
            encodedId);
   }
}