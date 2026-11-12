package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LssImportTest {
    private long now = 1_700_000_000_000L;

    private String exported() {
        SpeedrunTimer t = new SpeedrunTimer(() -> now);
        t.setSteps(Arrays.asList("Village", "Nether & Co"));
        t.start();
        for (int i = 0; i < 1200; i++) t.gameTick();
        now += 61_000;
        t.split();
        for (int i = 0; i < 600; i++) t.gameTick();
        now += 31_500;
        t.split();
        return SpeedrunExport.lss(SpeedrunExport.from(t, "Classique", "1.21.1", "Fabric", null, now));
    }

    @Test
    void relitCeQueLExportEcrit() throws Exception {
        LssImport.Result r = LssImport.parse(exported().getBytes(StandardCharsets.UTF_8));
        assertEquals("Classique", r.category);
        assertEquals(Arrays.asList("Village", "Nether & Co"), r.steps);
        assertEquals(Arrays.asList(1200L, 1800L), r.bestTicks); // 60 s et 90 s de temps de jeu, en ticks
    }

    @Test
    void sansMeilleurTempsCompletLesEtapesSeulesSontLues() throws Exception {
        String xml = "<Run version=\"1.7.0\"><CategoryName>Any%</CategoryName><Segments>"
                + "<Segment><Name>A</Name></Segment><Segment><Name></Name></Segment></Segments></Run>";
        LssImport.Result r = LssImport.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("A", "Étape 2"), r.steps);
        assertTrue(r.bestTicks.isEmpty());
    }

    @Test
    void refuseCeQuiNEstPasUnLss() {
        assertEquals("not_xml", assertThrows(LssImport.LssException.class, () -> LssImport.parse("pas du xml".getBytes(StandardCharsets.UTF_8))).getMessage());
        assertEquals("not_lss", assertThrows(LssImport.LssException.class, () -> LssImport.parse("<Autre/>".getBytes(StandardCharsets.UTF_8))).getMessage());
        assertEquals("no_segments", assertThrows(LssImport.LssException.class, () -> LssImport.parse("<Run><Segments/></Run>".getBytes(StandardCharsets.UTF_8))).getMessage());
    }

    @Test
    void refuseUneDtdPourEviterLesEntitesExternes() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE Run [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><Run><CategoryName>&x;</CategoryName>"
                + "<Segments><Segment><Name>A</Name></Segment></Segments></Run>";
        assertEquals("not_xml", assertThrows(LssImport.LssException.class, () -> LssImport.parse(xxe.getBytes(StandardCharsets.UTF_8))).getMessage());
    }

    @Test
    void refuseUnFichierTropGros(@TempDir Path dir) throws Exception {
        Path big = dir.resolve("gros.lss");
        Files.write(big, new byte[(int) LssImport.MAX_BYTES + 1]);
        assertEquals("too_big", assertThrows(LssImport.LssException.class, () -> LssImport.read(big)).getMessage());
    }

    @Test
    void lesTempsSeLisentAvecOuSansFraction() {
        assertEquals(Long.valueOf(5_432_100), LssImport.parseTime("01:30:32.1000000"));
        assertEquals(Long.valueOf(61_000), LssImport.parseTime("00:01:01"));
        assertEquals(null, LssImport.parseTime(""));
        assertEquals(null, LssImport.parseTime("abc"));
    }
}
