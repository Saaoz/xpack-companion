package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestRewardsTest {
    @TempDir
    Path game;

    @Test
    void litLesRecompensesFtbQuestsMaisPasLesTaches() throws Exception {
        Path chapters = Files.createDirectories(game.resolve("config/ftbquests/quests/chapters"));
        Files.writeString(chapters.resolve("debut.snbt"), """
                {
                	quests: [{
                		id: "1A2B"
                		tasks: [{ id: "3C", type: "item", item: "minecraft:diamond" }]
                		rewards: [
                			{ id: "4D", type: "item", item: "minecraft:pig_spawn_egg" }
                			{ id: "5E", type: "item", item: { id: "mekanism:creative_energy_cube", count: 1 } }
                			{ id: "6F", type: "random", table_id: 123L }
                		]
                	}]
                }
                """);
        Path tables = Files.createDirectories(game.resolve("config/ftbquests/quests/reward_tables"));
        Files.writeString(tables.resolve("rare.snbt"), """
                { id: "7A", title: "Rare \\"]\\" loot", rewards: [{ item: "minecraft:nether_star", weight: 1.0f }] }
                """);
        assertEquals(Set.of("minecraft:pig_spawn_egg", "mekanism:creative_energy_cube", "minecraft:nether_star"), QuestRewards.scan(game));
    }

    @Test
    void litLesRecompensesHeracles() throws Exception {
        Path quests = Files.createDirectories(game.resolve("config/heracles/quests/main"));
        Files.writeString(quests.resolve("q.json"), """
                {"tasks": {"t": {"type": "heracles:item", "item": "minecraft:dirt"}},
                 "rewards": {"r": {"type": "heracles:item", "item": {"id": "minecraft:elytra", "count": 1}}}}
                """);
        assertEquals(Set.of("heracles:item", "minecraft:elytra"), QuestRewards.scan(game));
    }

    @Test
    void sansQuetesRienNEstExplique() {
        assertEquals(Set.of(), QuestRewards.scan(game));
    }
}
