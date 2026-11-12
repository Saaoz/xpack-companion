package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpeedrunExportTest {
    private long now = 1_700_000_000_000L;

    private SpeedrunExport.Run run() {
        SpeedrunTimer t = new SpeedrunTimer(() -> now);
        t.setSteps(Arrays.asList("Village", "Nether, vite & bien"));
        t.start();
        for (int i = 0; i < 1200; i++) t.gameTick();
        now += 61_000;
        t.split();
        for (int i = 0; i < 600; i++) t.gameTick();
        now += 31_500;
        t.split();
        return SpeedrunExport.from(t, "Classique", "1.21.1", "Fabric", "Mon pack", now);
    }

    @Test
    void seulUnChronoTermineSExporte() {
        SpeedrunTimer t = new SpeedrunTimer(() -> now);
        assertNull(SpeedrunExport.from(t, "Libre", "1.21.1", "Fabric", null, now));
        t.start();
        assertNull(SpeedrunExport.from(t, "Libre", "1.21.1", "Fabric", null, now));
        t.lose();
        assertNull(SpeedrunExport.from(t, "Libre", "1.21.1", "Fabric", null, now));
    }

    @Test
    void jsonDitQueLeChronoNEstPasVerifie() {
        JsonObject o = JsonParser.parseString(SpeedrunExport.json(run())).getAsJsonObject();
        assertEquals("free", o.get("mode").getAsString());
        assertEquals(false, o.get("verified").getAsBoolean());
        assertEquals(90_000, o.get("igtMs").getAsLong());
        assertEquals(92_500, o.get("rtaMs").getAsLong());
        assertEquals("Mon pack", o.get("modpack").getAsString());
        assertEquals(2, o.getAsJsonArray("splits").size());
        JsonObject second = o.getAsJsonArray("splits").get(1).getAsJsonObject();
        assertEquals(30_000, second.get("segmentIgtMs").getAsLong());
        assertEquals(31_500, second.get("segmentRtaMs").getAsLong());
    }

    @Test
    void csvProtegeLesVirgulesEtAjouteLeTotal() {
        String[] lines = SpeedrunExport.csv(run()).split("\n");
        assertEquals("step,igt_ms,rta_ms,segment_igt_ms,segment_rta_ms", lines[0]);
        assertEquals("Village,60000,61000,60000,61000", lines[1]);
        assertTrue(lines[2].startsWith("\"Nether, vite & bien\",90000,92500,"));
        assertEquals("TOTAL,90000,92500,0,0", lines[3]);
    }

    @Test
    void lssEstUnXmlLiveSplitAvecLesNomsEchappes() {
        String xml = SpeedrunExport.lss(run());
        assertTrue(xml.contains("<GameName>Minecraft</GameName>"));
        assertTrue(xml.contains("<CategoryName>Classique</CategoryName>"));
        assertTrue(xml.contains("<Name>Nether, vite &amp; bien</Name>"));
        assertTrue(xml.contains("<GameTime>00:01:30.0000000</GameTime>"));
        assertTrue(xml.contains("<RealTime>00:01:32.5000000</RealTime>"));
        // XML bien formé
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        try {
            f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError("lss mal formé : " + e.getMessage());
        }
    }

    @Test
    void leTexteSpeedrunComContientLesTempsEtLaMention() {
        String text = SpeedrunExport.text(run());
        assertTrue(text.contains("Minecraft 1.21.1 (Fabric) - modpack: Mon pack"));
        assertTrue(text.contains("IGT: 01:30.000 | RTA: 01:32.500"));
        assertTrue(text.contains("- Village: 01:00.000 / 01:01.000"));
        assertTrue(text.contains("not verified by XPack"));
    }

    @Test
    void writeCreeQuatreFichiers(@TempDir Path dir) throws Exception {
        List<Path> files = SpeedrunExport.write(dir.resolve("sous-dossier"), run());
        assertEquals(4, files.size());
        for (Path p : files) assertTrue(Files.size(p) > 0, p.toString());
        assertTrue(files.get(0).getFileName().toString().endsWith(".json"));
        assertTrue(files.get(2).getFileName().toString().endsWith(".lss"));
    }
}
