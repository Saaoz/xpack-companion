package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class DiagnosticsFilterTest {
    private static JsonObject filter(String name, String json) {
        return J8.parseJson(Diagnostics.filterJson(name, json)).getAsJsonObject();
    }

    @Test
    void minecraftinstanceGardeLIdentiteEtLesVersionsSeulement() {
        JsonObject o = filter("minecraftinstance.json", "{\"name\":\"Pack\",\"gameVersion\":\"1.20.1\",\"installPath\":\"C:\\\\Users\\\\x\\\\cf\","
                + "\"lastPlayed\":\"2026\",\"accountId\":123,\"baseModLoader\":{\"name\":\"forge-47.2\",\"downloadUrl\":\"http://x\"},"
                + "\"installedModpack\":{\"addonID\":925200,\"secret\":\"s\",\"installedFile\":{\"id\":7,\"displayName\":\"v1\",\"downloadUrl\":\"http://y\"}}}");
        assertEquals("Pack", o.get("name").getAsString());
        assertEquals("1.20.1", o.get("gameVersion").getAsString());
        assertEquals(925200, o.getAsJsonObject("installedModpack").get("addonID").getAsInt());
        assertEquals("forge-47.2", o.getAsJsonObject("baseModLoader").get("name").getAsString());
        String s = o.toString();
        for (String bad : new String[] {"installPath", "lastPlayed", "accountId", "downloadUrl", "secret", "Users"}) assertFalse(s.contains(bad), bad);
    }

    @Test
    void manifestParcourtLesTableauxEtEcarteLaListeDesFichiers() {
        JsonObject o = filter("manifest.json", "{\"manifestType\":\"minecraftModpack\",\"name\":\"BMC4\",\"version\":\"v1\","
                + "\"minecraft\":{\"version\":\"1.20.1\",\"modLoaders\":[{\"id\":\"forge-47\",\"primary\":true,\"x\":1}]},"
                + "\"files\":[{\"projectID\":1,\"fileID\":2}],\"overrides\":\"overrides\"}");
        assertEquals("forge-47", o.getAsJsonObject("minecraft").getAsJsonArray("modLoaders").get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(o.has("files"));
        assertFalse(o.toString().contains("\"x\""));
    }

    @Test
    void valeursNonSimplesEtLonguesSontTraitees() {
        JsonObject o = filter("manifest.json", "{\"name\":{\"a\":1},\"version\":\"" + "v".repeat(500) + "\",\"manifestType\":[1]}");
        assertFalse(o.has("name"));
        assertFalse(o.has("manifestType"));
        assertTrue(o.get("version").getAsString().length() <= 201);
    }

    @Test
    void fichierIllisibleOuInconnuNEstJamaisRecopieTelQuel() {
        String broken = Diagnostics.filterJson("manifest.json", "{pas du json token=SECRET");
        assertTrue(broken.contains("illisible"));
        assertFalse(broken.contains("SECRET"));
        assertEquals("{}\n", Diagnostics.filterJson("autre.json", "{\"token\":\"SECRET\"}"));
    }
}
