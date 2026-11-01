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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            Optional<ModpackInfo> found = modrinth(dir).or(() -> modrinthApp(dir)).or(() -> curseforgeApp(dir)).or(() -> curseforgeManifest(dir));
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

    /**
     * Profil de la Modrinth App ({@code ModrinthApp/profiles/<dossier>}). L'app ne laisse aucun index
     * dans le profil : l'ancienne version écrivait {@code profile.json}, l'actuelle range le lien vers
     * le pack dans sa base SQLite {@code app.db}. Sans pilote SQLite, on y cherche les deux lignes,
     * dont les valeurs texte sont contiguës : {@code instances} (id puis dossier du profil) et
     * {@code instance_links} (id, {@code modrinth_modpack}, projet, version). Le journal WAL d'abord :
     * il porte les écritures les plus récentes.
     */
    static Optional<ModpackInfo> modrinthApp(Path dir) {
        Path profiles = dir.getParent();
        if (profiles == null || profiles.getFileName() == null || !profiles.getFileName().toString().equals("profiles")) return Optional.empty();
        String folder = dir.getFileName().toString();
        Optional<ModpackInfo> legacy = read(dir.resolve("profile.json")).flatMap(j -> {
            JsonObject meta = j.has("metadata") && j.get("metadata").isJsonObject() ? j.getAsJsonObject("metadata") : null;
            JsonObject linked = meta != null && meta.has("linked_data") && meta.get("linked_data").isJsonObject() ? meta.getAsJsonObject("linked_data") : null;
            String project = linked == null ? null : str(linked, "project_id");
            return project == null ? Optional.empty() : Optional.of(new ModpackInfo("modrinth", project, str(linked, "version_id"), str(meta, "name"), null));
        });
        if (legacy.isPresent()) return legacy;
        Path app = profiles.getParent();
        if (app == null) return Optional.empty();
        for (String file : new String[] {"app.db-wal", "app.db"}) {
            String bytes = latin1(app.resolve(file));
            if (bytes == null) continue;
            String instance = instanceId(bytes, folder);
            if (instance == null) continue;
            Matcher m = Pattern.compile(Pattern.quote(instance) + "modrinth_modpack([0-9A-Za-z]{8})([0-9A-Za-z]{8})").matcher(bytes);
            String project = null, version = null;
            while (m.find()) {
                project = m.group(1);
                version = m.group(2);
            }
            if (project != null) return Optional.of(new ModpackInfo("modrinth", project, version, folder, null));
        }
        return Optional.empty();
    }

    /** Taille maximale lue de la base de la Modrinth App (au-delà, on renonce). */
    private static final long MAX_APP_DB_BYTES = 64L * 1024 * 1024;

    /**
     * Id de l'instance dont le dossier est {@code folder}. Un autre profil peut commencer par le même
     * nom (« Pack » et « Pack 2 ») : on exige ensuite la colonne suivante, le jeu de contenu appliqué.
     */
    static String instanceId(String bytes, String folder) {
        Matcher m = Pattern.compile("(local:[0-9a-f-]{36})" + Pattern.quote(folder) + "(content-set:)?").matcher(bytes);
        String loose = null;
        while (m.find()) {
            if (m.group(2) != null) return m.group(1);
            if (loose == null) loose = m.group(1);
            else if (!loose.equals(m.group(1))) loose = "";
        }
        return loose == null || loose.isEmpty() ? null : loose;
    }

    private static String latin1(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_APP_DB_BYTES) return null;
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        } catch (IOException | RuntimeException e) {
            return null; // base verrouillée ou illisible : modpack non identifié, rien de plus
        }
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
