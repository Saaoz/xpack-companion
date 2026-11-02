package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LegacyStatsTest {
    @Test
    void translatesBlocksAndItemsToTheCommonFormat() {
        assertEquals("minecraft:mined/minecraft:diamond_ore", LegacyStats.toKey("stat.mineBlock.minecraft.diamond_ore"));
        assertEquals("minecraft:crafted/minecraft:iron_pickaxe", LegacyStats.toKey("stat.craftItem.minecraft.iron_pickaxe"));
        assertEquals("minecraft:used/minecraft:bow", LegacyStats.toKey("stat.useItem.minecraft.bow"));
        assertEquals("minecraft:broken/minecraft:iron_pickaxe", LegacyStats.toKey("stat.breakItem.minecraft.iron_pickaxe"));
        assertEquals("minecraft:mined/tconstruct:ore", LegacyStats.toKey("stat.mineBlock.tconstruct.ore"));
    }

    @Test
    void translatesCustomStatistics() {
        assertEquals("minecraft:custom/minecraft:play_time", LegacyStats.toKey("stat.playOneMinute"));
        assertEquals("minecraft:custom/minecraft:walk_one_cm", LegacyStats.toKey("stat.walkOneCm"));
        assertEquals("minecraft:custom/minecraft:deaths", LegacyStats.toKey("stat.deaths"));
        assertEquals("minecraft:custom/minecraft:mob_kills", LegacyStats.toKey("stat.mobKills"));
    }

    @Test
    void ignoresWhatHasNoCommonEquivalent() {
        assertNull(LegacyStats.toKey("achievement.openInventory"));
        assertNull(LegacyStats.toKey("stat.killEntity.Zombie"));
        assertNull(LegacyStats.toKey("stat.mineBlock.nodot"));
        assertNull(LegacyStats.toKey(null));
    }
}
