package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModpackDetectorTest {
    @TempDir
    Path instance;

    /** Fixture réelle (spec/launcher-fixtures/bmc4-manifest-curseforge) : manifest sans id de projet. */
    @Test
    void fixtureReelleBmc4NeDonneQueLeNomEtLaVersion() throws IOException {
        Path fixture = Path.of(System.getProperty("xpack.spec"), "launcher-fixtures", "bmc4-manifest-curseforge");
        Files.copy(fixture.resolve("manifest.json"), instance.resolve("manifest.json"));
        var info = ModpackDetector.detect(instance, List.of());
        assertEquals("curseforge", info.source());
        assertEquals(null, info.projectId(), "le manifest n'a pas d'id de projet : la résolution devra se faire côté serveur");
        assertEquals("BMC4", info.name());
        assertEquals("v62hf", info.version());
    }

    @Test
    void litUnPackModrinth() throws IOException {
        Files.writeString(instance.resolve("modrinth.index.json"), "{\"name\":\"Engrenages\",\"versionId\":\"1.4.0\",\"files\":[]}");
        var info = ModpackDetector.detect(instance, List.of());
        assertEquals("modrinth", info.source());
        assertEquals("Engrenages", info.name());
        assertEquals("1.4.0", info.version());
    }

    @Test
    void litUneInstanceCurseForgeDepuisLeDossierParent() throws IOException {
        Path game = Files.createDirectories(instance.resolve("minecraft"));
        Files.writeString(instance.resolve("minecraftinstance.json"),
                "{\"name\":\"All the Mods\",\"installedModpack\":{\"addonID\":925200,\"installedFile\":{\"id\":5550000,\"displayName\":\"ATM 1.2\"}}}");
        var info = ModpackDetector.detect(game, List.of());
        assertEquals("curseforge", info.source());
        assertEquals("925200", info.projectId());
        assertEquals("5550000", info.versionId());
    }

    @Test
    void litUnManifestCurseForge() throws IOException {
        Files.writeString(instance.resolve("manifest.json"), "{\"manifestType\":\"minecraftModpack\",\"projectID\":42,\"name\":\"Pack\",\"version\":\"2\"}");
        var info = ModpackDetector.detect(instance, List.of());
        assertEquals("42", info.projectId());
    }

    @Test
    void sinonEmpreinteStableDeLaListeDeMods() throws IOException {
        Files.writeString(instance.resolve("modrinth.index.json"), "pas du json");
        var a = ModpackDetector.detect(instance, List.of("create", "jei", "minecraft"));
        var b = ModpackDetector.detect(instance, List.of("minecraft", "create", "jei"));
        assertEquals("unknown", a.source());
        assertEquals(a.projectId(), b.projectId());
        assertTrue(a.projectId().startsWith("sha256:"));
    }
}
