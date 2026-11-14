package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CredentialsSaveTest {
    private static final String UUID = "11111111-2222-3333-4444-555555555555";

    @TempDir
    Path dir;

    @Test
    void enregistreEtRelitSansLaisserDeTmp() throws Exception {
        Path file = dir.resolve("sub/credentials.json");
        new Credentials(file).put("https://api.example/", UUID, "Joueur", "jeton-secret");
        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.exists(dir.resolve("sub/credentials.json.tmp")));
        assertEquals("jeton-secret", new Credentials(file).token("https://api.example", UUID));
    }

    @Test
    void ecraseUnFichierEtUnTmpResiduel() throws Exception {
        Path file = dir.resolve("credentials.json");
        Files.writeString(dir.resolve("credentials.json.tmp"), "reste");
        Credentials c = new Credentials(file);
        c.put("u", UUID, "A", "t1");
        c.put("u", UUID, "A", "t2");
        assertEquals("t2", new Credentials(file).token("u", UUID));
    }

    @Test
    void fichierReserveAuProprietaireSousPosix() throws Exception {
        if (!dir.getFileSystem().supportedFileAttributeViews().contains("posix")) return; // Windows : le dossier du compte est déjà privé
        Path file = dir.resolve("credentials.json");
        new Credentials(file).put("u", UUID, "A", "t");
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
    }
}
