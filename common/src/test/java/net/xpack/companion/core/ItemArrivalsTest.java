package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemArrivalsTest {
    private static final ItemArrivals.Context NONE = new ItemArrivals.Context(null, false);
    private static final String DIAMONDS = "minecraft:diamond_block";

    @Test
    void listeEmbarqueeCommeLeSite() {
        ItemArrivals log = ItemArrivals.bundled();
        assertTrue(log.isValuable(DIAMONDS));
        assertTrue(log.isValuable("minecraft:zombie_spawn_egg"));
        assertTrue(log.isValuable("mekanism:creative_energy_cube"));
        assertFalse(log.isValuable("minecraft:cobblestone"));
    }

    @Test
    void unGiveEnSoloArriveSansExplication() {
        ItemArrivals log = ItemArrivals.bundled();
        log.observe(Map.of(), NONE, 0);
        assertEquals(List.of(new ItemArrivals.Arrival(DIAMONDS, 64, ItemArrivals.Via.DIRECT, null, false, false)), log.observe(Map.of(DIAMONDS, 64), NONE, 1_000));
        // Une pile ordinaire arrivée d'un coup est notée aussi ; un seau rempli ne l'est pas.
        List<ItemArrivals.Arrival> next = log.observe(Map.of(DIAMONDS, 64, "minecraft:cobblestone", 64, "minecraft:water_bucket", 1), NONE, 2_000);
        assertEquals(List.of(new ItemArrivals.Arrival("minecraft:cobblestone", 64, ItemArrivals.Via.DIRECT, null, false, false)), next);
    }

    @Test
    void unRamassageExpliqueLArriveePendantDeuxReleves() {
        ItemArrivals log = ItemArrivals.bundled();
        log.observe(Map.of(), NONE, 0);
        log.onPickup(DIAMONDS, 3);
        log.observe(Map.of(), NONE, 1_000); // le paquet de ramassage précède la mise à jour de l'inventaire
        var nearby = new ItemArrivals.Context(null, true);
        assertEquals(
                List.of(new ItemArrivals.Arrival(DIAMONDS, 3, ItemArrivals.Via.PICKUP, null, true, false), new ItemArrivals.Arrival(DIAMONDS, 1, ItemArrivals.Via.DIRECT, null, true, false)),
                log.observe(Map.of(DIAMONDS, 4), nearby, 2_000));
        log.onPickup(DIAMONDS, 1);
        log.observe(Map.of(DIAMONDS, 4), NONE, 3_000);
        log.observe(Map.of(DIAMONDS, 4), NONE, 4_000);
        assertEquals(ItemArrivals.Via.DIRECT, log.observe(Map.of(DIAMONDS, 5), NONE, 5_000).get(0).via(), "ramassage trop ancien");
    }

    @Test
    void ecranOuvertEtRecompenseDeQuete() {
        ItemArrivals log = ItemArrivals.bundled();
        log.setQuestRewards(java.util.Set.of("minecraft:pig_spawn_egg"));
        log.observe(Map.of(), NONE, 0);
        var chest = new ItemArrivals.Context("minecraft:generic_9x3", false);
        assertEquals(
                List.of(new ItemArrivals.Arrival("minecraft:pig_spawn_egg", 1, ItemArrivals.Via.SCREEN, "minecraft:generic_9x3", false, true)),
                log.observe(Map.of("minecraft:pig_spawn_egg", 1, "minecraft:cobblestone", 64), chest, 1_000));
    }

    @Test
    void repartDeZeroEtLimiteLeFlot() {
        ItemArrivals log = ItemArrivals.bundled();
        log.observe(Map.of(), NONE, 0);
        log.reset();
        assertEquals(List.of(), log.observe(Map.of(DIAMONDS, 64), NONE, 500), "réapparition : l'inventaire revient, rien n'arrive");
        int noted = 0;
        for (int i = 1; i <= 40; i++) noted += log.observe(Map.of(DIAMONDS, 64 + i), NONE, 1_000 + i).size();
        assertEquals(ItemArrivals.MAX_PER_MINUTE, noted);
        assertEquals(1, log.observe(Map.of(DIAMONDS, 200), NONE, 70_000).size(), "nouvelle minute");
    }
}
