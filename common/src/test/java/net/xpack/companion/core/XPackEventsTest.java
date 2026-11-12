package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.xpack.companion.api.XPackEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** API d'événements locale : désactivée par défaut, émise depuis le cœur, isolée des erreurs des autres mods. */
class XPackEventsTest {
    static final String BATCH = "{\"accepted\":1,\"unlocked\":[{\"achievementId\":\"x/diamants\",\"name\":\"Diamants\",\"points\":19,\"rarity\":\"epique\","
            + "\"item\":\"minecraft:diamond\",\"percent\":0.4,\"difficulty\":\"hard\",\"quest\":true}],"
            + "\"completed\":null,\"milestone\":{\"version\":\"1.2\",\"tier\":50,\"achievements\":21,\"total\":42,\"playtimeTicks\":720000},"
            + "\"integrity\":{\"status\":\"propre\",\"flaggedAt\":null}}";

    /** Enregistre tout ce qu'il reçoit, sous forme de texte. */
    static final class Recorder implements XPackEvents.Listener {
        final List<String> got = new ArrayList<>();
        final List<Object> events = new ArrayList<>();

        @Override
        public void onAchievementUnlocked(XPackEvents.AchievementUnlocked e) {
            got.add("unlocked");
            events.add(e);
        }

        @Override
        public void onMilestone(XPackEvents.Milestone e) {
            got.add("milestone");
            events.add(e);
        }

        @Override
        public void onModpackCompleted(XPackEvents.ModpackCompleted e) {
            got.add("completed");
            events.add(e);
        }

        @Override
        public void onWorldTracking(XPackEvents.WorldTracking e) {
            got.add("tracking:" + e.tracked + ":" + e.reason);
            events.add(e);
        }
    }

    @TempDir
    Path dir;
    XPackCoreTest.FakeApi api;
    XPackCoreTest.FakeUi ui;
    Config config;
    Credentials creds;
    XPackCore core;

    @BeforeEach
    void setUp() {
        XPackEvents.clearListeners();
        api = new XPackCoreTest.FakeApi();
        ui = new XPackCoreTest.FakeUi();
        config = new Config();
        config.consented = true;
        creds = new Credentials(dir.resolve("credentials.json"));
        core = new XPackCore(config, dir.resolve("xpack.json"), creds, new ApiClient(api), ui, Runnable::run, new XPackCoreTest.MutableClock(), IntegrityRules.bundled());
    }

    @AfterEach
    void tearDown() {
        XPackEvents.clearListeners();
    }

    XPackCore.WorldContext world(String folder, boolean creative) {
        return new XPackCore.WorldContext(dir.resolve(folder + "/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", creative, XPackCoreTest.UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), new JsonObject(), creative);
    }

    void joinAndUnlock() {
        creds.put(config.apiUrl, XPackCoreTest.UUID, "Steve", "jeton");
        api.reply(201, XPackCoreTest.SESSION);
        core.onJoinWorld(world("world", false));
        core.onAdvancements(List.of("create:andesite_alloy"), false, 2_000);
        api.reply(200, BATCH);
        core.tick();
    }

    @Test
    void desactiveeParDefautAucunEcouteurAppele() {
        Recorder r = new Recorder();
        XPackEvents.register(r);
        assertFalse(config.eventsApi);
        assertFalse(XPackEvents.enabled());
        joinAndUnlock();
        core.onLeaveWorld();
        assertTrue(r.got.isEmpty());
        assertEquals(2, ui.toasts.size(), "les toasts ne changent pas");
    }

    @Test
    void activeeEmetSuccesEtPalierMemeSansToasts() {
        config.eventsApi = true;
        config.toasts = false;
        Recorder r = new Recorder();
        XPackEvents.register(r);
        joinAndUnlock();
        assertTrue(ui.toasts.isEmpty(), "toasts coupés : aucun toast");
        assertEquals(List.of("tracking:true:joined", "unlocked", "milestone"), r.got);
        XPackEvents.AchievementUnlocked a = (XPackEvents.AchievementUnlocked) r.events.get(1);
        assertEquals("Diamants", a.name);
        assertEquals(19, a.points);
        assertEquals("epique", a.rarity);
        assertEquals("minecraft:diamond", a.item);
        assertEquals(0.4, a.percent, 1e-9);
        assertEquals("hard", a.difficulty);
        assertTrue(a.quest);
        XPackEvents.Milestone m = (XPackEvents.Milestone) r.events.get(2);
        assertEquals("Engrenages", m.modpackName);
        assertEquals(50, m.tier);
        assertEquals(21, m.achievements);
        assertEquals(42, m.total);
        assertTrue(a.toString().contains("Diamants"));
    }

    @Test
    void emetLeModpackTermine() {
        config.eventsApi = true;
        Recorder r = new Recorder();
        XPackEvents.register(r);
        creds.put(config.apiUrl, XPackCoreTest.UUID, "Steve", "jeton");
        api.reply(201, XPackCoreTest.SESSION);
        core.onJoinWorld(world("world", false));
        core.onAdvancements(List.of("create:andesite_alloy"), false, 2_000);
        api.reply(200, "{\"accepted\":1,\"unlocked\":[],\"completed\":{\"version\":\"1.2\",\"achievements\":42,\"playtimeTicks\":720000},"
                + "\"integrity\":{\"status\":\"propre\",\"flaggedAt\":null}}");
        core.tick();
        assertEquals(List.of("tracking:true:joined", "completed"), r.got);
        XPackEvents.ModpackCompleted c = (XPackEvents.ModpackCompleted) r.events.get(1);
        assertEquals(42, c.achievements);
        assertEquals(720000L, c.playtimeTicks);
    }

    @Test
    void ecouteurQuiLanceUneExceptionNeCassePasLesAutresNiLeToast() {
        config.eventsApi = true;
        XPackEvents.register(new XPackEvents.Listener() {
            @Override
            public void onAchievementUnlocked(XPackEvents.AchievementUnlocked e) {
                throw new IllegalStateException("autre mod cassé");
            }

            @Override
            public void onMilestone(XPackEvents.Milestone e) {
                throw new IllegalStateException("autre mod cassé");
            }
        });
        Recorder r = new Recorder();
        XPackEvents.register(r);
        joinAndUnlock();
        assertEquals(List.of("tracking:true:joined", "unlocked", "milestone"), r.got);
        assertEquals(2, ui.toasts.size(), "le toast et le palier sont affichés malgré l'exception");
    }

    @Test
    void unregisterRetireLEcouteur() {
        config.eventsApi = true;
        Recorder r = new Recorder();
        XPackEvents.register(r);
        XPackEvents.unregister(r);
        joinAndUnlock();
        assertTrue(r.got.isEmpty());
    }

    @Test
    void suiviDuMondeEntreeSortieEtMondeDeTest() {
        config.eventsApi = true;
        Recorder r = new Recorder();
        XPackEvents.register(r);
        creds.put(config.apiUrl, XPackCoreTest.UUID, "Steve", "jeton");
        api.reply(201, XPackCoreTest.SESSION);
        core.onJoinWorld(world("world", false));
        core.markWorldPractice();
        core.markWorldPractice(); // déjà monde de test : pas de doublon
        core.onLeaveWorld();
        core.onJoinWorld(world("creatif", true));
        core.onLeaveWorld();
        assertEquals(List.of("tracking:true:joined", "tracking:false:practice", "tracking:false:left",
                "tracking:false:practice", "tracking:false:left"), r.got);
    }

    @Test
    void mondeSignaleEmetUneSeuleFois() {
        config.eventsApi = true;
        Recorder r = new Recorder();
        XPackEvents.register(r);
        creds.put(config.apiUrl, XPackCoreTest.UUID, "Steve", "jeton");
        api.reply(201, XPackCoreTest.SESSION);
        core.onJoinWorld(world("world", false));
        core.onPlayerState(new XPackCore.PlayerState("creative", false), 30);
        core.onPlayerState(new XPackCore.PlayerState("creative", false), 40);
        assertTrue(core.integrity().isFlagged());
        assertEquals(List.of("tracking:true:joined", "tracking:false:flagged"), r.got);
    }
}
