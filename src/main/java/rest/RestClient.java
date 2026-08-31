package rest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

public class RestClient {

   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
   private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

   private static final HttpClient client = HttpClient.newBuilder()
         .connectTimeout(CONNECT_TIMEOUT)
         .build();

   public static String postJson(String url, String jsonPayload) throws IOException {
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();
      try {
         HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
         return response.body();
      } catch (ConnectException e) {
         throw new IOException("Cannot connect to " + url + " (is the service running?): " + e.getMessage(), e);
      } catch (HttpConnectTimeoutException e) {
         throw new IOException("Connect timeout (" + CONNECT_TIMEOUT.toSeconds() + "s) reaching " + url, e);
      } catch (HttpTimeoutException e) {
         throw new IOException("Request timeout (" + REQUEST_TIMEOUT.toSeconds() + "s) calling " + url, e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException("Request interrupted while calling " + url, e);
      }
   }
}