package net.xpack.companion.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Client HTTP de l'API XPack (docs/API.md). Asynchrone : n'est jamais appelé depuis le thread de rendu en mode bloquant. */
public final class ApiClient {
    /** Réponse brute ; {@code status = 0} signale une erreur réseau. */
    public record Response(int status, JsonObject body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** Couche transport, remplaçable dans les tests. */
    public interface Transport {
        CompletableFuture<Response> send(String method, String path, JsonObject body, String token);
    }

    private static final Gson GSON = new Gson();
    private final Transport transport;

    public ApiClient(Transport transport) {
        this.transport = transport;
    }

    public static Transport http(String baseUrl, String userAgent) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return (method, path, body, token) -> {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent);
            if (token != null) req.header("Authorization", "Bearer " + token);
            if (body != null) {
                req.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
            } else {
                req.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return client.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> new Response(res.statusCode(), parse(res.body())))
                    .exceptionally(e -> new Response(0, new JsonObject()));
        };
    }

    static JsonObject parse(String s) {
        try {
            var el = JsonParser.parseString(s == null || s.isBlank() ? "{}" : s);
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    public CompletableFuture<Response> linkStart(String minecraftUuid, String minecraftName) {
        JsonObject b = new JsonObject();
        b.addProperty("minecraftUuid", minecraftUuid);
        b.addProperty("minecraftName", minecraftName);
        return transport.send("POST", "/v1/link/start", b, null);
    }

    public CompletableFuture<Response> linkPoll(String deviceCode) {
        JsonObject b = new JsonObject();
        b.addProperty("deviceCode", deviceCode);
        return transport.send("POST", "/v1/link/poll", b, null);
    }

    public CompletableFuture<Response> openSession(JsonObject body, String token) {
        return transport.send("POST", "/v1/sessions", body, token);
    }

    public CompletableFuture<Response> postEvents(String sessionId, JsonObject body, String token) {
        return transport.send("POST", "/v1/sessions/" + sessionId + "/events", body, token);
    }

    public CompletableFuture<Response> unlink(String token) {
        return transport.send("DELETE", "/v1/link", null, token);
    }
}
