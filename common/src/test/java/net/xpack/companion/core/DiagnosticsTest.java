package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticsTest {
    @TempDir
    Path dir;

    @Test
    void copieLesFichiersDuLanceurSansCheminNiNomDUtilisateur() throws Exception {
        String home = System.getProperty("user.home");
        Path instance = dir.resolve("instances/MonPack");
        Path game = instance.resolve("minecraft");
        Files.createDirectories(game);
        Files.writeString(instance.resolve("instance.cfg"), String.join("\n",
                "InstanceType=OneSix",
                "JavaPath=" + home + "\\java\\bin\\javaw.exe",
                "ManagedPack=true",
                "ManagedPackID=1KVo5zza",
                "ManagedPackType=modrinth",
                "name=MonPack"));
        Files.writeString(game.resolve("minecraftinstance.json"),
                "{\"installPath\":\"" + home.replace("\\", "\\\\") + "\\\\curseforge\",\"installedModpack\":{\"addonID\":925200}}");

        Clock clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
        Diagnostics.Result r = Diagnostics.write(game, dir.resolve("out"), "XPack 0.1.0", List.of("sodium", "create"), clock);

        assertEquals(List.of("jeu/minecraftinstance.json", "parent/instance.cfg"), r.found());
        String cfg = Files.readString(r.folder().resolve("parent-instance.cfg"));
        assertTrue(cfg.contains("ManagedPackID=1KVo5zza"));
        assertTrue(cfg.contains("name=MonPack"));
        assertFalse(cfg.contains("JavaPath"), "les chemins d'instance.cfg ne sortent pas");
        String cf = Files.readString(r.folder().resolve("jeu-minecraftinstance.json"));
        assertFalse(cf.contains("installPath"), "les champs hors liste blanche ne sortent pas");
        assertFalse(cf.toLowerCase().contains(home.replace("\\", "\\\\").toLowerCase()));
        assertTrue(cf.contains("925200"));
        assertEquals("create\nsodium\n", Files.readString(r.folder().resolve("mods.txt")));
        assertTrue(Files.readString(r.folder().resolve("LISEZMOI.txt")).contains("parent/instance.cfg"));
        assertTrue(r.folder().getFileName().toString().endsWith("20261026-120000"));
    }

    @Test
    void sansFichierDeLanceurLeDiagnosticLeDit() throws Exception {
        Path game = dir.resolve("vide");
        Files.createDirectories(game);
        Diagnostics.Result r = Diagnostics.write(game, dir.resolve("out"), "XPack", List.of(), Clock.systemUTC());
        assertTrue(r.found().isEmpty());
        assertTrue(Files.readString(r.folder().resolve("LISEZMOI.txt")).contains("(aucun)"));
    }
}
