package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BetterQuestingTest {
    static final String PLAYER = "9c3081cd-54cf-4822-8f2d-00fc1fc82f85";

    /** Extrait du livre de RLCraft 2.9.3 (BetterQuesting 3.5, identifiants entiers, noms à traduire). */
    static final String RLCRAFT_DB = """
            {"build:8":"3.5.329","questDatabase:9":{
              "0:10":{"questID:3":0,"preRequisites:11":[13],"properties:10":{"betterquesting:10":{
                "visibility:8":"NORMAL","ismain:1":0,"name:8":"betterquesting.quest.name.1","desc:8":"betterquesting.quest.desc.1",
                "icon:10":{"id:8":"iceandfire:amphithere_feather","Count:3":1}}}},
              "1:10":{"questID:3":13,"preRequisites:11":[],"properties:10":{"betterquesting:10":{
                "visibility:8":"HIDDEN","ismain:1":1,"name:8":"betterquesting.quest.name.14","desc:8":"",
                "icon:10":{"id:8":"minecraft:book"}}}},
              "2:10":{"questID:3":40,"preRequisites:11":[],"properties:10":{"betterquesting:10":{"name:8":"Hors du livre"}}}},
             "questLines:9":{
              "0:10":{"properties:10":{"betterquesting:10":{"name:8":"betterquesting.chapter.name.1"}},
                      "quests:9":{"0:10":{"id:3":13,"x:3":0},"1:10":{"id:3":0,"x:3":12}}}}}""";

    /** Extrait de GT: New Horizons (BetterQuesting 3.8 GTNH : identifiants UUID, chapitres ordonnés). */
    static final String GTNH_DB = """
            {"questDatabase:9":{
              "0:10":{"questIDLow:4":0,"questIDHigh:4":0,"preRequisites:9":{},"properties:10":{"betterquesting:10":{
                "name:8":"§6Welcome","desc:8":"Read\\nthe §lbook§r.","visibility:8":"NORMAL","icon:10":{"id:8":"minecraft:bed"}}}},
              "1:10":{"questIDLow:4":-4782315901562449638,"questIDHigh:4":-337303635474561203,
                "preRequisites:9":{"0:10":{"questIDLow:4":0,"questIDHigh:4":0}},"properties:10":{"betterquesting:10":{
                "name:8":"Steam Age","desc:8":"Boilers","visibility:8":"SECRET","icon:10":{"id:8":"gregtech:gt.blockmachines"}}}}},
             "questLines:9":{
              "0:10":{"order:3":1,"properties:10":{"betterquesting:10":{"name:8":"Tier 0.5 - Steam"}},
                      "quests:9":{"0:10":{"questIDLow:4":-4782315901562449638,"questIDHigh:4":-337303635474561203}}},
              "1:10":{"order:3":0,"properties:10":{"betterquesting:10":{"name:8":"And So, It Begins"}},
                      "quests:9":{"0:10":{"questIDLow:4":0,"questIDHigh:4":0}}}}}""";

    @TempDir
    Path dir;

    @Test
    void litLeLivreDeRlcraftDansLOrdreDuChapitreSansLesQuetesHorsDuLivre() {
        List<BetterQuesting.Quest> quests = BetterQuesting.parseQuests(J8.parseJson(RLCRAFT_DB).getAsJsonObject());
        assertEquals(2, quests.size(), "la quête 40 n'est dans aucun chapitre : invisible en jeu");
        BetterQuesting.Quest root = quests.get(0);
        assertEquals("betterquesting:quest/13", root.id);
        assertTrue(root.main);
        assertTrue(root.hidden);
        assertNull(root.parent);
        assertEquals("betterquesting.chapter.name.1", root.line);
        BetterQuesting.Quest feather = quests.get(1);
        assertEquals("betterquesting:quest/0", feather.id);
        assertEquals("betterquesting:quest/13", feather.parent);
        assertEquals("iceandfire:amphithere_feather", feather.icon);
        assertFalse(feather.main);

        Map<String, String> lang = Map.of("betterquesting.quest.name.1", "How to Tame Your Dragon Serpent Bird", "betterquesting.chapter.name.1", "A Song of Ice and Fire");
        List<XPackCore.AdvancementInfo> catalog = BetterQuesting.catalog(quests, k -> k == null ? null : lang.getOrDefault(k, k));
        assertEquals("goal", catalog.get(0).frame(), "quête principale : objectif");
        assertEquals("How to Tame Your Dragon Serpent Bird", catalog.get(1).title());
        assertEquals("A Song of Ice and Fire", catalog.get(1).tab());
        assertEquals("task", catalog.get(1).frame());
    }

    @Test
    void litLesIdentifiantsUuidEtLOrdreDesChapitresDeGtnh() {
        List<BetterQuesting.Quest> quests = BetterQuesting.parseQuests(J8.parseJson(GTNH_DB).getAsJsonObject());
        assertEquals(2, quests.size());
        assertEquals("betterquesting:quest/00000000-0000-0000-0000-000000000000", quests.get(0).id, "chapitre « order » 0 d'abord");
        assertEquals("And So, It Begins", quests.get(0).line);
        BetterQuesting.Quest steam = quests.get(1);
        assertEquals("betterquesting:quest/fb51a821-7217-474d-bda1-cd067bab111a", steam.id);
        assertEquals("betterquesting:quest/00000000-0000-0000-0000-000000000000", steam.parent);
        assertTrue(steam.hidden);
        List<XPackCore.AdvancementInfo> catalog = BetterQuesting.catalog(quests, k -> k);
        assertEquals("Welcome", catalog.get(0).title(), "sans codes de couleur");
        assertEquals("Read the book.", catalog.get(0).description(), "sur une ligne");
    }

    @Test
    void quetesTermineesParLeJoueurAvecLeurDateDansLesDeuxFormats() throws Exception {
        Path bq = Files.createDirectories(dir.resolve("betterquesting"));
        // 1.12.2 : un fichier pour tous les joueurs.
        Files.writeString(bq.resolve("QuestProgress.json"), """
                {"questProgress:9":{
                  "0:10":{"questID:3":0,"completed:9":{"0:10":{"claimed:1":0,"uuid:8":"%s","timestamp:4":1793737678698}}},
                  "1:10":{"questID:3":1,"completed:9":{"0:10":{"uuid:8":"11111111-1111-4111-8111-111111111111","timestamp:4":5}}},
                  "2:10":{"questID:3":2,"completed:9":{}}}}""".formatted(PLAYER), StandardCharsets.UTF_8);
        assertEquals(Map.of("betterquesting:quest/0", 1793737678698L), BetterQuesting.readCompleted(dir, PLAYER), "seulement les siennes");

        // GT: New Horizons : un fichier par joueur, prioritaire.
        Files.createDirectories(bq.resolve("QuestProgress"));
        Files.writeString(bq.resolve("QuestProgress").resolve(PLAYER + ".json"), """
                {"questProgress:9":{"0:10":{"questIDLow:4":0,"questIDHigh:4":0,
                  "completed:9":{"0:10":{"claimed:1":1,"timestamp:4":1793734528284,"uuid:8":"%s"}}}}}""".formatted(PLAYER), StandardCharsets.UTF_8);
        assertEquals(Map.of("betterquesting:quest/00000000-0000-0000-0000-000000000000", 1793734528284L), BetterQuesting.readCompleted(dir, PLAYER));
    }

    @Test
    void sansBetterQuestingRienNiErreur() throws Exception {
        assertTrue(BetterQuesting.readQuests(dir).isEmpty());
        assertNull(BetterQuesting.readCompleted(dir, PLAYER));
        Files.createDirectories(dir.resolve("betterquesting"));
        Files.writeString(dir.resolve("betterquesting/QuestDatabase.json"), "{ en cours d'écri", StandardCharsets.UTF_8);
        assertTrue(BetterQuesting.readQuests(dir).isEmpty(), "fichier en cours d'écriture : on réessaiera");
    }
}
