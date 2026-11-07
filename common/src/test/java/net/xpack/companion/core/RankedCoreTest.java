package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Speedrun classé dans le cœur (#225) : démarrage au premier tick d'un monde neuf, fin à la mort du dragon. */
class RankedCoreTest {
    static final String SESSION = XPackCoreTest.SESSION;

    @TempDir
    Path dir;
    XPackCoreTest.FakeApi api;
    XPackCoreTest.FakeUi ui;
    XPackCoreTest.MutableClock clock;
    Config config;
    Credentials creds;
    XPackCore core;

    @BeforeEach
    void setUp() {
        api = new XPackCoreTest.FakeApi();
        ui = new XPackCoreTest.FakeUi();
        clock = new XPackCoreTest.MutableClock();
        config = new Config();
        config.consented = true;
        creds = new Credentials(dir.resolve("credentials.json"));
        core = new XPackCore(config, dir.resolve("xpack.json"), creds, new ApiClient(api), ui, Runnable::run, clock, IntegrityRules.bundled());
        creds.put(config.apiUrl, XPackCoreTest.UUID, "Steve", "jeton");
    }

    XPackCore.WorldContext world(String clockKind) {
        JsonObject fingerprint = new JsonObject();
        fingerprint.addProperty("playtimeClock", clockKind);
        return new XPackCore.WorldContext(dir.resolve("world/xpack.json"), "singleplayer", "2026-10-01T10:00:00Z", false, XPackCoreTest.UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), fingerprint, false);
    }

    List<String> sentTypes() {
        List<String> out = new ArrayList<>();
        for (XPackCoreTest.FakeApi.Call c : api.calls) {
            if (!c.path().endsWith("/events")) continue;
            JsonArray events = c.body().getAsJsonArray("events");
            for (JsonElement e : events) out.add(e.getAsJsonObject().get("type").getAsString());
        }
        return out;
    }

    void flush() {
        clock.millis += XPackCore.FLUSH_INTERVAL_MS;
        core.tick();
    }

    @Test
    void laRunDemarreAuPremierTickDUnMondeNeufSolo() {
        api.reply(201, SESSION);
        core.onJoinWorld(world("world"));
        core.setPlaytime(3);
        assertNotNull(core.rankedRun());
        assertTrue(ui.notices.contains("xpack.speedrun.ranked.started"));
        flush();
        assertTrue(sentTypes().contains("speedrun_start"));
        core.setPlaytime(4); // une seule fois
        flush();
        assertEquals(1, sentTypes().stream().filter("speedrun_start"::equals).count());
    }

    @Test
    void pasDeRunSiLeMondeEstDejaBienEntameOuEnMultijoueur() {
        api.reply(201, SESSION);
        core.onJoinWorld(world("world"));
        core.setPlaytime(5_000); // monde créé avant le mod
        assertNull(core.rankedRun());
        core.onLeaveWorld();

        core = new XPackCore(config, dir.resolve("xpack.json"), creds, new ApiClient(api), ui, Runnable::run, clock, IntegrityRules.bundled());
        api.reply(201, SESSION);
        core.onJoinWorld(new XPackCore.WorldContext(dir.resolve("multi/xpack.json"), "multiplayer", "2026-10-01T10:00:00Z", false, XPackCoreTest.UUID, "Steve",
                new ModpackDetector.ModpackInfo("modrinth", null, null, "Engrenages", "1.4.0"), jsonClock("player"), false));
        core.setPlaytime(3);
        assertNull(core.rankedRun(), "multijoueur : pas de run classée pour l'instant");
    }

    private static JsonObject jsonClock(String kind) {
        JsonObject o = new JsonObject();
        o.addProperty("playtimeClock", kind);
        return o;
    }

    @Test
    void lesEtapesPuisLaMortDuDragonSontEnvoyees() {
        api.reply(201, SESSION);
        core.onJoinWorld(world("world"));
        core.setPlaytime(0);
        core.onAdvancements(List.of("minecraft:story/enter_the_nether"), false, 2_000);
        clock.millis += 60_000;
        core.onAdvancements(List.of("minecraft:end/kill_dragon"), false, 24_000);
        assertEquals(RankedRun.State.FINISHED, core.rankedRun().state);
        assertEquals(24_000, core.rankedIgtTicks());
        assertEquals(60_000, core.rankedRtaMs());
        flush();
        List<String> types = sentTypes();
        assertTrue(types.indexOf("speedrun_start") < types.indexOf("speedrun_split"));
        assertTrue(types.indexOf("speedrun_split") < types.indexOf("speedrun_end"));
    }

    @Test
    void lesProgresDeLInstantaneDeConnexionNeComptentPas() {
        api.reply(201, SESSION);
        core.onJoinWorld(world("world"));
        core.setPlaytime(0);
        core.onAdvancements(List.of("minecraft:end/kill_dragon"), true, 10);
        assertEquals(RankedRun.State.ACTIVE, core.rankedRun().state);
    }

    @Test
    void lesTrichesActiveesFontPerdreLaRun() {
        api.reply(201, SESSION);
        core.onJoinWorld(world("world"));
        core.setPlaytime(0);
        core.onCheatsAllowed(true);
        assertEquals(RankedRun.State.INVALID, core.rankedRun().state);
        assertEquals("cheats", core.rankedRun().invalidReason);
        assertTrue(ui.notices.contains("xpack.speedrun.ranked.invalid"));
        core.onAdvancements(List.of("minecraft:end/kill_dragon"), false, 24_000);
        flush();
        assertFalse(sentTypes().contains("speedrun_end"), "une run perdue n'envoie plus d'étape");
    }

    @Test
    void laRunSurvitAuRedemarrageDuMonde() {
        api.reply(201, SESSION);
        core.onJoinWorld(world("world"));
        core.setPlaytime(0);
        core.onAdvancements(List.of("minecraft:story/enter_the_nether"), false, 2_000);
        core.onLeaveWorld();
        api.reply(201, SESSION);
        core.onJoinWorld(world("world"));
        assertNotNull(core.rankedRun());
        assertEquals(1, core.rankedRun().splits.size());
        core.setPlaytime(3_000);
        assertEquals(1, core.rankedRun().splits.size(), "pas de nouveau départ à la reconnexion");
    }
}
