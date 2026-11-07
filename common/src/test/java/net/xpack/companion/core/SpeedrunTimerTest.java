package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpeedrunTimerTest {
    private long now = 1_000;
    private final SpeedrunTimer timer = new SpeedrunTimer(() -> now);

    private void play(int ticks, long realMs) {
        for (int i = 0; i < ticks; i++) timer.gameTick();
        now += realMs;
    }

    @Test
    void ignoreLesTicksTantQueLeChronoNeTournePas() {
        timer.gameTick();
        assertEquals(0, timer.igtTicks());
        assertEquals(SpeedrunTimer.State.IDLE, timer.state());
    }

    @Test
    void igtCompteLesTicksEtRtaLeTempsReel() {
        timer.start();
        play(40, 3_000);
        assertEquals(2_000, timer.igtMs());
        assertEquals(3_000, timer.rtaMs());
    }

    @Test
    void laPauseArreteLesDeuxTempsPuisReprend() {
        timer.start();
        play(20, 1_000);
        timer.toggle();
        assertEquals(SpeedrunTimer.State.PAUSED, timer.state());
        play(100, 60_000);
        assertEquals(1_000, timer.igtMs());
        assertEquals(1_000, timer.rtaMs());
        timer.toggle();
        play(20, 1_000);
        assertEquals(2_000, timer.igtMs());
        assertEquals(2_000, timer.rtaMs());
    }

    @Test
    void sansListeLesEtapesSontNumerotees() {
        timer.start();
        play(20, 1_000);
        assertEquals("Étape 1", timer.split().name);
        play(20, 1_000);
        assertEquals("Étape 2", timer.split().name);
        assertEquals(2, timer.splits().size());
        assertEquals(SpeedrunTimer.State.RUNNING, timer.state());
    }

    @Test
    void laDerniereEtapeDeLaListeTermineLeChrono() {
        timer.setSteps(Arrays.asList("Village", "Nether"));
        timer.start();
        play(20, 1_000);
        assertEquals("Village", timer.split().name);
        play(20, 1_000);
        assertEquals("Nether", timer.split().name);
        assertEquals(SpeedrunTimer.State.FINISHED, timer.state());
        assertNull(timer.split());
        assertNull(timer.nextStep());
        play(20, 1_000);
        assertEquals(2_000, timer.igtMs());
        assertEquals(2_000, timer.rtaMs());
    }

    @Test
    void arreterGardeLesTempsJusquaLaRemiseAZero() {
        timer.start();
        play(20, 1_000);
        timer.stop();
        assertTrue(timer.isActive());
        assertEquals(1_000, timer.rtaMs());
        timer.reset();
        assertEquals(SpeedrunTimer.State.IDLE, timer.state());
        assertEquals(0, timer.igtTicks());
        assertTrue(timer.splits().isEmpty());
    }

    @Test
    void ecartAvecLeMeilleurTempsPersonnel() {
        timer.setSteps(Arrays.asList("A", "B"));
        timer.setBest(Arrays.asList(100L, 300L));
        timer.start();
        play(90, 0);
        timer.split();
        play(220, 0);
        timer.split();
        assertEquals(Long.valueOf(-10), timer.deltaTicks(0));
        assertEquals(Long.valueOf(10), timer.deltaTicks(1));
        assertNull(timer.deltaTicks(2));
    }

    @Test
    void unMeilleurTempsNEstEnregistreQuePlusRapideEtComplet() {
        timer.setSteps(Arrays.asList("A", "B"));
        timer.start();
        play(100, 0);
        timer.split();
        assertNull(timer.newBest());
        play(100, 0);
        timer.split();
        List<Long> first = timer.newBest();
        assertEquals(Arrays.asList(100L, 200L), first);
        timer.setBest(first);
        timer.start();
        play(100, 0);
        timer.split();
        play(150, 0);
        timer.split();
        assertNull(timer.newBest());
        timer.start();
        play(90, 0);
        timer.split();
        play(100, 0);
        timer.split();
        assertEquals(Arrays.asList(90L, 190L), timer.newBest());
    }

    @Test
    void immobileEnPauseLeChronoSeReprendSansPerte() {
        timer.start();
        play(20, 1_000);
        timer.pause();
        assertFalse(timer.watchMovement(10, 64, 10, "minecraft:overworld"));
        for (int i = 0; i < 100; i++) assertFalse(timer.watchMovement(10.001, 64, 10, "minecraft:overworld"));
        timer.resume();
        assertEquals(SpeedrunTimer.State.RUNNING, timer.state());
    }

    @Test
    void toutDeplacementEnPauseFaitPerdreLeChrono() {
        timer.start();
        play(20, 1_000);
        timer.pause();
        timer.watchMovement(10, 64, 10, "minecraft:overworld");
        assertTrue(timer.watchMovement(10, 64, 10.5, "minecraft:overworld"));
        assertEquals(SpeedrunTimer.State.LOST, timer.state());
        play(100, 60_000);
        assertEquals(1_000, timer.rtaMs()); // temps figés
        timer.resume();
        assertEquals(SpeedrunTimer.State.LOST, timer.state());
        assertNull(timer.newBest());
    }

    @Test
    void unChangementDeDimensionEnPauseFaitPerdreLeChrono() {
        timer.start();
        timer.pause();
        timer.watchMovement(0, 64, 0, "minecraft:overworld");
        assertTrue(timer.watchMovement(0, 64, 0, "minecraft:the_nether"));
    }

    @Test
    void lesDeplacementsHorsPauseNeComptentPas() {
        timer.start();
        assertFalse(timer.watchMovement(0, 64, 0, "minecraft:overworld"));
        assertFalse(timer.watchMovement(50, 64, 50, "minecraft:overworld"));
        assertEquals(SpeedrunTimer.State.RUNNING, timer.state());
    }

    @Test
    void uneNouvellePauseRepartDeSaPositionEtUnChronoPerduSeRelance() {
        timer.start();
        timer.pause();
        timer.watchMovement(0, 64, 0, "minecraft:overworld");
        timer.resume();
        timer.watchMovement(30, 64, 0, "minecraft:overworld"); // pas en pause : rien
        timer.pause();
        assertFalse(timer.watchMovement(30, 64, 0, "minecraft:overworld"));
        assertTrue(timer.watchMovement(31, 64, 0, "minecraft:overworld"));
        timer.toggle(); // un chrono perdu repart de zéro
        assertEquals(SpeedrunTimer.State.RUNNING, timer.state());
        assertEquals(0, timer.igtTicks());
    }

    @Test
    void formatDesTemps() {
        assertEquals("00:01.500", SpeedrunTimer.format(1_500));
        assertEquals("1:02:03.456", SpeedrunTimer.format(3_723_456));
        assertEquals("-00:00.250", SpeedrunTimer.format(-250));
    }
}
