package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningTest {
    @Test
    void reconnaitMineraisEtRocheDesStatistiques() {
        Mining mining = Mining.bundled();
        assertTrue(mining.isOre("minecraft:deepslate_diamond_ore"));
        assertTrue(mining.isOre("create:zinc_ore"));
        assertTrue(mining.isOre("minecraft:ancient_debris"));
        assertFalse(mining.isOre("minecraft:stone"));
        Mining.Summary s = mining.summarize(Map.of(
                "minecraft:mined/minecraft:diamond_ore", 4,
                "minecraft:mined/minecraft:deepslate_diamond_ore", 6,
                "minecraft:mined/minecraft:stone", 900,
                "minecraft:mined/minecraft:deepslate", 100,
                "minecraft:mined/minecraft:oak_log", 50,
                "minecraft:used/minecraft:diamond_ore", 1,
                "minecraft:custom/minecraft:play_time", 72_000));
        assertEquals(new Mining.Summary(Map.of("minecraft:diamond_ore", 4, "minecraft:deepslate_diamond_ore", 6), 1000), s);
    }

    @Test
    void compteLesMineraisCassesALaMainAvecLOutil() {
        Mining mining = Mining.bundled();
        mining.onBlockBroken("minecraft:diamond_ore", "minecraft:iron_pickaxe");
        mining.onBlockBroken("minecraft:diamond_ore", "tconstruct:pickaxe");
        mining.onBlockBroken("minecraft:stone", "tconstruct:hammer"); // pas un minerai : ni compté ni outil retenu
        assertEquals(Map.of("minecraft:diamond_ore", 2), mining.hand());
        assertEquals(Set.of("minecraft:iron_pickaxe", "tconstruct:pickaxe"), mining.tools());
        mining.reset();
        assertEquals(Map.of(), mining.hand());
    }

    @Test
    void modsQuiMinentPourLeJoueur() {
        assertEquals(List.of("ftbultimine", "mekanism"), Mining.bundled().helpers(List.of("jei", "mekanism", "ftbultimine", "xpack")));
    }
}
