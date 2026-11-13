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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /**
     * LISTE BLANCHE (#4) : les seuls champs lus dans les fichiers du lanceur, par fichier. Un chemin
     * pointé ({@code installedModpack.addonID}) désigne une valeur simple (texte, nombre, booléen) ;
     * un objet ou un tableau à cet endroit est ignoré. Rien d'autre n'est lu ni envoyé : toute
     * extension de cette liste doit mettre à jour la section « Tes données » du README.
     * Le détecteur ne fait que COLLECTER ; c'est le serveur XPack qui interprète.
     */
    public static final Map<String, List<String>> WHITELIST;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("modrinth.index.json", Arrays.asList("name", "versionId"));
        m.put("profile.json", Arrays.asList("metadata.name", "metadata.linked_data.project_id", "metadata.linked_data.version_id"));
        m.put("minecraftinstance.json", Arrays.asList("name", "installedModpack.addonID", "installedModpack.installedFile.id", "installedModpack.installedFile.displayName"));
        m.put("manifest.json", Arrays.asList("manifestType", "projectID", "name", "version"));
        // Prism / MultiMC : fichier INI, seules ces clés sont lues (les autres lignes sont sautées).
        m.put("instance.cfg", Arrays.asList("ManagedPackType", "ManagedPackID", "ManagedPackName", "ManagedPackVersionID", "ManagedPackVersionName"));
        m.put("instance.json", Arrays.asList("launcher.pack", "launcher.version")); // ATLauncher
        // app.db (Modrinth App) n'est pas du JSON : on n'y cherche que la ligne « instance_links »
        // du profil (projet et version du pack), voir modrinthApp.
        WHITELIST = Collections.unmodifiableMap(m);
    }

    public static final class ModpackInfo {
        private final String source;
        private final String projectId;
        private final String versionId;
        private final String name;
        private final String version;

        public ModpackInfo(String source, String projectId, String versionId, String name, String version) {
            this.source = source;
            this.projectId = projectId;
            this.versionId = versionId;
            this.name = name;
            this.version = version;
        }

        public String source() {
            return source;
        }

        public String projectId() {
            return projectId;
        }

        public String versionId() {
            return versionId;
        }

        public String name() {
            return name;
        }

        public String version() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ModpackInfo)) return false;
            ModpackInfo other = (ModpackInfo) o;
            return java.util.Objects.equals(source, other.source) && java.util.Objects.equals(projectId, other.projectId) && java.util.Objects.equals(versionId, other.versionId) && java.util.Objects.equals(name, other.name) && java.util.Objects.equals(version, other.version);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(source, projectId, versionId, name, version);
        }

        @Override
        public String toString() {
            return "ModpackInfo[" + "source=" + source + ", projectId=" + projectId + ", versionId=" + versionId + ", name=" + name + ", version=" + version + "]";
        }

        public static ModpackInfo unknown(String modsHash) {
            return new ModpackInfo("unknown", modsHash, null, null, null);
        }

    }

    private ModpackDetector() {}

    public static ModpackInfo detect(Path gameDir, List<String> loadedModIds) {
        for (Path dir : new Path[] {gameDir, gameDir.getParent()}) {
            if (dir == null) continue;
            Optional<ModpackInfo> found = J8.or(J8.or(J8.or(J8.or(J8.or(modrinth(dir), () -> modrinthApp(dir)), () -> curseforgeApp(dir)), () -> curseforgeManifest(dir)), () -> prism(dir)), () -> atlauncher(dir));
            if (found.isPresent()) return found.get();
        }
        return ModpackInfo.unknown(modsHash(loadedModIds));
    }

    /**
     * Pack Modrinth (.mrpack décompressé, Modrinth App). L'index ne contient pas l'identifiant du
     * projet : le serveur rapproche par nom et version.
     */
    static Optional<ModpackInfo> modrinth(Path dir) {
        return collect(dir, "modrinth.index.json").map(f -> new ModpackInfo("modrinth", null, null, f.get("name"), f.get("versionId")));
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
        Optional<ModpackInfo> legacy = collect(dir, "profile.json").flatMap(f -> {
            String project = f.get("metadata.linked_data.project_id");
            return project == null ? Optional.empty() : Optional.of(new ModpackInfo("modrinth", project, f.get("metadata.linked_data.version_id"), f.get("metadata.name"), null));
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
        return collect(dir, "minecraftinstance.json").flatMap(f -> {
            String projectId = f.get("installedModpack.addonID");
            if (projectId == null && f.get("installedModpack.installedFile.id") == null) return Optional.empty();
            return Optional.of(new ModpackInfo("curseforge", projectId, f.get("installedModpack.installedFile.id"), f.get("name"), f.get("installedModpack.installedFile.displayName")));
        });
    }

    /** Export CurseForge (manifest.json à la racine d'un pack décompressé). */
    static Optional<ModpackInfo> curseforgeManifest(Path dir) {
        return collect(dir, "manifest.json").filter(f -> "minecraftModpack".equals(f.get("manifestType")))
                .map(f -> new ModpackInfo("curseforge", f.get("projectID"), null, f.get("name"), f.get("version")));
    }

    /**
     * Instance Prism / MultiMC : {@code instance.cfg} (INI) porte l'identité du pack géré par le lanceur
     * (« Managed pack »). {@code modrinth} et {@code flame} (CurseForge) reprennent les sources connues du serveur.
     */
    static Optional<ModpackInfo> prism(Path dir) {
        return collectIni(dir.resolve("instance.cfg"), WHITELIST.get("instance.cfg")).flatMap(f -> {
            String type = f.get("ManagedPackType"), id = f.get("ManagedPackID");
            if (type == null || id == null) return Optional.empty();
            String source = "flame".equals(type) ? "curseforge" : "modrinth".equals(type) ? "modrinth" : null;
            if (source == null) return Optional.empty();
            return Optional.of(new ModpackInfo(source, id, f.get("ManagedPackVersionID"), f.get("ManagedPackName"), f.get("ManagedPackVersionName")));
        });
    }

    /** Instance ATLauncher : nom et version du pack ({@code instance.json}, section {@code launcher}). */
    static Optional<ModpackInfo> atlauncher(Path dir) {
        return collect(dir, "instance.json").flatMap(f -> {
            String name = f.get("launcher.pack");
            return name == null ? Optional.empty() : Optional.of(new ModpackInfo("atlauncher", null, null, name, f.get("launcher.version")));
        });
    }

    /**
     * Valeurs de la liste blanche de {@code fileName} dans {@code dir}, par chemin pointé. Une valeur
     * absente, nulle ou non simple (objet, tableau) est sautée sans erreur.
     */
    static Optional<Map<String, String>> collect(Path dir, String fileName) {
        List<String> paths = WHITELIST.get(fileName);
        if (paths == null) return Optional.empty();
        return read(dir.resolve(fileName)).map(j -> extract(j, paths));
    }

    static Map<String, String> extract(JsonObject root, List<String> paths) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String path : paths) {
            JsonElement e = root;
            for (String key : path.split("\\.")) {
                e = e != null && e.isJsonObject() ? e.getAsJsonObject().get(key) : null;
            }
            if (e != null && e.isJsonPrimitive()) out.put(path, e.getAsString());
        }
        return out;
    }

    /** Clés {@code cle=valeur} de la liste blanche dans un fichier INI ; les autres lignes ne sont pas conservées. */
    static Optional<Map<String, String>> collectIni(Path file, List<String> keys) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            Map<String, String> out = new LinkedHashMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                if (keys.contains(key)) out.put(key, line.substring(eq + 1).trim());
            }
            return Optional.of(out);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Empreinte stable de la liste des mods (ordre indifférent), rapprochée côté serveur. */
    public static String modsHash(List<String> modIds) {
        String joined = modIds.stream().sorted().collect(Collectors.joining("\n"));
        try {
            return "sha256:" + J8.hex(MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<JsonObject> read(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = J8.parseJson(r);
            return el.isJsonObject() ? Optional.of(el.getAsJsonObject()) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty(); // fichier illisible : on tente la source suivante
        }
    }
}
