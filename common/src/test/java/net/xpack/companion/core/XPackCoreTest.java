package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XPackCoreTest {
    static final String UUID = "0f9e1c2a-3b4c-4d5e-8f60-718293a4b5c6";

    /** Faux serveur : enregistre les requêtes et répond avec des réponses programmées. */
    static final class FakeApi implements ApiClient.Transport {
        record Call(String method, String path, JsonObject body, String token) {}

        final List<Call> calls = new ArrayList<>();
        final Deque<ApiClient.Response> replies = new ArrayDeque<>();

        FakeApi reply(int status, String json) {
            replies.add(new ApiClient.Response(status, JsonParser.parseString(json).getAsJsonObject()));
            return this;
        }

        @Override
        public CompletableFuture<ApiClient.Response> send(String method, String path, JsonObject body, String token) {
            calls.add(new Call(method, path, body, token));
            ApiClient.Response r = replies.isEmpty() ? new ApiClient.Response(200, new JsonObject()) : replies.poll();
            return CompletableFuture.completedFuture(r);
        }

        Call last(String path) {
            for (int i = calls.size() - 1; i >= 0; i--) if (calls.get(i).path().endsWith(path)) return calls.get(i);
            return null;
        }
    }

    static final class FakeUi implements Ui {
        final List<String> notices = new ArrayList<>();
        final List<String> toasts = new ArrayList<>();
        String warnedCommand;
        Runnable onContinue;

        @Override
        public void showCheatWarning(String command, Runnable onContinue) {
            this.warnedCommand = command;
            this.onContinue = onContinue;
        }

        @Override
        public void toastUnlocked(String name, int points, String rarity) {
            toasts.add(name + ":" + points + ":" + rarity);
        }

        @Override
        public void notify(String key, Object... args) {
            notices.add(key);
        }
    }

    /** Horloge réglable pour piloter les délais d'envoi. */
    static final class MutableClock extends Clock {
        long millis = Instant.parse("2026-10-14T10:00:00Z").toEpochMilli();

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    @TempDir
    Path dir;
    FakeApi api;
    FakeUi ui;
    MutableClock clock;
    Config config;
    XPackCore core;

    static final String SESSION = """
            {"sessionId":"s_1","integrity":{"status":"propre","flaggedAt":null},
             "rules":{"trackedStats":["minecraft:mined/minecraft:diamond_ore"],
                      "integrity":{"allowedCommands":["home"],"allowsFlight":false},
                      "cheatCommands":[],"alwaysAllowedCommands":[]}}""";

    @BeforeEach
    void setUp() {
        api = new FakeApi();
        ui = new FakeUi();
        clock = new MutableClock();
        config = new Config();
        core = new XPackCore(config, dir.resolve("xpack.json"), new ApiClient(api), ui, Runnable::run, clock, IntegrityRules.bundled());
    }

    XPackCore.WorldContext world() {
        return new XPackCore.WorldContext(dir.resolve("world/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", false, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject());
    }

    void linkedAndJoined() {
        config.token = "jeton";
        config.tokenUuid = XPackCore.normalizeUuid(UUID);
        api.reply(201, SESSION);
        core.onJoinWorld(world());
    }

    JsonArray sentEvents() {
        return api.last("/events").body().getAsJsonArray("events");
    }

    @Test
    void lieLeCompteParCodePuisOuvreLaSession() {
        api.reply(201, "{\"deviceCode\":\"dc\",\"userCode\":\"K7Q-2MX\",\"expiresIn\":600,\"interval\":5}")
                .reply(202, "{\"status\":\"pending\"}")
                .reply(200, "{\"token\":\"jeton-mod\",\"playerId\":\"acc_1\"}")
                .reply(201, SESSION);
        core.onJoinWorld(world());
        core.tick();
        assertTrue(ui.notices.contains("xpack.link.code"));
        clock.millis += 5_000;
        core.tick(); // 202 : en attente
        assertNull(config.token);
        clock.millis += 5_000;
        core.tick(); // 200 : jeton reçu, session ouverte
        assertEquals("jeton-mod", config.token);
        assertEquals("0f9e1c2a3b4c4d5e8f60718293a4b5c6", config.tokenUuid);
        assertNotNull(api.last("/v1/sessions"));
        assertEquals(XPackCore.Status.COUNTED, core.status());
        // Le jeton est sauvegardé pour les prochains lancements.
        assertEquals("jeton-mod", Config.load(dir.resolve("xpack.json")).token);
    }

    @Test
    void envoieLesProgresParLotsAvecToast() {
        linkedAndJoined();
        core.onAdvancements(List.of("minecraft:story/root", "minecraft:recipes/misc/torch"), true, 10);
        core.onAdvancements(List.of("minecraft:story/root", "create:andesite_alloy"), false, 2_000);
        api.reply(200, "{\"accepted\":2,\"unlocked\":[{\"achievementId\":\"x/alliage\",\"name\":\"Alliage\",\"points\":10,\"rarity\":\"rare\"}],"
                + "\"integrity\":{\"status\":\"propre\",\"flaggedAt\":null}}");
        core.tick();

        JsonArray events = sentEvents();
        assertEquals(2, events.size(), "la recette est ignorée et le progrès déjà connu n'est pas renvoyé");
        JsonObject first = events.get(0).getAsJsonObject();
        assertTrue(first.get("snapshot").getAsBoolean());
        assertEquals(0, first.get("seq").getAsInt());
        assertEquals(1, events.get(1).getAsJsonObject().get("seq").getAsInt());
        assertFalse(events.get(1).getAsJsonObject().has("snapshot"));
        assertTrue(first.get("id").getAsString().startsWith("adv-"), "identifiant déterministe pour le dédoublonnage");
        assertEquals(List.of("Alliage:10:rare"), ui.toasts);
    }

    @Test
    void neSuitQueLesStatistiquesDemandeesEtSeulementSiElleChangent() {
        linkedAndJoined();
        core.onStats(Map.of("minecraft:mined/minecraft:diamond_ore", 3, "minecraft:custom/minecraft:jump", 900), 100);
        core.onStats(Map.of("minecraft:mined/minecraft:diamond_ore", 3), 200);
        core.onStats(Map.of("minecraft:mined/minecraft:diamond_ore", 5), 300);
        core.tick();
        JsonArray events = sentEvents();
        assertEquals(2, events.size());
        assertEquals(5, events.get(1).getAsJsonObject().get("value").getAsInt());
    }

    @Test
    void alerteAvantUneCommandeDeTricheEtSignaleSiLeJoueurContinue() {
        linkedAndJoined();
        List<String> resent = new ArrayList<>();
        assertTrue(core.onCommand("home base", resent::add), "commande autorisée par le modpack");
        assertTrue(core.onCommand("msg Ami salut", resent::add));
        assertFalse(core.onCommand("give @s diamond 64", resent::add), "la commande est retenue");
        assertEquals("give @s diamond 64", ui.warnedCommand);
        assertFalse(core.integrity().isFlagged(), "rien n'est signalé tant que le joueur n'a pas confirmé");

        ui.onContinue.run(); // le joueur clique « Continuer »
        assertTrue(core.integrity().isFlagged());
        assertEquals(List.of("give @s diamond 64"), resent);
        assertTrue(core.onCommand("give @s diamond 64", resent::add), "la commande renvoyée passe une fois");
        assertTrue(core.onCommand("give @s dirt", resent::add), "monde déjà signalé : plus d'alerte");

        core.tick();
        JsonObject integrity = sentEvents().get(0).getAsJsonObject();
        assertEquals("integrity", integrity.get("type").getAsString());
        assertEquals("command", integrity.get("reason").getAsString());
        assertEquals("/give @s diamond 64", integrity.get("detail").getAsString());
        assertEquals(XPackCore.Status.NOT_COUNTED, core.status());
    }

    @Test
    void signaleLeModeCreatifEtLeGodmode() {
        linkedAndJoined();
        core.onPlayerState(new XPackCore.PlayerState("survival", false, false), 10);
        assertFalse(core.integrity().isFlagged());
        core.onPlayerState(new XPackCore.PlayerState("creative", true, true), 20);
        assertTrue(core.integrity().isFlagged());
        assertEquals(List.of("xpack.flagged.creative"), ui.notices);

        // Autre monde : vol en survie interdit par le modpack.
        api.reply(201, SESSION);
        core.onJoinWorld(new XPackCore.WorldContext(dir.resolve("world2/xpack.json"), "singleplayer", "2026-10-02T10:00:00Z", false, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject()));
        assertFalse(core.integrity().isFlagged());
        core.onPlayerState(new XPackCore.PlayerState("survival", false, true), 30);
        assertTrue(core.integrity().isFlagged());
    }

    @Test
    void leStatutSignaleSurvitAuRedemarrageEtLesEvenementsNonEnvoyesAussi() throws Exception {
        linkedAndJoined();
        core.onAdvancements(List.of("create:andesite_alloy"), false, 100);
        assertFalse(core.onCommand("gamemode creative", c -> {}));
        ui.onContinue.run();
        // Serveur injoignable : rien n'est perdu.
        api.reply(0, "{}").reply(0, "{}"); // l'envoi périodique puis celui de la sortie du monde échouent
        core.tick();
        core.onLeaveWorld();
        JsonObject saved = JsonParser.parseString(Files.readString(dir.resolve("world/xpack.json"))).getAsJsonObject();
        assertEquals("signale", saved.get("status").getAsString());
        assertEquals(2, saved.getAsJsonArray("pending").size());

        // Relance du jeu : même monde, même identifiant, statut et file restaurés.
        XPackCore restarted = new XPackCore(config, dir.resolve("xpack.json"), new ApiClient(api), ui, Runnable::run, clock, IntegrityRules.bundled());
        api.reply(201, SESSION);
        restarted.onJoinWorld(world());
        assertTrue(restarted.integrity().isFlagged());
        clock.millis += 10_000;
        restarted.tick();
        JsonArray resent = sentEvents();
        assertEquals(2, resent.size());
        assertEquals(saved.get("worldId").getAsString(), api.last("/v1/sessions").body().getAsJsonObject("world").get("id").getAsString());
    }

    @Test
    void reessaieAvecUnDelaiCroissantPuisOublieUnJetonRevoque() {
        linkedAndJoined();
        core.onAdvancements(List.of("a:b"), false, 1);
        api.reply(503, "{}");
        core.tick();
        assertEquals(XPackCore.Status.OFFLINE, core.status());
        int calls = api.calls.size();
        clock.millis += 5_000;
        core.tick();
        assertEquals(calls, api.calls.size(), "délai doublé : pas de nouvel essai après 5 s");
        clock.millis += 6_000;
        api.reply(401, "{}");
        core.tick();
        assertNull(config.token);
        assertTrue(ui.notices.contains("xpack.link.revoked"));
    }

    @Test
    void nEnvoieRienPourUnAutreCompteMinecraft() {
        config.token = "jeton";
        config.tokenUuid = "11111111222233334444555555555555";
        core.onJoinWorld(world());
        assertTrue(ui.notices.contains("xpack.uuid_mismatch"));
        assertTrue(core.onCommand("give @s diamond", c -> {}), "pas d'alerte : ce compte n'est pas suivi");
        core.onAdvancements(List.of("a:b"), false, 1);
        core.tick();
        assertNull(api.last("/events"));
        assertEquals(XPackCore.Status.DISABLED, core.status());
    }

    @SuppressWarnings("unused")
    private static List<String> keys(JsonArray events) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : events) out.add(e.getAsJsonObject().get("key").getAsString());
        return out;
    }
}
