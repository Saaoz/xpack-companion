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
    void litLeProjetDUnProfilDeLaModrinthApp() throws IOException {
        // Octets tels que relevés dans app.db (Modrinth App, novembre 2026) : valeurs texte contiguës.
        Path app = Files.createDirectories(instance.resolve("ModrinthApp"));
        Path game = Files.createDirectories(app.resolve("profiles/Cobblemon EngineMc"));
        Files.createDirectories(app.resolve("profiles/Cobblemon EngineMc 2"));
        String a = "local:e5835b96-5a3f-4abc-973d-5a91158cbe80", b = "local:d7ba6019-6c40-41ad-97e1-9c2c470e1709";
        String db = "\u0000SQLite format 3\u0000\u0007" + b + "Cobblemon EngineMc 2content-set:x\u0003"
                + a + "Cobblemon EngineMccontent-set:y\u0003installed"
                + "\u0004" + b + "modrinth_modpack4BV47HRnbdRIjRgs\u0004" + a + "modrinth_modpackZQs1BP41SatDFxac";
        Files.write(app.resolve("app.db"), db.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        var info = ModpackDetector.detect(game, List.of("cobblemon"));
        assertEquals("modrinth", info.source());
        assertEquals("ZQs1BP41", info.projectId());
        assertEquals("SatDFxac", info.versionId());
        assertEquals("Cobblemon EngineMc", info.name());
        // Lien plus récent dans le journal WAL (pack mis à jour) : il l'emporte.
        Files.writeString(app.resolve("app.db-wal"), a + "Cobblemon EngineMccontent-set:z" + a + "modrinth_modpackZQs1BP41NewVer01");
        assertEquals("NewVer01", ModpackDetector.detect(game, List.of()).versionId());
    }

    @Test
    void profilModrinthAppNonLieAUnPack() throws IOException {
        Path app = Files.createDirectories(instance.resolve("ModrinthApp"));
        Path game = Files.createDirectories(app.resolve("profiles/Mon instance"));
        Files.writeString(app.resolve("app.db"), "local:e5835b96-5a3f-4abc-973d-5a91158cbe80Mon instancecontent-set:y");
        assertEquals("unknown", ModpackDetector.detect(game, List.of("sodium")).source());
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

    /** Fixture réelle : seuls les champs de la liste blanche sortent du manifest (pas author, files, minecraft…). */
    @Test
    void fixtureReelleNeCollecteQueLaListeBlanche() throws IOException {
        Path fixture = Path.of(System.getProperty("xpack.spec"), "launcher-fixtures", "bmc4-manifest-curseforge");
        Files.copy(fixture.resolve("manifest.json"), instance.resolve("manifest.json"));
        var fields = ModpackDetector.collect(instance, "manifest.json").orElseThrow();
        assertEquals(java.util.Set.of("manifestType", "name", "version"), fields.keySet());
        assertEquals("BMC4", fields.get("name"));
    }

    @Test
    void laListeBlancheEstExplicite() {
        assertEquals(java.util.Set.of("modrinth.index.json", "profile.json", "minecraftinstance.json", "manifest.json", "instance.cfg", "instance.json"),
                ModpackDetector.WHITELIST.keySet());
        assertEquals(List.of("name", "versionId"), ModpackDetector.WHITELIST.get("modrinth.index.json"));
    }

    @Test
    void valeurNonPrimitiveIgnoreeSansErreur() throws IOException {
        Files.writeString(instance.resolve("modrinth.index.json"), "{\"name\":{},\"versionId\":[\"x\"]}");
        var info = ModpackDetector.detect(instance, List.of());
        assertEquals("modrinth", info.source());
        assertEquals(null, info.name());
        assertEquals(null, info.version());
        Files.writeString(instance.resolve("modrinth.index.json"), "{\"name\":{},\"versionId\":\"2\"}");
        assertEquals("2", ModpackDetector.detect(instance, List.of()).version());
        Files.delete(instance.resolve("modrinth.index.json"));
        Files.writeString(instance.resolve("minecraftinstance.json"), "{\"name\":{},\"installedModpack\":{\"addonID\":{},\"installedFile\":{\"id\":7,\"displayName\":[]}}}");
        var cf = ModpackDetector.detect(instance, List.of());
        assertEquals("curseforge", cf.source());
        assertEquals(null, cf.projectId());
        assertEquals("7", cf.versionId());
        assertEquals(null, cf.name());
        Files.writeString(instance.resolve("minecraftinstance.json"), "{\"installedModpack\":\"oups\"}");
        assertEquals("unknown", ModpackDetector.detect(instance, List.of()).source());
    }

    @Test
    void litUneInstancePrismGeree() throws IOException {
        Path game = Files.createDirectories(instance.resolve(".minecraft"));
        Files.writeString(instance.resolve("instance.cfg"), "[General]\nInstanceType=OneSix\nManagedPack=true\nManagedPackID=ZQs1BP41\nManagedPackName=Cobblemon\nManagedPackType=modrinth\nManagedPackVersionID=SatDFxac\nManagedPackVersionName=1.2\nname=Mon instance\nlastLaunchTime=5\n");
        var info = ModpackDetector.detect(game, List.of());
        assertEquals("modrinth", info.source());
        assertEquals("ZQs1BP41", info.projectId());
        assertEquals("SatDFxac", info.versionId());
        assertEquals("Cobblemon", info.name());
        assertEquals("1.2", info.version());
        Files.writeString(instance.resolve("instance.cfg"), "ManagedPackType=flame\nManagedPackID=925200\n");
        assertEquals("curseforge", ModpackDetector.detect(game, List.of()).source());
        Files.writeString(instance.resolve("instance.cfg"), "name=Instance libre\n");
        assertEquals("unknown", ModpackDetector.detect(game, List.of()).source());
    }

    @Test
    void litUneInstanceATLauncher() throws IOException {
        Files.writeString(instance.resolve("instance.json"), "{\"id\":\"x\",\"launcher\":{\"pack\":\"All the Mods 9\",\"version\":\"0.2.1\",\"loaderVersion\":{\"a\":1}}}");
        var info = ModpackDetector.detect(instance, List.of());
        assertEquals("atlauncher", info.source());
        assertEquals("All the Mods 9", info.name());
        assertEquals("0.2.1", info.version());
    }

    @Test
    void reponseDuServeurSurLeModpack() {
        var parse = (java.util.function.Function<String, Boolean>) j -> XPackCore.parseModpackRecognized(com.google.gson.JsonParser.parseString(j).getAsJsonObject());
        assertEquals(Boolean.TRUE, parse.apply("{\"modpack\":{\"recognized\":true}}"));
        assertEquals(Boolean.FALSE, parse.apply("{\"modpack\":{\"recognized\":false}}"));
        assertEquals(null, parse.apply("{}"));
        assertEquals(null, parse.apply("{\"modpack\":{\"recognized\":\"yes\"}}"));
        assertEquals(null, parse.apply("{\"modpack\":\"x\"}"));
    }
}
