package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Quêtes FTB Quests (04/11/2026), au format d'ATM10 (FTB Quests 2101). */
class FtbQuestsTest {
    static final String PLAYER = "9c3081cd-54cf-4822-8f2d-00fc1fc82f85";

    static final String AETHER = """
            {
            	default_hide_dependency_lines: false
            	filename: "aether"
            	group: "5A4775CCD6D26C80"
            	icon: { id: "aether:angelic_stone" }
            	id: "24151251C8730F2E"
            	order_index: 0
            	quests: [
            		{
            			icon: { id: "ftbquests:custom_icon" }
            			id: "27F62C4B42B16015"
            			tasks: [{ count: 10L, id: "3677156DC4D715D5", item: { count: 1, id: "minecraft:glowstone" }, type: "item" }]
            			x: 0.0d
            			y: 16.5d
            		}
            		{
            			dependencies: ["27F62C4B42B16015"]
            			icon: { id: "aether:golden_aercloud" }
            			id: "5825892699A8DCB7"
            			invisible: true
            			x: 1.5d
            			y: 16.5d
            		}
            	]
            }""";

    static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    static void book(Path game) throws Exception {
        Path q = FtbQuests.booksDir(game);
        write(q.resolve("chapter_groups.snbt"), "{ chapter_groups: [ { id: \"2084F3F6FB861C5B\" }\n { id: \"5A4775CCD6D26C80\" } ] }");
        write(q.resolve("chapters/aether.snbt"), AETHER);
        write(q.resolve("chapters/welcome.snbt"), "{ filename: \"welcome\", group: \"\", id: \"0D990A1D1E088701\", title: \"&6Welcome\", quests: [{ id: \"3BC0A50886A3222B\", title: \"Hi &aATM\", x: 0.0d, y: 0.0d }, { id: \"CCCC000000000002\", tasks: [{ id: \"3\", type: \"checkmark\" }], description: [\"To claim chunks, open up your map. Then drag.\"], x: 1.0d, y: 0.0d }] }");
        write(q.resolve("lang/en_us/chapter.snbt"), "{\n\tchapter.24151251C8730F2E.title: \"The Aether\"\n}");
        write(q.resolve("lang/fr_fr/chapter.snbt"), "{ chapter.24151251C8730F2E.title: \"L'Aether\" }");
        write(q.resolve("lang/en_us/chapters/aether.snbt"), "{\n\tquest.5825892699A8DCB7.quest_desc: [\"Climb to the &eclouds&r.\"]\n\tquest.5825892699A8DCB7.title: \"&dCloud Walker\"\n}");
        write(q.resolve("lang/fr_fr/chapters/aether.snbt"), "{ quest.5825892699A8DCB7.title: \"Marcheur des nuages\" }");
    }

    @Test
    void snbt() {
        JsonObject o = Snbt.parse("{ a.b: 10L, x: 0.5d, t: true, s: \"q\\\"uote\", ids: [I; 1, 2], l: [ \"a\"\n \"b\" ], id: minecraft:stone }").getAsJsonObject();
        assertEquals(10, o.get("a.b").getAsLong());
        assertEquals(0.5, o.get("x").getAsDouble());
        assertTrue(o.get("t").getAsBoolean());
        assertEquals("q\"uote", o.get("s").getAsString());
        assertEquals(2, o.getAsJsonArray("ids").size());
        assertEquals(2, o.getAsJsonArray("l").size());
        assertEquals("minecraft:stone", o.get("id").getAsString());
        assertThrows(IllegalArgumentException.class, () -> Snbt.parse("{ a: "));
    }

    @Test
    void livreDansLOrdreAvecSesTextes(@TempDir Path game) throws Exception {
        book(game);
        List<FtbQuests.Quest> fr = FtbQuests.readBook(game, "fr_fr");
        assertEquals(List.of("ftbquests:quest/3BC0A50886A3222B", "ftbquests:quest/CCCC000000000002", "ftbquests:quest/27F62C4B42B16015", "ftbquests:quest/5825892699A8DCB7"), fr.stream().map(q -> q.id).toList());
        FtbQuests.Quest walker = fr.get(3);
        assertEquals("Marcheur des nuages", walker.title);
        assertEquals("Climb to the clouds.", walker.description);
        assertEquals("L'Aether", walker.chapter);
        assertEquals("ftbquests:quest/27F62C4B42B16015", walker.parent);
        assertTrue(walker.hidden);
        assertEquals(3, walker.column);
        assertEquals(33, walker.row);
        assertEquals("Welcome", fr.get(0).chapter);
        assertEquals("Hi ATM", fr.get(0).title);
        // Quête sans titre : le nom de l'objet de sa première tâche, traduit par le jeu.
        List<XPackCore.AdvancementInfo> catalog = FtbQuests.catalog(FtbQuests.readBook(game, "en_us"), k -> k.equals("block.minecraft.glowstone") ? "Glowstone" : k);
        assertEquals("To claim chunks, open up your map.", catalog.get(1).title(), "ni titre ni objet : le début de la description");
        assertEquals("Glowstone", catalog.get(2).title());
        assertEquals("The Aether", catalog.get(2).tab());
        assertEquals("minecraft:glowstone", catalog.get(2).icon());
        assertEquals("Cloud Walker", catalog.get(3).title());
        assertTrue(FtbQuests.readBook(game.resolve("autre"), "en_us").isEmpty());
    }

    @Test
    void textesDansUnSeulFichierParLangue(@TempDir Path game) throws Exception {
        book(game);
        Path q = FtbQuests.booksDir(game);
        write(q.resolve("lang/en_us.snbt"), "{\n\tchapter.24151251C8730F2E.title: \"Aether Realm\"\n\tquest.27F62C4B42B16015.title: \"Glow Up\"\n}");
        write(q.resolve("lang/fr_fr.snbt"), "{ quest.27F62C4B42B16015.title: \"Ça brille\" }");
        FtbQuests.Quest fr = FtbQuests.readBook(game, "fr_fr").get(2);
        assertEquals("Ça brille", fr.title);
        FtbQuests.Quest en = FtbQuests.readBook(game, "en_us").get(2);
        assertEquals("Glow Up", en.title);
        assertEquals("Aether Realm", en.chapter);
    }

    @Test
    void progressionDeLEquipeDuJoueur(@TempDir Path world) throws Exception {
        Path own = world.resolve("ftbquests").resolve(PLAYER + ".snbt");
        write(own, "{\n\tversion: 1\n\tcompleted: {\n\t\t\"27F62C4B42B16015\": 1730000000000L\n\t\t\"24151251C8730F2E\": 1730000000500L\n\t}\n\ttask_progress: { \"3677156DC4D715D5\": 10L }\n}");
        assertEquals(own, FtbQuests.progressFile(world, PLAYER));
        Map<String, Long> done = FtbQuests.readCompleted(own, Set.of("ftbquests:quest/27F62C4B42B16015", "ftbquests:quest/5825892699A8DCB7"));
        assertEquals(Map.of("ftbquests:quest/27F62C4B42B16015", 1_730_000_000_000L), done, "le chapitre terminé n'est pas une quête");
        assertEquals(2, FtbQuests.readCompleted(own, null).size());
        assertTrue(FtbQuests.readCompleted(world.resolve("absent.snbt"), null).isEmpty());
        write(own, "{ completed: { ");
        assertNull(FtbQuests.readCompleted(own, null), "en cours d'écriture : relu plus tard");
    }

    @Test
    void equipeFtbTeamsOuSeulFichierDuMonde(@TempDir Path world) throws Exception {
        String party = "0f9e1c2a-3b4c-4d5e-8f60-718293a4b5c6";
        write(world.resolve("ftbquests").resolve(party + ".snbt"), "{ completed: {} }");
        write(world.resolve("ftbquests").resolve("11111111-2222-3333-4444-555555555555.snbt"), "{ completed: {} }");
        write(world.resolve("ftbteams").resolve("party").resolve(party + ".snbt"), "{ members: { \"" + PLAYER.toUpperCase() + "\": {} } }");
        assertEquals(world.resolve("ftbquests").resolve(party + ".snbt"), FtbQuests.progressFile(world, PLAYER));
    }

    @Test
    void seulFichierDuMonde(@TempDir Path world) throws Exception {
        Path only = world.resolve("ftbquests").resolve("abc.snbt");
        write(only, "{ completed: {} }");
        assertEquals(only, FtbQuests.progressFile(world, PLAYER));
    }

    @Test
    void suiviAvecQuestWatcher(@TempDir Path dir) throws Exception {
        Path game = dir.resolve("game");
        Path world = dir.resolve("world");
        book(game);
        write(world.resolve("ftbquests").resolve(PLAYER + ".snbt"), "{ completed: { \"5825892699A8DCB7\": 1730000000000L } }");
        List<List<XPackCore.AdvancementInfo>> catalogs = new ArrayList<>();
        List<Map<String, Long>> completions = new ArrayList<>();
        QuestWatcher.Source source = FtbQuests.source(game, world, PLAYER, () -> "en_us");
        assertEquals(world.resolve("ftbquests").resolve(PLAYER + ".snbt"), source.progressFile());
        List<XPackCore.AdvancementInfo> catalog = source.readCatalog(k -> k);
        catalogs.add(catalog);
        completions.add(source.readCompleted());
        assertEquals(4, catalogs.get(0).size());
        assertEquals(Map.of("ftbquests:quest/5825892699A8DCB7", 1_730_000_000_000L), completions.get(0));
        assertFalse(catalog.get(0).id().startsWith(BetterQuesting.PREFIX));
    }
}
