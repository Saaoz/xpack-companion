package net.xpack.companion.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jetons du mod, rangés <b>hors de l'instance Minecraft</b>, dans le dossier de l'utilisateur.
 *
 * <p>Le dossier {@code config/} d'une instance part dans les exports de modpack (CurseForge,
 * Modrinth, Prism), les archives partagées et les dépôts Git des créateurs : un jeton qui s'y
 * trouve finit publié. Ici, il reste sur la machine, il est partagé par toutes les instances (on
 * ne lie son compte qu'une fois) et il est indexé par serveur XPack et par compte Minecraft.
 */
public final class Credentials {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Une liaison : compte Minecraft → jeton du mod. */
    public static final class Entry {
        public String token;
        public String minecraftName;
    }

    private static final class FileData {
        /** apiUrl → (UUID Minecraft sans tirets → liaison). */
        Map<String, Map<String, Entry>> servers = new LinkedHashMap<>();
    }

    private final Path file;
    private FileData data;

    public Credentials(Path file) {
        this.file = file;
        this.data = load(file);
    }

    public Path file() {
        return file;
    }

    /**
     * Emplacement par défaut, propre à l'utilisateur et non à l'instance. Surcharge possible avec
     * {@code -Dxpack.credentials=<fichier>} (tests, installations portables).
     */
    public static Path defaultLocation() {
        String override = System.getProperty("xpack.credentials");
        if (override != null && !J8.isBlank(override)) return java.nio.file.Paths.get(override);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        Path dir;
        if (os.contains("win") && System.getenv("APPDATA") != null) dir = java.nio.file.Paths.get(System.getenv("APPDATA"), "XPack");
        else if (os.contains("mac")) dir = java.nio.file.Paths.get(home, "Library", "Application Support", "XPack");
        else if (System.getenv("XDG_CONFIG_HOME") != null) dir = java.nio.file.Paths.get(System.getenv("XDG_CONFIG_HOME"), "xpack");
        else dir = java.nio.file.Paths.get(home, ".config", "xpack");
        return dir.resolve("credentials.json");
    }

    public synchronized String token(String apiUrl, String minecraftUuid) {
        Entry e = entries(apiUrl).get(XPackCore.normalizeUuid(minecraftUuid));
        return e == null ? null : e.token;
    }

    public synchronized void put(String apiUrl, String minecraftUuid, String minecraftName, String token) {
        Entry e = new Entry();
        e.token = token;
        e.minecraftName = minecraftName;
        data.servers.computeIfAbsent(key(apiUrl), k -> new LinkedHashMap<>()).put(XPackCore.normalizeUuid(minecraftUuid), e);
        save();
    }

    /** Oublie la liaison ; renvoie le jeton retiré (pour le révoquer côté serveur), ou null. */
    public synchronized String remove(String apiUrl, String minecraftUuid) {
        Entry e = entries(apiUrl).remove(XPackCore.normalizeUuid(minecraftUuid));
        if (e != null) save();
        return e == null ? null : e.token;
    }

    /** Pseudos Minecraft liés sur ce serveur XPack (affichés dans les réglages). */
    public synchronized List<String> linkedNames(String apiUrl) {
        return entries(apiUrl).values().stream().map(e -> e.minecraftName == null ? "?" : e.minecraftName).collect(java.util.stream.Collectors.toList());
    }

    private Map<String, Entry> entries(String apiUrl) {
        return data.servers.getOrDefault(key(apiUrl), J8.mapOf());
    }

    private static String key(String apiUrl) {
        String u = apiUrl == null ? "" : apiUrl.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static FileData load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                FileData d = GSON.fromJson(J8.readString(file, StandardCharsets.UTF_8), FileData.class);
                if (d != null && d.servers != null) return d;
            }
        } catch (IOException | RuntimeException e) {
            XLog.warn("fichier de liaison illisible", e);
            // fichier illisible : on repart sans liaison (le joueur reliera son compte)
        }
        return new FileData();
    }

    private void save() {
        Path tmp = null;
        try {
            Files.createDirectories(file.getParent());
            // Écriture atomique : un plantage ne laisse jamais un fichier à moitié écrit.
            tmp = file.resolveSibling(file.getFileName() + ".tmp");
            createPrivate(tmp);
            Files.write(tmp, GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException | AccessDeniedException e) {
                // Système de fichiers sans déplacement atomique : repli non atomique.
                XLog.warn("déplacement atomique du fichier de liaison impossible, repli", e);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            XLog.warn("liaison du compte non enregistrée", e);
            // disque indisponible : la liaison reste valable pour cette session
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // rien de plus à faire
                }
            }
        }
    }

    /**
     * Crée le fichier temporaire réservé à l'utilisateur dès sa création (Linux, macOS) : aucune
     * fenêtre où le jeton serait lisible par les autres comptes. Sans permissions POSIX (Windows,
     * où %APPDATA% est déjà privé), création ordinaire.
     */
    private static void createPrivate(Path tmp) throws IOException {
        Files.deleteIfExists(tmp); // reste d'une écriture interrompue
        try {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            Files.createFile(tmp);
        }
    }
}
