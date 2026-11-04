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

        final List<String> cheatModes = new ArrayList<>();
        Runnable onDisableMode;
        Runnable onKeepMode;

        @Override
        public void showCheatModeWarning(String mode, Runnable onDisable, Runnable onContinue) {
            cheatModes.add(mode);
            onDisableMode = onDisable;
            onKeepMode = onContinue;
        }

        @Override
        public void toastUnlocked(Ui.Unlock u) {
            toasts.add(u.name + ":" + u.points + ":" + u.rarity + (u.item != null ? ":" + u.item + ":" + u.shownPercent() + ":" + u.difficulty : ""));
        }

        @Override
        public void toastCompleted(String modpackName, int achievements, long playtimeTicks) {
            toasts.add("100%:" + modpackName + ":" + achievements + ":" + playtimeTicks);
        }

        @Override
        public void toastMilestone(String modpackName, int tier, int achievements, int total) {
            toasts.add("palier:" + tier + ":" + achievements + "/" + total);
        }

        final List<String> alerts = new ArrayList<>();

        @Override
        public void notify(String key, Object... args) {
            notices.add(key);
        }

        @Override
        public void integrityAlert(String key, Object... args) {
            notices.add(key);
            alerts.add(key);
        }

        final List<String> presenceReports = new ArrayList<>();

        @Override
        public void presenceReport(boolean selfLinked, List<Presence.Player> players) {
            presenceReports.add(selfLinked + ":" + players);
        }

        @Override
        public void teamNotice(String key, Object... args) {
            notices.add(key + (args.length > 0 ? ":" + args[0] : ""));
        }

        final List<String> difficultyWarnings = new ArrayList<>();

        @Override
        public void difficultyWarning(String from, String to, boolean canRevert) {
            difficultyWarnings.add(from + ">" + to + ":" + canRevert);
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
        // Compte non lié : un rappel vers les réglages, mais ni code ni lien envoyés d'office.
        assertEquals(List.of("xpack.link.hint"), ui.notices);
        assertTrue(api.calls.isEmpty());
        core.onLeaveWorld();
        core.onJoinWorld(world());
        assertEquals(1, ui.notices.stream().filter("xpack.link.hint"::equals).count(), "un seul rappel par lancement");

        core.startLinking(UUID, "Steve"); // bouton « Lier mon compte » des réglages
        core.tick();
        assertEquals("K7Q-2MX", core.linkState().userCode(), "le code s'affiche dans les réglages");
        assertFalse(ui.notices.contains("xpack.link.code"), "pas dans le chat");
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
        core.startLinking(UUID, "Steve");
        core.tick();
        clock.millis += 5_000;
        core.tick();
        assertTrue(ui.notices.contains("xpack.link.expired"));
        assertNull(core.linkState());
        int calls = api.calls.size();
        for (int i = 0; i < 5; i++) {
            clock.millis += 600_000;
            core.tick();
        }
        assertEquals(calls, api.calls.size(), "aucun nouveau code sans action du joueur");
        assertEquals(2, calls, "un seul code demandé, puis un seul poll");
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
        core.onPlayerState(new XPackCore.PlayerState("creative", true), 20);
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
        core.onPlayerState(new XPackCore.PlayerState("creative", false), 30);
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
    void leTempsDeJeuEnvoyeNeReculeJamaisPendantUneSession() {
        // ATM10 au chargement : l'horloge du client prend de l'avance (34) puis le serveur la recale (19).
        linkedAndJoined();
        core.onAdvancements(List.of("tombstone:event/root"), false, 34);
        core.onStats(Map.of("minecraft:custom/minecraft:play_time", 19), 19);
        core.onStats(Map.of("minecraft:custom/minecraft:play_time", 1_134), 1_134);
        core.tick();

        List<Long> ticks = new ArrayList<>();
        for (var e : sentEvents()) ticks.add(e.getAsJsonObject().get("playtimeTicks").getAsLong());
        for (int i = 1; i < ticks.size(); i++) assertTrue(ticks.get(i) >= ticks.get(i - 1), "recul envoyé : " + ticks);

        // Un autre monde repart de son propre temps, pas de celui du précédent.
        core.onLeaveWorld();
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        core.onAdvancements(List.of("tombstone:event/root", "minecraft:story/mine_stone"), false, 10);
        core.tick();
        JsonArray next = sentEvents();
        assertEquals(10L, next.get(next.size() - 1).getAsJsonObject().get("playtimeTicks").getAsLong());
    }

    @Test
    void envoieLaDifficulteEtPrevientUneSeuleFoisQuandElleBaisse() {
        linkedAndJoined();
        core.onDifficulty("hard", true, 100);
        core.onDifficulty("hard", true, 200); // inchangée : rien
        core.onDifficulty("easy", true, 3_000);
        core.onDifficulty("hard", true, 4_000);
        core.onDifficulty("peaceful", true, 5_000); // nouvelle baisse : déjà prévenu dans ce monde
        core.onDifficulty("inconnue", true, 6_000);
        core.tick();

        List<String> sent = new ArrayList<>();
        for (var e : sentEvents()) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("type").getAsString().equals("difficulty")) sent.add(o.get("key").getAsString());
        }
        assertEquals(List.of("hard", "easy", "hard", "peaceful"), sent);
        assertEquals(List.of("hard>easy:true"), ui.difficultyWarnings);
        assertTrue(readFile("world/xpack.json").contains("\"difficultyWarned\": true"), "mémorisé dans le monde");
    }

    @Test
    void marqueLesProgresObtenusAvecDAutresJoueursCommeEquipe() {
        linkedAndJoined();
        core.onAdvancements(List.of("minecraft:story/root"), false, 100);
        core.setOthersOnline(true);
        core.onAdvancements(List.of("minecraft:story/mine_stone"), false, 200);
        core.onStats(java.util.Map.of("minecraft:custom/minecraft:play_time", 300), 300);
        core.onDifficulty("hard", true, 300);
        core.tick();
        JsonArray events = sentEvents();
        assertFalse(events.get(0).getAsJsonObject().has("team"), "seul : pas d'équipe");
        assertTrue(events.get(1).getAsJsonObject().get("team").getAsBoolean());
        for (var e : events) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("type").getAsString().equals("difficulty")) assertFalse(o.has("team"), "la difficulté ne porte pas d'équipe");
        }
    }

    @Test
    void noteLaProvenanceDesObjetsDeValeurRecus() {
        linkedAndJoined();
        core.setQuestRewards(java.util.Set.of("minecraft:pig_spawn_egg"));
        var none = new ItemArrivals.Context(null, false);
        core.onInventory(Map.of("minecraft:dirt", 3), none, 100); // point de départ : rien n'est noté
        core.onItemPickup("minecraft:diamond_block", 2);
        core.onInventory(Map.of("minecraft:dirt", 4, "minecraft:diamond_block", 2), none, 120);
        core.onInventory(Map.of("minecraft:dirt", 4, "minecraft:diamond_block", 2, "minecraft:pig_spawn_egg", 1), new ItemArrivals.Context("QuestScreen", false), 140);
        core.tick();
        JsonArray events = sentEvents();
        assertEquals(2, events.size(), "la terre n'est pas un objet de valeur");
        JsonObject picked = events.get(0).getAsJsonObject();
        assertEquals("item", picked.get("type").getAsString());
        assertEquals("minecraft:diamond_block", picked.get("key").getAsString());
        assertEquals(2, picked.get("value").getAsInt());
        assertEquals("pickup", picked.get("via").getAsString());
        JsonObject egg = events.get(1).getAsJsonObject();
        assertEquals("screen", egg.get("via").getAsString());
        assertEquals("QuestScreen", egg.get("screen").getAsString());
        assertTrue(egg.get("quest").getAsBoolean());
    }

    @Test
    void envoieLesPacksDeTexturesEtLeMinageQuandIlsChangent() {
        linkedAndJoined();
        core.onResourcePacks(List.of("XRay_Ultimate.zip", "Faithful"), 100);
        core.onResourcePacks(List.of("Faithful", "XRay_Ultimate.zip"), 110); // même choix : rien de nouveau
        core.onBlockBroken("minecraft:diamond_ore", "minecraft:iron_pickaxe");
        Map<String, Integer> stats = Map.of("minecraft:mined/minecraft:diamond_ore", 3, "minecraft:mined/minecraft:stone", 200);
        core.onStats(stats, 1_200);
        core.onStats(stats, 2_400); // inchangé : pas de nouvel envoi
        core.tick();
        JsonArray events = sentEvents();
        JsonObject packs = events.get(0).getAsJsonObject();
        assertEquals("resource_packs", packs.get("type").getAsString());
        assertEquals("[\"Faithful\",\"XRay_Ultimate.zip\"]", packs.get("packs").toString());
        JsonObject mined = null;
        int miningEvents = 0;
        for (var e : events) {
            if (!e.getAsJsonObject().get("type").getAsString().equals("mining")) continue;
            mined = e.getAsJsonObject();
            miningEvents++;
        }
        assertEquals(1, miningEvents);
        assertEquals(3, mined.getAsJsonObject("ores").get("minecraft:diamond_ore").getAsInt());
        assertEquals(200, mined.get("stone").getAsInt());
        assertEquals(1, mined.getAsJsonObject("hand").get("minecraft:diamond_ore").getAsInt());
        assertEquals("[\"minecraft:iron_pickaxe\"]", mined.get("tools").toString());
    }

    @Test
    void unMondeCommenceSansLeModNeComptePasEtLeDitUneFois() {
        linkedAndJoined();
        core.onAdvancements(List.of("minecraft:story/root"), false, 200_000);
        api.reply(200, "{\"accepted\":1,\"unlocked\":[],\"world\":{\"startedWithoutMod\":true,\"difficulty\":null}}");
        core.tick();
        assertEquals(XPackCore.Status.STARTED_LATE, core.status());
        assertEquals(1, ui.notices.stream().filter("xpack.started_late"::equals).count());
        // Revenir dans le monde : toujours non compté, sans répéter le message.
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        assertEquals(XPackCore.Status.STARTED_LATE, core.status());
        assertEquals(1, ui.notices.stream().filter("xpack.started_late"::equals).count());
    }

    @Test
    void envoieLesProgresParLotsAvecToast() {
        linkedAndJoined();
        core.onAdvancements(List.of("minecraft:story/root", "minecraft:recipes/misc/torch"), true, 10);
        core.onAdvancements(List.of("minecraft:story/root", "create:andesite_alloy"), false, 2_000);
        api.reply(200, "{\"accepted\":2,\"unlocked\":[{\"achievementId\":\"x/alliage\",\"name\":\"Alliage\",\"points\":10,\"rarity\":\"rare\"},{\"achievementId\":\"x/diamants\",\"name\":\"Diamants\",\"points\":19,\"rarity\":\"epique\",\"item\":\"minecraft:diamond\",\"percent\":0.4,\"difficulty\":\"hard\"}],"
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
        assertEquals(List.of("Alliage:10:rare", "Diamants:19:epique:minecraft:diamond:0:hard"), ui.toasts, "ancien serveur, puis objet, part des joueurs et difficulté");
    }

    @Test
    void annonceLeModpackTermineACentPourCent() {
        linkedAndJoined();
        core.onAdvancements(List.of("create:andesite_alloy"), false, 2_000);
        api.reply(200, "{\"accepted\":1,\"unlocked\":[{\"achievementId\":\"x/alliage\",\"name\":\"Alliage\",\"points\":10,\"rarity\":\"rare\"}],"
                + "\"completed\":{\"version\":\"1.2\",\"achievements\":42,\"playtimeTicks\":720000},\"integrity\":{\"status\":\"propre\",\"flaggedAt\":null}}");
        core.tick();
        assertEquals(2, ui.toasts.size());
        assertTrue(ui.toasts.get(1).startsWith("100%:"), "le toast du 100 % suit celui du succès");
        assertTrue(ui.toasts.get(1).endsWith(":42:720000"));
    }

    @Test
    void annonceUnPalierDeCompletion() {
        linkedAndJoined();
        core.onAdvancements(List.of("create:andesite_alloy"), false, 2_000);
        api.reply(200, "{\"accepted\":1,\"unlocked\":[{\"achievementId\":\"x/alliage\",\"name\":\"Alliage\",\"points\":10,\"rarity\":\"rare\"}],"
                + "\"completed\":null,\"milestone\":{\"version\":\"1.2\",\"tier\":50,\"achievements\":21,\"total\":42,\"playtimeTicks\":720000},\"integrity\":{\"status\":\"propre\",\"flaggedAt\":null}}");
        core.tick();
        assertEquals(List.of("Alliage:10:rare", "palier:50:21/42"), ui.toasts);
    }

    @Test
    void neSuitQueLesStatistiquesDemandeesEtSeulementSiElleChangent() {
        linkedAndJoined();
        core.onStats(Map.of("minecraft:mined/minecraft:diamond_ore", 3, "minecraft:custom/minecraft:jump", 900), 100);
        core.onStats(Map.of("minecraft:mined/minecraft:diamond_ore", 3), 200);
        core.onStats(Map.of("minecraft:mined/minecraft:diamond_ore", 5), 300);
        core.tick();
        List<JsonObject> events = new ArrayList<>();
        for (var e : sentEvents()) if (e.getAsJsonObject().get("type").getAsString().equals("stat")) events.add(e.getAsJsonObject());
        assertEquals(2, events.size(), "le résumé du minage (#129) part à part");
        assertEquals(5, events.get(1).get("value").getAsInt());
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
    void previentAvantLeModeTricheDUnModEtNeSignaleQueSiLeJoueurLeGarde() {
        linkedAndJoined();
        List<String> disabled = new ArrayList<>();
        core.onCheatModes(java.util.Set.of("jei"), disabled::add);
        assertEquals(List.of(), ui.cheatModes, "sans droits de triche, JEI ne peut rien donner");

        core.onCheatsAllowed(true);
        core.onCheatModes(java.util.Set.of("jei", "ftbquests"), disabled::add);
        core.onCheatModes(java.util.Set.of("jei", "ftbquests"), disabled::add);
        assertEquals(List.of("ftbquests"), ui.cheatModes, "une question à la fois, pas de répétition écran ouvert");
        ui.onDisableMode.run();
        assertEquals(List.of("ftbquests"), disabled);
        core.onCheatModes(java.util.Set.of("jei"), disabled::add);
        assertEquals(List.of("ftbquests", "jei"), ui.cheatModes);
        assertFalse(core.integrity().isFlagged());

        // Resté allumé malgré « Désactiver » : on redemande après quelques secondes.
        clock.millis += XPackCore.CHEAT_MODE_RECHECK_MS;
        core.onCheatModes(java.util.Set.of("ftbquests"), disabled::add);
        assertEquals(List.of("ftbquests", "jei", "ftbquests"), ui.cheatModes);
        ui.onKeepMode.run();
        assertTrue(core.integrity().isFlagged());
        assertEquals(XPackCore.Status.NOT_COUNTED, core.status());
    }

    @Test
    void signaleLeModeCreatifEtLeGodmode() {
        linkedAndJoined();
        core.onPlayerState(new XPackCore.PlayerState("survival", false), 10);
        assertFalse(core.integrity().isFlagged());
        core.onPlayerState(new XPackCore.PlayerState("creative", true), 20);
        assertTrue(core.integrity().isFlagged());
        assertEquals(List.of("xpack.flagged.creative"), ui.notices);

        // Autre monde : le vol en survie (jetpack…) n'est pas signalé (#48), l'invulnérabilité oui.
        api.reply(201, SESSION);
        core.onJoinWorld(new XPackCore.WorldContext(dir.resolve("world2/xpack.json"), "singleplayer", "2026-10-02T10:00:00Z", false, UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), false));
        assertFalse(core.integrity().isFlagged());
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 30);
        assertFalse(core.integrity().isFlagged(), "protection au spawn (AngerMod) : tolérée une minute");
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 30 + XPackCore.INVULNERABLE_GRACE_TICKS);
        assertTrue(core.integrity().isFlagged());
    }

    @Test
    void laProtectionAuSpawnDesModsNEstPasDuGodmode() {
        linkedAndJoined();
        // AngerMod (GT:NH) : quelques secondes à chaque apparition ou changement de dimension, sans fin.
        for (long t = 0; t < 10 * XPackCore.INVULNERABLE_GRACE_TICKS; t += XPackCore.INVULNERABLE_GRACE_TICKS / 3) {
            core.onPlayerState(new XPackCore.PlayerState("survival", true), t);
            core.onPlayerState(new XPackCore.PlayerState("survival", false), t + 200);
        }
        assertFalse(core.integrity().isFlagged());
        // Changer de monde ne garde pas l'invulnérabilité du monde précédent.
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 20_000);
        core.onLeaveWorld();
        linkedAndJoined();
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 20_000 + XPackCore.INVULNERABLE_GRACE_TICKS);
        assertFalse(core.integrity().isFlagged());
    }

    @Test
    void gtNewHorizonsNeSignaleJamaisLInvulnerabilite() {
        // dreamcraft : mod cœur de GT: New Horizons, où l'invulnérabilité se gagne en jeu.
        core.setLoadedMods(List.of("minecraft", "forge", "dreamcraft", "angermod"));
        linkedAndJoined();
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 0);
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 100 * XPackCore.INVULNERABLE_GRACE_TICKS);
        assertFalse(core.integrity().isFlagged());
        // Le créatif, lui, reste signalé.
        core.onPlayerState(new XPackCore.PlayerState("creative", true), 100 * XPackCore.INVULNERABLE_GRACE_TICKS + 20);
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
        // Ce compte-ci n'est pas lié : on lui rappelle les réglages, sans rien demander au serveur.
        assertTrue(ui.notices.contains("xpack.link.hint"));
        assertTrue(api.calls.isEmpty());
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
    void informeAussiQuandLesTrichesSontActiveesEnCoursDePartie() {
        linkedAndJoined(); // monde sans triches à l'entrée
        core.onCheatsAllowed(false);
        assertFalse(ui.notices.contains("xpack.cheats_enabled"));
        core.onCheatsAllowed(true); // « Ouvrir au LAN » avec les commandes
        core.onCheatsAllowed(true);
        assertEquals(1, ui.notices.stream().filter("xpack.cheats_enabled"::equals).count());
        assertFalse(core.integrity().isFlagged(), "seule l'utilisation d'une triche compte");
    }

    @Test
    void ecritLeFichierDuMondeEnArrierePlanEtNeGardeQueLaDerniereVersion() {
        List<Runnable> disk = new ArrayList<>();
        core.writeWorldFilesWith(disk::add);
        linkedAndJoined();
        core.setPlaytime(1234);
        core.onAdvancements(List.of("minecraft:story/root", "minecraft:story/mine_stone"), false, 1234);
        assertFalse(Files.exists(dir.resolve("world/xpack.json")), "rien d'écrit sur le thread de jeu");
        assertEquals(1, disk.size(), "une seule écriture pour toute la rafale");
        disk.forEach(Runnable::run);
        JsonObject saved = JsonParser.parseString(readFile("world/xpack.json")).getAsJsonObject();
        assertEquals(1234, saved.get("playtime").getAsLong());
        // À la sortie du monde, l'écriture est faite tout de suite (le monde suivant ne l'écrase pas).
        core.onLeaveWorld();
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        assertEquals(1234, core.playtimeFloor(), "plancher du temps de jeu relu depuis le fichier du monde");
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

    // ------------------------------------------------------------------ alertes et support

    @Test
    void uneDetectionAutomatiqueAvertitSansBloquerEtLeSupportRecoitLeContexte() {
        core.setLoadedMods(List.of("minecraft", "mekanism"));
        linkedAndJoined();
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 30);
        core.onPlayerState(new XPackCore.PlayerState("survival", true), 30 + XPackCore.INVULNERABLE_GRACE_TICKS);
        assertEquals(List.of("xpack.flagged.godmode"), ui.alerts, "notification avec accès au support");

        assertTrue(core.canContactSupport());
        JsonObject report = core.supportReport();
        assertEquals("NOT_COUNTED", report.get("status").getAsString());
        JsonObject detection = report.getAsJsonArray("detections").get(0).getAsJsonObject();
        assertEquals("abilities", detection.get("reason").getAsString());
        assertEquals("invulnerable", detection.get("detail").getAsString());
        assertEquals("Engrenages", report.getAsJsonObject("modpack").get("name").getAsString());
        assertTrue(report.getAsJsonArray("mods").toString().contains("mekanism"), "utile pour comprendre la détection");

        api.reply(201, "{\"id\":\"sup_1\",\"mailed\":true}");
        List<Boolean> sent = new ArrayList<>();
        core.sendSupport("  J'avais un jetpack Mekanism  ", sent::add);
        assertEquals(List.of(true), sent);
        XPackCoreTest.FakeApi.Call call = api.last("/v1/support/reports");
        assertEquals("jeton", call.token());
        assertEquals("J'avais un jetpack Mekanism", call.body().get("message").getAsString());
        assertEquals("abilities", call.body().getAsJsonObject("report").getAsJsonArray("detections").get(0).getAsJsonObject().get("reason").getAsString());
    }

    @Test
    void sansCompteLieLeSupportNePeutPasRepondre() {
        core.onJoinWorld(world());
        assertFalse(core.canContactSupport());
        List<Boolean> sent = new ArrayList<>();
        core.sendSupport("aide", sent::add);
        assertEquals(List.of(false), sent);
        assertNull(api.last("/v1/support/reports"));
    }

    // ------------------------------------------------------------------ catalogue des progrès (#80)

    static XPackCore.AdvancementInfo adv(String id, String parent, String frame) {
        return new XPackCore.AdvancementInfo(id, parent, "Titre " + id, "Description " + id, frame, "minecraft:stone", false);
    }

    @Test
    void toastDEssaiAvecLePremierSuccesDuModpackMemeToastsCoupes() {
        config.toasts = false;
        core.previewToast("Succès d'essai");
        assertEquals(List.of("Succès d'essai:5:commun"), ui.toasts, "hors partie : l'exemple");
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        core.onAdvancementCatalog(List.of(adv("farmersdelight:main/root", null, "task"), adv("minecraft:story/root", null, "task")), "fr_fr");
        int calls = api.calls.size();
        core.previewToast("Succès d'essai");
        assertEquals("Titre minecraft:story/root:5:commun:minecraft:stone:88:normal", ui.toasts.get(1), "le premier succès du jeu en priorité");
        assertEquals(calls, api.calls.size(), "rien n'est envoyé");
    }

    @Test
    void leToastAttendLeDelaiChoisiDansLesReglages() {
        config.toastDelay = 3;
        core.previewToast("A");
        core.previewToast("B");
        assertEquals(List.of(), ui.toasts);
        clock.millis += 2_999;
        core.tick();
        assertEquals(List.of(), ui.toasts);
        clock.millis += 1;
        core.tick();
        assertEquals(List.of("A:5:commun", "B:5:commun"), ui.toasts, "dans l'ordre d'arrivée");
        config.toastDelay = 99;
        assertEquals(10_000, config.toastDelayMs(), "borné à 10 s");
        assertEquals("bottom_right", config.toastPosition("bottom_right"), "rien de choisi : celui de la version");
        config.toastPosition = "top_left";
        assertEquals("top_left", config.toastPosition("bottom_right"));
    }

    @Test
    void envoieLeCatalogueDesProgresUneFoisParPartieEtSeulementSIlChange() {
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        // Le catalogue peut arriver avant la réponse de session : il part dès qu'elle est ouverte.
        List<XPackCore.AdvancementInfo> catalog = List.of(
                adv("farmersdelight:main/root", null, "task"),
                adv("farmersdelight:main/cutting_board", "farmersdelight:main/root", "goal"),
                adv("minecraft:recipes/misc/torch", null, "task"));
        core.onAdvancementCatalog(catalog, "fr_fr");
        XPackCoreTest.FakeApi.Call sent = api.last("/catalog");
        assertNotNull(sent);
        assertEquals("/v1/sessions/s_1/catalog", sent.path());
        assertEquals("fr_fr", sent.body().get("lang").getAsString());
        JsonArray list = sent.body().getAsJsonArray("advancements");
        assertEquals(2, list.size(), "les recettes ne sont pas des succès");
        JsonObject board = list.get(1).getAsJsonObject();
        assertEquals("farmersdelight:main/root", board.get("parent").getAsString());
        assertEquals("goal", board.get("frame").getAsString());
        assertEquals("Titre farmersdelight:main/cutting_board", board.get("title").getAsString());

        core.onAdvancementCatalog(catalog, "fr_fr"); // même catalogue (paquet de mise à jour) : rien
        clock.millis += 120_000;
        core.tick();
        assertEquals(1, api.calls.stream().filter(c -> c.path().endsWith("/catalog")).count(), "pas de renvoi");
        core.onAdvancementCatalog(List.of(catalog.get(0), catalog.get(1), adv("farmersdelight:main/knife", "farmersdelight:main/root", "task")), "fr_fr");
        assertEquals(3, api.last("/catalog").body().getAsJsonArray("advancements").size(), "nouveau progrès visible : renvoyé");
    }

    @Test
    void envoieLaPositionDuSuccesSurLaPageDuJeuQuandElleExiste() {
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        core.onAdvancementCatalog(List.of(adv("gt:a", null, "task").withPosition(3, -2), adv("gt:b", "gt:a", "task")), "en_us");
        JsonArray list = api.last("/catalog").body().getAsJsonArray("advancements");
        assertEquals(3, list.get(0).getAsJsonObject().get("col").getAsInt());
        assertEquals(-2, list.get(0).getAsJsonObject().get("row").getAsInt());
        assertFalse(list.get(1).getAsJsonObject().has("col"), "pas de position connue : rien d'envoyé");
    }

    @Test
    void demandeLesIconesManquantesUneFoisLeCatalogueEnvoyeEtLesEnvoieUneAUne() {
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        core.tick();
        assertNull(api.last("/icon-gaps"), "pas avant que le serveur ait le catalogue");
        core.onAdvancementCatalog(List.of(adv("mekanism:root", null, "task")), "fr_fr");
        api.reply(200, "{\"items\":[\"mekanism:jetpack\",\"create:track_signal\"]}");
        core.tick();
        XPackCoreTest.FakeApi.Call ask = api.last("/icon-gaps");
        assertNotNull(ask);
        assertEquals("GET", ask.method());
        assertEquals("/v1/sessions/s_1/icon-gaps", ask.path());
        core.tick();
        assertEquals(1, api.calls.stream().filter(c -> c.path().endsWith("/icon-gaps")).count(), "une seule demande par partie");
        assertEquals("mekanism:jetpack", core.nextIconToDraw());
        core.submitIcon("mekanism:jetpack", new byte[] {1, 2, 3});
        XPackCoreTest.FakeApi.Call sent = api.last("/icons");
        assertEquals("POST", sent.method());
        assertEquals("/v1/sessions/s_1/icons", sent.path());
        assertEquals("mekanism:jetpack", sent.body().get("item").getAsString());
        assertEquals("AQID", sent.body().get("png").getAsString());
        assertEquals("create:track_signal", core.nextIconToDraw());
        assertNull(core.nextIconToDraw());
    }

    @Test
    void leLivreDeQuetesPartAvecLesProgresEtEnPlusieursEnvoisSIlEstGros() {
        linkedAndJoined();
        core.onAdvancementCatalog(List.of(adv("minecraft:story/root", null, "task")), "en_us");
        List<XPackCore.AdvancementInfo> quests = new ArrayList<>();
        String longText = "x".repeat(590);
        for (int i = 0; i < 2_000; i++) {
            quests.add(new XPackCore.AdvancementInfo(BetterQuesting.PREFIX + i, null, "Quest " + i, longText, "task", "minecraft:stone", false, "Tier 0"));
        }
        int before = api.calls.size();
        core.onQuestCatalog(quests, "en_us");
        List<FakeApi.Call> parts = api.calls.subList(before, api.calls.size());
        assertTrue(parts.size() >= 2, "plus d'un envoi : " + parts.size());
        int total = 0;
        for (FakeApi.Call c : parts) {
            assertTrue(c.body().toString().length() < 1_000_000, "chaque envoi tient sous la limite de l'API");
            total += c.body().getAsJsonArray("advancements").size();
        }
        assertEquals(2_001, total, "progrès et quêtes, chacun une fois");
        assertEquals("Tier 0", parts.get(parts.size() - 1).body().getAsJsonArray("advancements").get(0).getAsJsonObject().get("tab").getAsString());
        core.onQuestCatalog(quests, "en_us");
        assertEquals(before + parts.size(), api.calls.size(), "même livre : rien de renvoyé");
    }

    @Test
    void pasDeQuetesPourUnModpackDe300ProgresOuPlus() {
        linkedAndJoined();
        List<XPackCore.AdvancementInfo> quests = List.of(new XPackCore.AdvancementInfo(BetterQuesting.PREFIX + "0", null, "Quest", "", "task", "minecraft:stone", false, "Tier 0"));
        List<XPackCore.AdvancementInfo> advancements = new ArrayList<>();
        for (int i = 0; i < 299; i++) advancements.add(adv("gregtech:a" + i, null, "task"));
        core.onAdvancementCatalog(advancements, "en_us");
        assertTrue(core.wantsQuests(), "299 progrès : le livre s'ajoute");
        core.onQuestCatalog(quests, "en_us");
        assertEquals(300, api.last("/catalog").body().getAsJsonArray("advancements").size());

        advancements.add(adv("gregtech:a299", null, "task"));
        core.onAdvancementCatalog(advancements, "en_us");
        assertFalse(core.wantsQuests(), "300 progrès : les progrès suffisent");
        JsonArray sent = api.last("/catalog").body().getAsJsonArray("advancements");
        assertEquals(300, sent.size());
        for (var a : sent) assertFalse(a.getAsJsonObject().get("id").getAsString().startsWith(BetterQuesting.PREFIX));
        core.onQuests(Map.of(BetterQuesting.PREFIX + "0", 1_000L), 2_000, 10);
        clock.millis += 10_000;
        core.tick();
        for (FakeApi.Call c : api.calls) {
            if (c.path().endsWith("/events")) assertFalse(c.body().toString().contains(BetterQuesting.PREFIX), "quête ignorée");
        }
    }

    @Test
    void lesQuetesTermineesAvantLeModSontUnInstantaneEtCellesDeLaFinDePartieComptent() {
        linkedAndJoined();
        // Première partie : instantané, puis une quête finie en jeu.
        core.onQuests(Map.of(BetterQuesting.PREFIX + "0", 1_000L), 2_000, 10);
        core.onQuests(Map.of(BetterQuesting.PREFIX + "0", 1_000L, BetterQuesting.PREFIX + "1", 5_000L), 6_000, 20);
        clock.millis += 10_000;
        core.tick();
        List<String> events = new ArrayList<>();
        for (FakeApi.Call c : api.calls) {
            if (!c.path().endsWith("/events")) continue;
            for (var e : c.body().getAsJsonArray("events")) {
                JsonObject o = e.getAsJsonObject();
                if (o.get("type").getAsString().equals("advancement")) events.add(o.get("key").getAsString() + (o.has("snapshot") ? ":instantane" : ""));
            }
        }
        assertEquals(List.of("betterquesting:quest/0:instantane", "betterquesting:quest/1"), events);

        // Quête finie juste avant de quitter (après la dernière sauvegarde lue) : elle compte à la
        // partie suivante. Celle finie après la sortie (sans le mod) reste un instantané.
        long left = clock.millis();
        core.onLeaveWorld();
        api.calls.clear();
        api.reply(201, SESSION);
        core.onJoinWorld(world());
        core.onQuests(Map.of(BetterQuesting.PREFIX + "0", 1_000L, BetterQuesting.PREFIX + "1", 5_000L, BetterQuesting.PREFIX + "2", left - 1, BetterQuesting.PREFIX + "3", left + 60_000), left + 60_000, 30);
        clock.millis += 10_000;
        core.tick();
        List<String> next = new ArrayList<>();
        for (var e : sentEvents()) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("type").getAsString().equals("advancement")) next.add(o.get("key").getAsString() + (o.has("snapshot") ? ":instantane" : ""));
        }
        assertTrue(next.contains("betterquesting:quest/2"), next.toString());
        assertTrue(next.contains("betterquesting:quest/3:instantane"), next.toString());
        assertFalse(next.contains("betterquesting:quest/2:instantane"), next.toString());
    }

    @Test
    void pasDeCatalogueDansUnMondeDeTest() {
        creds.put(config.apiUrl, UUID, "Steve", "jeton");
        core.onJoinWorld(creativeWorld("test2"));
        core.onAdvancementCatalog(List.of(adv("minecraft:story/root", null, "task")), "fr_fr");
        assertNull(api.last("/catalog"));
    }

    // ------------------------------------------------------------------ jeu en groupe (#126)

    static final String ALEX = "11111111-1111-4111-8111-111111111111";
    static final String SAM = "22222222-2222-4222-8222-222222222222";

    @Test
    void envoieLaPresenceChaqueMinuteSansUuidEnClair() {
        linkedAndJoined();
        core.tick();
        assertNull(api.last("/v1/presence"), "seul : rien à envoyer");
        core.setOtherPlayers(Map.of(ALEX, "Alex", SAM, "Sam"));
        core.tick();
        var call = api.last("/v1/presence");
        assertNotNull(call);
        assertEquals("jeton", call.token());
        assertFalse(call.body().has("uuidHash"), "compte lié : le serveur connaît déjà l'UUID");
        List<String> others = new ArrayList<>();
        call.body().getAsJsonArray("others").forEach(e -> others.add(e.getAsString()));
        assertEquals(List.of(Presence.hash(ALEX), Presence.hash(SAM)).stream().sorted().toList(), others.stream().sorted().toList());
        assertFalse(call.body().toString().contains(ALEX), "jamais d'UUID en clair");

        int sent = (int) api.calls.stream().filter(c -> c.path().equals("/v1/presence")).count();
        clock.millis += 30_000;
        core.tick();
        assertEquals(sent, api.calls.stream().filter(c -> c.path().equals("/v1/presence")).count(), "pas avant une minute");
        clock.millis += 31_000;
        core.tick();
        assertEquals(sent + 1, api.calls.stream().filter(c -> c.path().equals("/v1/presence")).count());
    }

    @Test
    void sansCompteLieLaPresencePorteLEmpreinteDuJoueur() {
        core.onJoinWorld(world());
        core.setOtherPlayers(Map.of(ALEX, "Alex"));
        core.tick();
        var call = api.last("/v1/presence");
        assertNull(call.token());
        assertEquals(Presence.hash(UUID), call.body().get("uuidHash").getAsString());
        // Même calcul que le serveur : sans tirets, en minuscules.
        assertEquals(Presence.hash(UUID.replace("-", "").toUpperCase()), Presence.hash(UUID));
    }

    @Test
    void xpackCheckDitQuiALeMod() {
        linkedAndJoined();
        core.setOtherPlayers(Map.of(ALEX, "Alex", SAM, "Sam"));
        api.reply(200, "{\"players\":{\"" + Presence.hash(ALEX) + "\":\"linked\",\"" + Presence.hash(SAM) + "\":\"unknown\"}}");
        core.checkPresence();
        assertEquals(List.of("true:[Player[name=Alex, status=LINKED], Player[name=Sam, status=UNKNOWN]]"), ui.presenceReports);
        // Réponse récente : pas de nouvelle requête.
        int calls = api.calls.size();
        core.checkPresence();
        assertEquals(calls, api.calls.size());
        assertEquals(2, ui.presenceReports.size());
    }

    @Test
    void xpackCheckSeulOuHorsLigne() {
        core.checkPresence();
        assertEquals(List.of("xpack.check.no_world"), ui.notices);
        linkedAndJoined();
        core.checkPresence();
        assertTrue(ui.notices.contains("xpack.check.alone"), "seul et lié : on le dit");
        core.setOtherPlayers(Map.of(ALEX, "Alex"));
        api.reply(503, "{}");
        core.checkPresence();
        assertTrue(ui.notices.contains("xpack.check.offline"));
        assertTrue(ui.presenceReports.isEmpty());
    }

    // ------------------------------------------------------------------ équipe XPack (#145)

    static final String NO_TEAM = "\"team\":{\"team\":null,\"invites\":[],\"invited\":[]}";

    private long count(String notice) {
        return ui.notices.stream().filter(notice::equals).count();
    }

    @Test
    void proposeLEquipeUneFoisParJourQuandDAutresJoueursXPackSontLa() {
        linkedAndJoined();
        // Un joueur sans le mod : rien à proposer.
        core.setOtherPlayers(Map.of(SAM, "Sam"));
        api.reply(200, "{\"players\":{\"" + Presence.hash(SAM) + "\":\"unknown\"}," + NO_TEAM + "}");
        core.tick();
        assertEquals(0, count("xpack.team.suggest"));
        var call = api.last("/v1/presence");
        assertEquals("s_1", call.body().get("sessionId").getAsString(), "la session sert à retrouver le modpack de l'équipe");
        // Alex a le mod lié : proposé une fois.
        core.setOtherPlayers(Map.of(ALEX, "Alex", SAM, "Sam"));
        String both = "{\"players\":{\"" + Presence.hash(ALEX) + "\":\"linked\",\"" + Presence.hash(SAM) + "\":\"unknown\"}," + NO_TEAM + "}";
        api.reply(200, both).reply(200, both);
        clock.millis += 61_000;
        core.tick();
        clock.millis += 61_000;
        core.tick();
        assertEquals(1, count("xpack.team.suggest"), "une fois par jour");
        // Rejoindre une équipe FTB : rappel à chaque fois que ça ne compte pas pour XPack, sans bloquer la commande.
        assertTrue(core.onCommand("/ftbteams party join Mineurs", c -> {}));
        assertEquals(1, count("xpack.team.reminder"));
        // Déjà en équipe : plus de proposition, même le lendemain.
        clock.millis += 24 * 3_600_000L + 61_000;
        api.reply(200, "{\"players\":{\"" + Presence.hash(ALEX) + "\":\"linked\"},\"team\":{\"team\":{\"id\":\"team_1\",\"name\":null,\"founder\":\"Steve\","
                + "\"members\":[{\"pseudo\":\"Steve\",\"uuidHash\":\"" + Presence.hash(UUID) + "\"},{\"pseudo\":\"Alex\",\"uuidHash\":\"" + Presence.hash(ALEX) + "\"}]},"
                + "\"invites\":[],\"invited\":[]}}");
        core.tick();
        assertEquals(1, count("xpack.team.suggest"));
        assertTrue(core.team().inTeam());
        var alex = core.teamCandidates().stream().filter(c -> c.name().equals("Alex")).findFirst().orElseThrow();
        assertTrue(alex.teammate());
        assertEquals(Presence.Status.LINKED, alex.status());
    }

    @Test
    void annonceUneInvitationUneSeuleFoisEtLAccepte() {
        linkedAndJoined();
        core.setOtherPlayers(Map.of(ALEX, "Alex"));
        String invited = "{\"players\":{\"" + Presence.hash(ALEX) + "\":\"linked\"},\"team\":{\"team\":null,"
                + "\"invites\":[{\"id\":\"inv_1\",\"from\":\"Alex\",\"team\":null,\"founder\":\"Alex\"}],\"invited\":[]}}";
        api.reply(200, invited).reply(200, invited);
        core.tick();
        clock.millis += 61_000;
        core.tick();
        assertEquals(1, count("xpack.team.invited:Alex"), "annoncée une fois");
        assertEquals("Alex", core.team().invites().get(0).from());
        api.reply(200, "{\"team\":{\"id\":\"team_1\",\"name\":null,\"founder\":\"Alex\",\"members\":[]},\"invites\":[],\"invited\":[]}");
        core.answerTeamInvite("inv_1", true);
        var call = api.last("/v1/teams/invites/inv_1/accept");
        assertNotNull(call);
        assertEquals("s_1", call.body().get("sessionId").getAsString());
        assertTrue(core.team().inTeam());
        // Quitter l'équipe depuis le jeu.
        api.reply(200, "{\"team\":null,\"invites\":[],\"invited\":[]}");
        core.leaveTeam();
        assertNotNull(api.last("/v1/teams/team_1/leave"));
        assertFalse(core.team().inTeam());
    }

    @Test
    void inviterUnJoueurQuiNEstPlusLaAfficheLErreur() {
        linkedAndJoined();
        core.setOtherPlayers(Map.of(ALEX, "Alex"));
        api.reply(404, "{\"error\":\"not_on_server\"}");
        core.inviteToTeam(Presence.hash(ALEX));
        assertEquals(Presence.hash(ALEX), api.last("/v1/teams/invite").body().get("uuidHash").getAsString());
        assertEquals("xpack.team.error.not_on_server", core.teamError());
        api.reply(0, "{}");
        core.inviteToTeam(Presence.hash(ALEX));
        assertEquals("xpack.team.error.offline", core.teamError());
    }

    @Test
    void sansCompteLiePasDEquipe() {
        core.onJoinWorld(world());
        core.setOtherPlayers(Map.of(ALEX, "Alex"));
        assertFalse(core.teamsAvailable());
        core.inviteToTeam(Presence.hash(ALEX));
        assertNull(api.last("/v1/teams/invite"));
    }
}
