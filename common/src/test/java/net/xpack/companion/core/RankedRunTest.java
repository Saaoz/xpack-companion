package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class RankedRunTest {
    @Test
    void demarreDansUnMondeNeufSeulement() {
        assertNotNull(RankedRun.start(0, 1_000));
        assertNotNull(RankedRun.start(1200, 1_000));
        assertNull(RankedRun.start(1201, 1_000)); // monde déjà bien entamé : pas de run
        assertNull(RankedRun.start(-1, 1_000));
    }

    @Test
    void lIgtPartDeLApparitionEtLeRtaDuTempsReel() {
        RankedRun r = RankedRun.start(40, 10_000);
        assertEquals(0, r.igtTicks(40));
        assertEquals(1_000, r.igtTicks(1_040));
        assertEquals(5_000, r.rtaMs(15_000));
    }

    @Test
    void lesEtapesDuParcoursSontEnvoyeesUneSeuleFois() {
        RankedRun r = RankedRun.start(0, 0);
        RankedRun.Outcome o = r.onAdvancement("minecraft:story/enter_the_nether", 2_000, 100_000);
        assertEquals(RankedRun.Kind.SPLIT, o.kind);
        assertEquals(2_000, o.igtTicks);
        assertEquals(100_000, o.rtaMs);
        assertNull(r.onAdvancement("minecraft:story/enter_the_nether", 2_100, 105_000)); // déjà faite
        assertNull(r.onAdvancement("minecraft:story/mine_stone", 2_200, 106_000)); // hors parcours
        assertTrue(r.isActive());
    }

    @Test
    void laMortDuDragonEstUneEtapeEtNeTermineplusLaRun() {
        RankedRun r = RankedRun.start(0, 0);
        assertEquals(RankedRun.Kind.SPLIT, r.onAdvancement("minecraft:end/kill_dragon", 22_000, 1_200_000).kind);
        assertTrue(r.isActive());
    }

    @Test
    void leDebutDesCreditsTermineLaRunEtFigeLesTemps() {
        RankedRun r = RankedRun.start(0, 0);
        RankedRun.Outcome o = r.onCredits(24_000, 1_300_000);
        assertEquals(RankedRun.Kind.END, o.kind);
        assertEquals(RankedRun.State.FINISHED, r.state);
        assertEquals(24_000, r.igtTicks(99_999));
        assertEquals(1_300_000, r.rtaMs(9_999_999));
        assertNull(r.onCredits(25_000, 1_400_000)); // une seule fin
    }

    @Test
    void uneRunInvalideeNEnvoieRienDeplus() {
        RankedRun r = RankedRun.start(0, 0);
        assertTrue(r.invalidate("flagged", 500, 30_000));
        assertEquals(RankedRun.State.INVALID, r.state);
        assertEquals("flagged", r.invalidReason);
        assertFalse(r.invalidate("autre", 600, 40_000));
        assertNull(r.onCredits(700, 50_000));
        assertEquals(500, r.igtTicks(9_999));
    }

    @Test
    void uneRunTermineeNePeutPlusEtreInvalidee() {
        RankedRun r = RankedRun.start(0, 0);
        r.onCredits(100, 5_000);
        assertFalse(r.invalidate("flagged", 200, 6_000));
        assertEquals(RankedRun.State.FINISHED, r.state);
    }

    @Test
    void sesauvegardeEtSeRelitDansLeFichierDuMonde() {
        RankedRun r = RankedRun.start(10, 7_000);
        r.onAdvancement("minecraft:story/enter_the_nether", 400, 9_000);
        RankedRun back = new Gson().fromJson(new Gson().toJson(r), RankedRun.class);
        assertEquals(RankedRun.State.ACTIVE, back.state);
        assertEquals(10, back.startTicks);
        assertEquals(1, back.splits.size());
        assertNull(back.onAdvancement("minecraft:story/enter_the_nether", 500, 10_000));
    }
}
