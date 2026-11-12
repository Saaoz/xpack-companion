package net.xpack.companion.core;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/**
 * Ce que le mod demande à un serveur XPack. {@link ApiClient} est l'implémentation HTTP vers l'API
 * XPack ; tout le reste du mod ne dépend que de cette interface.
 */
public interface Backend {

    /** Vérifie qu'un serveur XPack répond ({@code { ok: true, service: "xpack" }}). */
    CompletableFuture<ApiClient.Response> health();

    CompletableFuture<ApiClient.Response> linkStart(String minecraftUuid, String minecraftName);

    CompletableFuture<ApiClient.Response> linkPoll(String deviceCode);

    CompletableFuture<ApiClient.Response> openSession(JsonObject body, String token);

    CompletableFuture<ApiClient.Response> postEvents(String sessionId, JsonObject body, String token);

    /** Catalogue des progrès du modpack (#80) : ils deviennent les succès XPack. */
    CompletableFuture<ApiClient.Response> postCatalog(String sessionId, JsonObject body, String token);

    /** Objets du modpack dont l'import n'a pas pu lire la texture : le jeu peut en dessiner l'icône (#189). */
    CompletableFuture<ApiClient.Response> iconGaps(String sessionId, String token);

    /** Icône d'un objet dessinée en jeu (PNG en base64) ; le serveur ne l'accepte que pour un objet demandé. */
    CompletableFuture<ApiClient.Response> postIcon(String sessionId, String item, String pngBase64, String token);

    /** Demande au support (contestation d'une détection). */
    CompletableFuture<ApiClient.Response> supportReport(JsonObject body, String token);

    /** Présence en multijoueur (#126) : empreintes des autres joueurs de la liste. */
    CompletableFuture<ApiClient.Response> presence(JsonObject body, String token);

    /** Équipe XPack (#145) : inviter un joueur détecté sur le serveur. */
    CompletableFuture<ApiClient.Response> teamInvite(JsonObject body, String token);

    /** Accepter ou refuser une invitation reçue. */
    CompletableFuture<ApiClient.Response> teamAnswer(String inviteId, boolean accept, JsonObject body, String token);

    CompletableFuture<ApiClient.Response> teamLeave(String teamId, JsonObject body, String token);

    /** Catégories de speedrun des modpacks (#222). Lecture seule, sans jeton ni corps. */
    CompletableFuture<ApiClient.Response> speedrunCategories();

    CompletableFuture<ApiClient.Response> unlink(String token);
}
