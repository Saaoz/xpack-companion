package net.xpack.companion.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Identification du modpack sans aide du créateur (#38), à partir des fichiers que les lanceurs
 * laissent dans l'instance. On regarde le dossier du jeu puis son parent (Prism range le jeu
 * dans {@code <instance>/minecraft} ou {@code <instance>/.minecraft}).
 */
public final class ModpackDetector {
    public record ModpackInfo(String source, String projectId, String versionId, String name, String version) {
        public static ModpackInfo unknown(String modsHash) {
            return new ModpackInfo("unknown", modsHash, null, null, null);
        }
    }

    private ModpackDetector() {}

    public static ModpackInfo detect(Path gameDir, List<String> loadedModIds) {
        for (Path dir : new Path[] {gameDir, gameDir.getParent()}) {
            if (dir == null) continue;
            Optional<ModpackInfo> found = modrinth(dir).or(() -> curseforgeApp(dir)).or(() -> curseforgeManifest(dir));
            if (found.isPresent()) return found.get();
        }
        return ModpackInfo.unknown(modsHash(loadedModIds));
    }

    /**
     * Pack Modrinth (.mrpack décompressé, Modrinth App). L'index ne contient pas l'identifiant du
     * projet : le serveur rapproche par nom et version.
     */
    static Optional<ModpackInfo> modrinth(Path dir) {
        return read(dir.resolve("modrinth.index.json")).map(j -> new ModpackInfo("modrinth", null, null, str(j, "name"), str(j, "versionId")));
    }

    /** Instance de l'app CurseForge. */
    static Optional<ModpackInfo> curseforgeApp(Path dir) {
        return read(dir.resolve("minecraftinstance.json")).flatMap(j -> {
            JsonObject pack = j.has("installedModpack") && j.get("installedModpack").isJsonObject() ? j.getAsJsonObject("installedModpack") : null;
            if (pack == null) return Optional.empty();
            String projectId = str(pack, "addonID");
            JsonObject file = pack.has("installedFile") && pack.get("installedFile").isJsonObject() ? pack.getAsJsonObject("installedFile") : null;
            return Optional.of(new ModpackInfo("curseforge", projectId, file == null ? null : str(file, "id"), str(j, "name"), file == null ? null : str(file, "displayName")));
        });
    }

    /** Export CurseForge (manifest.json à la racine d'un pack décompressé). */
    static Optional<ModpackInfo> curseforgeManifest(Path dir) {
        return read(dir.resolve("manifest.json")).filter(j -> "minecraftModpack".equals(str(j, "manifestType")))
                .map(j -> new ModpackInfo("curseforge", str(j, "projectID"), null, str(j, "name"), str(j, "version")));
    }

    /** Empreinte stable de la liste des mods (ordre indifférent), rapprochée côté serveur. */
    public static String modsHash(List<String> modIds) {
        String joined = modIds.stream().sorted().collect(Collectors.joining("\n"));
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<JsonObject> read(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            return el.isJsonObject() ? Optional.of(el.getAsJsonObject()) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty(); // fichier illisible : on tente la source suivante
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
