package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerStoreTest {
    @TempDir
    Path dir;

    @Test
    void leDossierSuitLEmplacementDesIdentifiants() {
        assertEquals(dir.toAbsolutePath().resolve("servers"), ServerStore.directory(dir.resolve("credentials.json")));
    }

    @Test
    void migreUneFoisLAncienFichierHorsDeConfig() throws Exception {
        Path config = dir.resolve("instance/config");
        Path old = config.resolve("xpack/servers/abc.json");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "{\"worldId\":\"x\"}");
        Path target = ServerStore.fileFor(dir.resolve("home/servers"), "abc", config);
        assertEquals(dir.resolve("home/servers/abc.json"), target);
        assertFalse(Files.exists(old));
        assertEquals("{\"worldId\":\"x\"}", Files.readString(target));
        ServerStore.fileFor(dir.resolve("home/servers"), "abc", config);
        assertTrue(Files.exists(target));
    }

    @Test
    void leNouveauFichierFaitFoiEtLAncienEstSupprime() throws Exception {
        Path config = dir.resolve("config");
        Path old = config.resolve("xpack/servers/abc.json");
        Path target = dir.resolve("s/abc.json");
        Files.createDirectories(old.getParent());
        Files.createDirectories(target.getParent());
        Files.writeString(old, "ancien");
        Files.writeString(target, "nouveau");
        ServerStore.fileFor(dir.resolve("s"), "abc", config);
        assertEquals("nouveau", Files.readString(target));
        assertFalse(Files.exists(old));
    }

    @Test
    void sansAncienFichierOuSansConfigRienNeCasse() {
        assertEquals(dir.resolve("s/zzz.json"), ServerStore.fileFor(dir.resolve("s"), "zzz", dir.resolve("config")));
        assertEquals(dir.resolve("s/zzz.json"), ServerStore.fileFor(dir.resolve("s"), "zzz", null));
    }
}
