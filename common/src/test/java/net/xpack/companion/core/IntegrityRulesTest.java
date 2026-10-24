package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Mêmes cas que src/domain/integrity.test.ts : le site et le mod doivent rendre le même verdict. */
class IntegrityRulesTest {
    private final IntegrityRules rules = IntegrityRules.bundled();

    @Test
    void normaliseLeNomDeCommande() {
        assertEquals("give", IntegrityRules.commandName("/minecraft:give @s diamond"));
        assertEquals("gamemode", IntegrityRules.commandName("  GameMode creative"));
        assertEquals("", IntegrityRules.commandName(""));
    }

    @Test
    void repereLesCommandesDeTriche() {
        assertEquals(IntegrityRules.Verdict.CHEAT, rules.classify("/give @s minecraft:diamond 64", List.of()));
        assertEquals(IntegrityRules.Verdict.CHEAT, rules.classify("/gamemode creative", List.of()));
        assertEquals(IntegrityRules.Verdict.CHEAT, rules.classify("effect give @s resistance 99999 255", List.of()));
    }

    @Test
    void laissePasserLesCommandesSocialesEtInconnues() {
        assertEquals(IntegrityRules.Verdict.ALLOWED, rules.classify("/msg Ami salut", List.of()));
        assertEquals(IntegrityRules.Verdict.NEUTRAL, rules.classify("/ftbchunks claim", List.of()));
    }

    @Test
    void respecteLaListeDuModpack() {
        List<String> cfg = List.of("home", "/back", "tp");
        assertEquals(IntegrityRules.Verdict.ALLOWED, rules.classify("/home base", cfg));
        assertEquals(IntegrityRules.Verdict.ALLOWED, rules.classify("/back", cfg));
        assertEquals(IntegrityRules.Verdict.ALLOWED, rules.classify("/tp ~ ~10 ~", cfg));
        assertEquals(IntegrityRules.Verdict.CHEAT, rules.classify("/give @s dirt", cfg));
        assertEquals(List.of("tp"), rules.dangerousAllowed(cfg));
    }

    @Test
    void statutDIntegrite() {
        WorldIntegrity w = new WorldIntegrity();
        assertTrue(w.unlockCounts("2026-01-01T00:00:00Z"));
        assertTrue(w.flag(WorldIntegrity.Reason.COMMAND, "2026-02-01T00:00:00Z", "/give"));
        assertFalse(w.flag(WorldIntegrity.Reason.CREATIVE, "2026-03-01T00:00:00Z", null));
        assertEquals("2026-02-01T00:00:00Z", w.flaggedAt());
        assertEquals(2, w.reasons().size());
        assertTrue(w.unlockCounts("2026-01-15T00:00:00Z"));
        assertFalse(w.unlockCounts("2026-02-15T00:00:00Z"));
    }
}
