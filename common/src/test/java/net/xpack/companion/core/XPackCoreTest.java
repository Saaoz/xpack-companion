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
        java.util.function.Consumer<Boolean> consent;
        int consentAsked;

        @Override
        public void askConsent(java.util.function.Consumer<Boolean> answer) {
            consentAsked++;
            consent = answer;
        }

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
    Credentials creds;
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
        config.consented = true;
        creds = new Credentials(dir.resolve("credentials.json"));
        core = new XPackCore(config, dir.resolve("xpack.json"), creds, new ApiClient(api), ui, Runnable::run, clock, IntegrityRules.bundled());
    }

    XPackCore.WorldContext world() {
        return new XPackCore.WorldContext(dir.resolve("world/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", false, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), false);
    }

    void linkedAndJoined() {
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
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
        assertFalse(core.isLinked(UUID));
        clock.millis += 5_000;
        core.tick(); // 200 : jeton reçu, session ouverte
        assertTrue(core.isLinked(UUID));
        assertNotNull(api.last("/v1/sessions"));
        assertEquals("jeton-mod", api.last("/v1/sessions").token());
        assertEquals(XPackCore.Status.COUNTED, core.status());
        // Sauvegardé pour les prochains lancements… mais jamais dans config/ (exporté avec les modpacks).
        assertEquals("jeton-mod", new Credentials(dir.resolve("credentials.json")).token(config.apiUrl, UUID));
        core.saveConfig();
        assertFalse(readFile("xpack.json").contains("jeton-mod"));
        assertNull(core.linkState());
    }

    String readFile(String name) {
        try {
            return Files.readString(dir.resolve(name));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void deplaceUnAncienJetonHorsDeLaConfig() {
        Config old = new Config();
        old.consented = true;
        old.token = "ancien-jeton";
        old.tokenUuid = XPackCore.normalizeUuid(UUID);
        old.save(dir.resolve("old.json"));
        Config loaded = Config.load(dir.resolve("old.json"));
        new XPackCore(loaded, dir.resolve("old.json"), creds, new ApiClient(api), ui, Runnable::run, clock, IntegrityRules.bundled());
        assertEquals("ancien-jeton", creds.token(loaded.apiUrl, UUID));
        assertFalse(readFile("old.json").contains("ancien-jeton"), "le jeton a quitté config/xpack.json");
        assertFalse(readFile("old.json").contains("tokenUuid"));
    }

    @Test
    void lieSonCompteDepuisLesReglagesSansEtreEnJeu() {
        api.reply(201, "{\"deviceCode\":\"dc\",\"userCode\":\"K7Q-2MX\",\"expiresIn\":600,\"interval\":5}")
                .reply(200, "{\"token\":\"jeton-mod\",\"playerId\":\"acc_1\"}");
        core.startLinking(UUID, "Steve");
        core.tick();
        XPackCore.LinkState state = core.linkState();
        assertEquals("K7Q-2MX", state.userCode());
        assertEquals("https://xpack.saaoz.fr/link?code=K7Q-2MX", state.url(), "code prérempli sur le site");
        clock.millis += 5_000;
        core.tick();
        assertTrue(core.isLinked(UUID));
        assertEquals(List.of("Steve"), core.linkedNames());
        assertNull(api.last("/v1/sessions"), "pas de monde : pas de session");
    }

    @Test
    void unCodeExpireNeRelancePasLaLiaisonToutSeul() {
        api.reply(201, "{\"deviceCode\":\"dc\",\"userCode\":\"K7Q-2MX\",\"expiresIn\":600,\"interval\":5}").reply(410, "{\"error\":\"expired\"}");
        core.onJoinWorld(world());
        core.tick();
        clock.millis += 5_000;
        core.tick();
        assertTrue(ui.notices.contains("xpack.link.expired"));
        int calls = api.calls.size();
        for (int i = 0; i < 5; i++) {
            clock.millis += 600_000;
            core.tick();
        }
        assertEquals(calls, api.calls.size(), "aucun nouveau code sans action du joueur");
        assertEquals(1, ui.notices.stream().filter("xpack.link.code"::equals).count());
    }

    @Test
    void delierRevoqueLeJetonEtArreteLEnvoi() {
        linkedAndJoined();
        core.unlink(UUID);
        assertFalse(core.isLinked(UUID));
        assertEquals("DELETE", api.last("/v1/link").method());
        assertEquals("jeton", api.last("/v1/link").token());
        assertEquals(XPackCore.Status.UNLINKED, core.status());
    }

    // ------------------------------------------------------------------ mondes de test

    XPackCore.WorldContext creativeWorld(String folder) {
        return new XPackCore.WorldContext(dir.resolve(folder + "/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", true, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), true);
    }

    @Test
    void unNouveauMondeEnCreatifEstUnMondeDeTestOuRienNEstEnvoye() {
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        core.onJoinWorld(creativeWorld("test"));
        assertEquals(XPackCore.Status.PRACTICE, core.status());
        assertTrue(ui.notices.contains("xpack.practice.auto"));
        core.onAdvancements(List.of("minecraft:story/root", "minecraft:end/kill_dragon"), false, 10);
        assertTrue(core.onCommand("give @s diamond 64", c -> {}), "pas d'alerte dans un monde de test");
        core.onPlayerState(new XPackCore.PlayerState("creative", true, true), 20);
        clock.millis += 10_000;
        core.tick();
        assertTrue(api.calls.isEmpty(), "ni session, ni événement, ni signalement");
        assertFalse(core.integrity().isFlagged(), "un monde de test n'est pas un monde signalé");

        // Repassé en survie : il reste un monde de test (sinon on préparerait tout en créatif).
        core.onLeaveWorld();
        core.onJoinWorld(new XPackCore.WorldContext(dir.resolve("test/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", true, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), false));
        assertEquals(XPackCore.Status.PRACTICE, core.status());
        assertEquals(1, ui.notices.stream().filter("xpack.practice.auto"::equals).count(), "prévenu une seule fois");
        assertTrue(api.calls.isEmpty());
    }

    @Test
    void unMondeDejaSuiviQuiPasseEnCreatifResteSuiviEtEstSignale() {
        linkedAndJoined();
        core.onLeaveWorld();
        api.reply(201, SESSION);
        core.onJoinWorld(new XPackCore.WorldContext(dir.resolve("world/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", false, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), true));
        assertEquals(XPackCore.Status.COUNTED, core.status());
        core.onPlayerState(new XPackCore.PlayerState("creative", false, true), 30);
        assertTrue(core.integrity().isFlagged());
    }

    @Test
    void leJoueurPeutDeclarerUnMondeDeTest() {
        linkedAndJoined();
        core.onAdvancements(List.of("create:andesite_alloy"), false, 100);
        core.markWorldPractice();
        assertEquals(1, sentEvents().size(), "ce qui était en file part avant");
        int calls = api.calls.size();
        core.onAdvancements(List.of("minecraft:end/kill_dragon"), false, 200);
        clock.millis += 10_000;
        core.tick();
        assertEquals(calls, api.calls.size(), "plus rien ensuite");
        assertEquals(XPackCore.Status.PRACTICE, core.status());
        assertTrue(ui.notices.contains("xpack.practice.manual"));
        assertTrue(readFile("world/xpack.json").contains("\"practice\": true"), "mémorisé dans le monde");
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
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), false));
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
        XPackCore restarted = new XPackCore(config, dir.resolve("xpack.json"), creds, new ApiClient(api), ui, Runnable::run, clock, IntegrityRules.bundled());
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
        assertFalse(core.isLinked(UUID));
        assertTrue(ui.notices.contains("xpack.link.revoked"));
    }

    @Test
    void lesJetonsDesAutresComptesMinecraftNeSontJamaisUtilises() {
        creds.put(config.apiUrl, "11111111222233334444555555555555", "Alex", "jeton-alex");
        api.reply(201, "{\"deviceCode\":\"dc\",\"userCode\":\"K7Q-2MX\",\"expiresIn\":600,\"interval\":5}");
        core.onJoinWorld(world());
        assertTrue(core.onCommand("give @s diamond", c -> {}), "pas d'alerte : ce compte n'est pas suivi");
        core.onAdvancements(List.of("a:b"), false, 1);
        core.tick();
        assertNull(api.last("/events"));
        assertNull(api.last("/v1/sessions"));
        assertTrue(api.calls.stream().noneMatch(c -> "jeton-alex".equals(c.token())));
        // Ce compte-ci se voit proposer sa propre liaison.
        assertEquals(XPackCore.normalizeUuid(UUID), api.last("/v1/link/start").body().get("minecraftUuid").getAsString());
        assertEquals(XPackCore.Status.UNLINKED, core.status());
    }

    @SuppressWarnings("unused")
    private static List<String> keys(JsonArray events) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : events) out.add(e.getAsJsonObject().get("key").getAsString());
        return out;
    }

    // ------------------------------------------------------------------ #40, #46, #50

    XPackCore.WorldContext cheatsWorld() {
        return new XPackCore.WorldContext(dir.resolve("world2/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", true, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), false);
    }

    @Test
    void demandeLeConsentementAvantDEnvoyerQuoiQueCeSoit() {
        config.consented = null;
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        core.onJoinWorld(world());
        core.tick();
        assertEquals(1, ui.consentAsked);
        assertTrue(api.calls.isEmpty(), "aucune requête sans consentement");
        assertEquals(XPackCore.Status.DISABLED, core.status());

        api.reply(201, SESSION);
        ui.consent.accept(true);
        assertNotNull(api.last("/v1/sessions"), "la session s'ouvre dès l'accord");
        assertEquals(Boolean.TRUE, Config.load(dir.resolve("xpack.json")).consented);
    }

    @Test
    void unRefusEstRetenuEtNeRedemandePas() {
        config.consented = null;
        core.onJoinWorld(world());
        ui.consent.accept(false);
        core.onJoinWorld(world());
        core.tick();
        assertEquals(1, ui.consentAsked);
        assertEquals(Boolean.FALSE, Config.load(dir.resolve("xpack.json")).consented);
        assertTrue(api.calls.isEmpty());
    }

    @Test
    void informeUneSeuleFoisQueLesTrichesSontActiveesSansSignaler() {
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        api.reply(201, SESSION);
        core.onJoinWorld(cheatsWorld());
        assertEquals(1, ui.notices.stream().filter("xpack.cheats_enabled"::equals).count());
        assertFalse(core.integrity().isFlagged(), "seule l'utilisation d'une triche compte");
        api.reply(201, SESSION);
        core.onJoinWorld(cheatsWorld());
        assertEquals(1, ui.notices.stream().filter("xpack.cheats_enabled"::equals).count(), "mémorisé dans le fichier du monde");
        core.onJoinWorld(world());
        assertEquals(1, ui.notices.stream().filter("xpack.cheats_enabled"::equals).count(), "monde sans triches : rien");
    }

    @Test
    void signaleUnModDeTricheSaufSiLeModpackLAutorise() {
        core.setLoadedMods(List.of("minecraft", "sodium", "Meteor-Client"));
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        // Le modpack fournit volontairement meteor-client : le serveur le retire de la liste.
        api.reply(201, SESSION.replace("\"sessionId\":\"s_1\",", "\"sessionId\":\"s_1\",\"blacklist\":{\"version\":1,\"modIds\":[\"wurst\"]},"));
        core.onJoinWorld(world());
        assertFalse(core.integrity().isFlagged());

        // Autre modpack : liste complète.
        api.reply(201, SESSION.replace("\"sessionId\":\"s_1\",", "\"sessionId\":\"s_2\",\"blacklist\":{\"version\":1,\"modIds\":[\"meteor-client\",\"wurst\"]},"));
        core.onJoinWorld(cheatsWorld());
        assertTrue(core.integrity().isFlagged());
        assertTrue(ui.notices.contains("xpack.flagged.blacklist"));
        core.tick();
        JsonObject flagged = sentEvents().get(0).getAsJsonObject();
        assertEquals("integrity", flagged.get("type").getAsString());
        assertEquals("blacklisted_mod", flagged.get("reason").getAsString());
        assertEquals("meteor-client", flagged.get("detail").getAsString());
    }

    @Test
    void sansListeDuServeurUtiliseLaListeEmbarquee() {
        core.setLoadedMods(List.of("baritone"));
        linkedAndJoined();
        assertTrue(core.integrity().isFlagged());
    }
}
