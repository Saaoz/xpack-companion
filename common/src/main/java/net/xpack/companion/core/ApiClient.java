package net.xpack.companion.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Client HTTP de l'API XPack (docs/API.md). Asynchrone : n'est jamais appelé depuis le thread de rendu en mode bloquant. */
public final class ApiClient {
    /** Réponse brute ; {@code status = 0} signale une erreur réseau. */
    public static final class Response {
        private final int status;
        private final JsonObject body;

        public Response(int status, JsonObject body) {
            this.status = status;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public JsonObject body() {
            return body;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Response)) return false;
            Response other = (Response) o;
            return java.util.Objects.equals(status, other.status) && java.util.Objects.equals(body, other.body);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(status, body);
        }

        @Override
        public String toString() {
            return "Response[" + "status=" + status + ", body=" + body + "]";
        }

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

    /** Les requêtes partent sur ces fils (démon : ils ne retiennent jamais la fermeture du jeu). */
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "XPack http");
        t.setDaemon(true);
        return t;
    });

    /** HttpURLConnection plutôt que java.net.http (Java 11) : le mod doit tourner en Java 8 (Minecraft 1.12.2). */
    public static Transport http(String baseUrl, String userAgent) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return (method, path, body, token) -> CompletableFuture.supplyAsync(() -> {
            java.net.HttpURLConnection con = null;
            try {
                con = (java.net.HttpURLConnection) new java.net.URL(base + path).openConnection();
                con.setConnectTimeout(8_000);
                con.setReadTimeout(15_000);
                con.setRequestMethod(method);
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty("User-Agent", userAgent);
                if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setDoOutput(true);
                    try (java.io.OutputStream out = con.getOutputStream()) {
                        out.write(GSON.toJson(body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                int status = con.getResponseCode();
                java.io.InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
                return new Response(status, parse(in == null ? null : readAll(in)));
            } catch (java.io.IOException | RuntimeException e) {
                return new Response(0, new JsonObject());
            } finally {
                if (con != null) con.disconnect();
            }
        }, IO);
    }

    private static String readAll(java.io.InputStream in) throws java.io.IOException {
        try (java.io.InputStream is = in; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            for (int n; (n = is.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    static JsonObject parse(String s) {
        try {
            JsonElement el = J8.parseJson(s == null || J8.isBlank(s) ? "{}" : s);
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

    /** Catalogue des progrès du modpack (#80) : ils deviennent les succès XPack. */
    public CompletableFuture<Response> postCatalog(String sessionId, JsonObject body, String token) {
        return transport.send("POST", "/v1/sessions/" + sessionId + "/catalog", body, token);
    }

    /** Objets du modpack dont l'import n'a pas pu lire la texture : le jeu peut en dessiner l'icône (#189). */
    public CompletableFuture<Response> iconGaps(String sessionId, String token) {
        return transport.send("GET", "/v1/sessions/" + sessionId + "/icon-gaps", null, token);
    }

    /** Icône d'un objet dessinée en jeu (PNG en base64) ; le serveur ne l'accepte que pour un objet demandé. */
    public CompletableFuture<Response> postIcon(String sessionId, String item, String pngBase64, String token) {
        JsonObject b = new JsonObject();
        b.addProperty("item", item);
        b.addProperty("png", pngBase64);
        return transport.send("POST", "/v1/sessions/" + sessionId + "/icons", b, token);
    }

    /** Demande au support (contestation d'une détection), envoyée par e-mail à l'équipe. */
    public CompletableFuture<Response> supportReport(JsonObject body, String token) {
        return transport.send("POST", "/v1/support/reports", body, token);
    }

    /**
     * Présence en multijoueur (#126) : empreintes des autres joueurs de la liste. Sans jeton (compte
     * pas encore lié), {@code body} porte l'empreinte du joueur. La réponse sert à {@code /xpack check}.
     */
    public CompletableFuture<Response> presence(JsonObject body, String token) {
        return transport.send("POST", "/v1/presence", body, token);
    }

    /** Équipe XPack (#145) : inviter un joueur détecté sur le serveur ({@code sessionId}, {@code uuidHash}). */
    public CompletableFuture<Response> teamInvite(JsonObject body, String token) {
        return transport.send("POST", "/v1/teams/invite", body, token);
    }

    /** Accepter ou refuser une invitation reçue. */
    public CompletableFuture<Response> teamAnswer(String inviteId, boolean accept, JsonObject body, String token) {
        return transport.send("POST", "/v1/teams/invites/" + inviteId + "/" + (accept ? "accept" : "decline"), body, token);
    }

    public CompletableFuture<Response> teamLeave(String teamId, JsonObject body, String token) {
        return transport.send("POST", "/v1/teams/" + teamId + "/leave", body, token);
    }

    public CompletableFuture<Response> unlink(String token) {
        return transport.send("DELETE", "/v1/link", null, token);
    }
}
