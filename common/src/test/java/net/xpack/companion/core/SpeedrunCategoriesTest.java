package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpeedrunCategoriesTest {
    private static JsonObject body(String json) {
        return ApiClient.parse(json);
    }

    private static final String API = "[{\"modpackId\":\"mr-abc\",\"modpackName\":\"Abc\",\"categories\":["
            + "{\"id\":\"any\",\"name\":\"Any%\",\"finish\":{\"type\":\"advancement\",\"id\":\"minecraft:end/kill_dragon\"}},"
            + "{\"id\":\"100\",\"name\":\"100 %\",\"finish\":{\"type\":\"allAchievements\"}}],\"rules\":null},"
            + "{\"modpackId\":\"cf-42\",\"modpackName\":\"Autre\",\"categories\":[{\"id\":\"x\",\"name\":\"Autre\",\"finish\":{\"type\":\"stat\",\"stat\":\"a\",\"min\":1}}]}]";

    @Test
    void unTableauALaRacineDeLaReponseEstLisible() {
        JsonObject b = body(API);
        assertTrue(b.has(SpeedrunCategories.ITEMS));
        assertEquals(1, SpeedrunCategories.parse(b, "cf-42").size());
    }

    @Test
    void seulesLesCategoriesDuModpackDetecteSontProposees() {
        List<SpeedrunCategories.Category> cats = SpeedrunCategories.parse(body(API), "mr-abc");
        assertEquals(2, cats.size());
        assertEquals("any", cats.get(0).id);
        assertEquals("Any%", cats.get(0).name);
        assertEquals("minecraft:end/kill_dragon", cats.get(0).advancement);
        assertNull(cats.get(1).advancement); // tous les succès : pas de progrès unique
        assertEquals("pack:any", cats.get(0).presetId());
        assertEquals(1, cats.get(0).steps().size());
        assertEquals("Any%", cats.get(0).steps().get(0));
        assertTrue(SpeedrunCategories.parse(body(API), "mr-inconnu").isEmpty());
        assertTrue(SpeedrunCategories.parse(body(API), null).isEmpty());
    }

    @Test
    void uneReponseAbimeeOuVideDonneAucunePreselection() {
        assertTrue(SpeedrunCategories.parse(new JsonObject(), "mr-abc").isEmpty());
        assertTrue(SpeedrunCategories.parse(body("[]"), "mr-abc").isEmpty());
        assertTrue(SpeedrunCategories.parse(body("n'importe quoi"), "mr-abc").isEmpty());
        assertTrue(SpeedrunCategories.parse(body("{\"error\":\"x\"}"), "mr-abc").isEmpty());
        assertTrue(SpeedrunCategories.parse(body("[{\"modpackId\":\"mr-abc\",\"categories\":\"oups\"}]"), "mr-abc").isEmpty());
    }

    @Test
    void lesCategoriesInvalidesOuEnDoubleSontIgnoreesEtLesNomsNettoyes() {
        String json = "[{\"modpackId\":\"mr-abc\",\"categories\":["
                + "{\"id\":\"BAD ID\",\"name\":\"Mauvais\"},"
                + "{\"id\":\"vide\",\"name\":\"   \"},"
                + "{\"id\":\"ok\",\"name\":\"  Nom\\n\\tavec   espaces  \"},"
                + "{\"id\":\"ok\",\"name\":\"Doublon\"},"
                + "{\"id\":\"long\",\"name\":\"0123456789012345678901234567890123456789\"},"
                + "\"pas un objet\"]}]";
        List<SpeedrunCategories.Category> cats = SpeedrunCategories.parse(body(json), "mr-abc");
        assertEquals(2, cats.size());
        assertEquals("Nom avec espaces", cats.get(0).name);
        assertEquals(32, cats.get(1).name.length());
    }

    @Test
    void auPlusDouzeCategories() {
        StringBuilder sb = new StringBuilder("[{\"modpackId\":\"mr-abc\",\"categories\":[");
        for (int i = 0; i < 20; i++) sb.append(i == 0 ? "" : ",").append("{\"id\":\"c").append(i).append("\",\"name\":\"C").append(i).append("\"}");
        sb.append("]}]");
        assertEquals(SpeedrunCategories.MAX_CATEGORIES, SpeedrunCategories.parse(body(sb.toString()), "mr-abc").size());
    }

    @Test
    void lesPreselectionsDuModSontToujoursLaEtLesCategoriesSAjoutent() {
        List<SpeedrunCategories.Category> cats = SpeedrunCategories.parse(body(API), "mr-abc");
        List<String> ids = SpeedrunCategories.presetIds(cats);
        assertEquals(SpeedrunPresets.CUSTOM, ids.get(0));
        assertTrue(ids.containsAll(SpeedrunPresets.ids()));
        assertEquals("pack:any", ids.get(ids.size() - 2));
        assertEquals("pack:100", ids.get(ids.size() - 1));
        assertEquals(SpeedrunPresets.ids().size(), SpeedrunCategories.presetIds(java.util.Collections.<SpeedrunCategories.Category>emptyList()).size());
        assertNotNull(SpeedrunCategories.find(cats, "pack:100"));
        assertNull(SpeedrunCategories.find(cats, "pack:inconnu"));
        assertNull(SpeedrunCategories.find(cats, "classic"));
    }

    @Test
    void unIdentifiantPackSeReconnaitMemeSansLesCategories() {
        assertTrue(SpeedrunCategories.isPackPreset("pack:any"));
        assertFalse(SpeedrunCategories.isPackPreset("pack:"));
        assertFalse(SpeedrunCategories.isPackPreset("classic"));
        assertFalse(SpeedrunCategories.isPackPreset(null));
    }

    @Test
    void lIdentifiantDuModpackSuitLesReglesDuServeur() {
        assertEquals("mr-abc", SpeedrunCategories.modpackId(new ModpackDetector.ModpackInfo("modrinth", "abc", "v1", "Nom", "1.0")));
        assertEquals("cf-42", SpeedrunCategories.modpackId(new ModpackDetector.ModpackInfo("curseforge", "42", null, "Nom", null)));
        // Modrinth sans projet (index .mrpack) : le nom, sans accent ni ponctuation.
        assertEquals("pack-mon-super-pack", SpeedrunCategories.modpackId(new ModpackDetector.ModpackInfo("modrinth", null, null, "Mon Süper  Pack!", null)));
        // Empreinte de mods seule : pas identifiable, donc pas de catégories.
        assertNull(SpeedrunCategories.modpackId(ModpackDetector.ModpackInfo.unknown("sha256:abcdef0123456789abcdef")));
        assertNull(SpeedrunCategories.modpackId(null));
    }
}
