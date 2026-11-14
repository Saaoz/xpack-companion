package net.xpack.companion.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Commande {@code /xpack diagnostic} : copie, anonymisés, les fichiers que les lanceurs laissent
 * dans l'instance et qui décrivent le modpack. Sert à préparer la détection des modpacks sur de
 * vraies installations (voir {@link ModpackDetector}) : le joueur voit ce qui a été trouvé et peut
 * envoyer le dossier produit.
 */
public final class Diagnostics {
    /** Fichiers regardés dans le dossier du jeu et dans son parent (Prism range le jeu dans un sous-dossier). */
    static final List<String> LAUNCHER_FILES = J8.listOf(
            "instance.cfg", // Prism / MultiMC
            "mmc-pack.json", // Prism / MultiMC : versions du jeu et du loader
            "minecraftinstance.json", // app CurseForge
            "manifest.json", // export CurseForge
            "modrinth.index.json", // .mrpack décompressé
            "profile.json", // Modrinth App (anciennes versions)
            "instance.json"); // ATLauncher, FTB App
    static final long MAX_BYTES = 2_000_000;
    /**
     * Champs supplémentaires utiles au diagnostic, en plus de {@link ModpackDetector#WHITELIST} : versions
     * du jeu et du loader, pour comprendre pourquoi un pack n'est pas reconnu. {@code a[].b} parcourt un
     * tableau. Tout ce qui n'est pas listé (chemins, comptes, jetons, liste des fichiers) est écarté.
     */
    static final Map<String, List<String>> EXTRA_KEYS;
    /** Longueur maximale d'une valeur recopiée. */
    private static final int MAX_VALUE = 200;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("mmc-pack.json", J8.listOf("formatVersion", "components[].uid", "components[].version"));
        m.put("minecraftinstance.json", J8.listOf("gameVersion", "baseModLoader.name", "baseModLoader.minecraftVersion"));
        m.put("manifest.json", J8.listOf("manifestVersion", "minecraft.version", "minecraft.modLoaders[].id", "minecraft.modLoaders[].primary"));
        m.put("modrinth.index.json", J8.listOf("formatVersion", "game", "dependencies.minecraft", "dependencies.fabric-loader",
                "dependencies.quilt-loader", "dependencies.forge", "dependencies.neoforge"));
        m.put("profile.json", J8.listOf("metadata.game_version", "metadata.loader", "metadata.loader_version"));
        m.put("instance.json", J8.listOf("launcher.loaderVersion.type", "launcher.loaderVersion.version"));
        EXTRA_KEYS = Collections.unmodifiableMap(m);
    }

    /** instance.cfg contient des chemins (JavaPath…) : on ne garde que ces clés. */
    private static final Pattern INSTANCE_CFG_KEYS = Pattern.compile("^(name|InstanceType|ManagedPack\\w*)=");

    public static final class Result {
        private final Path folder;
        private final List<String> found;

        public Result(Path folder, List<String> found) {
            this.folder = folder;
            this.found = found;
        }

        public Path folder() {
            return folder;
        }

        public List<String> found() {
            return found;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Result)) return false;
            Result other = (Result) o;
            return java.util.Objects.equals(folder, other.folder) && java.util.Objects.equals(found, other.found);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(folder, found);
        }

        @Override
        public String toString() {
            return "Result[" + "folder=" + folder + ", found=" + found + "]";
        }
    }

    private Diagnostics() {}

    public static Result write(Path gameDir, Path outRoot, String summary, List<String> modIds, Clock clock) throws IOException {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(clock.instant());
        Path out = outRoot.resolve("diagnostic-" + stamp);
        Files.createDirectories(out);
        List<String> found = new ArrayList<>();
        Path[] dirs = {gameDir, gameDir.getParent()};
        String[] labels = {"jeu", "parent"};
        for (int i = 0; i < dirs.length; i++) {
            if (dirs[i] == null) continue;
            for (String name : LAUNCHER_FILES) {
                Path f = dirs[i].resolve(name);
                if (!Files.isRegularFile(f) || Files.size(f) > MAX_BYTES) continue;
                String text;
                try {
                    text = J8.readString(f, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    continue; // encodage inattendu : ignoré
                }
                J8.writeString(out.resolve(labels[i] + "-" + name), sanitize(name, text), StandardCharsets.UTF_8);
                found.add(labels[i] + "/" + name);
            }
        }
        J8.writeString(out.resolve("mods.txt"), modIds.stream().sorted().collect(Collectors.joining("\n")) + "\n", StandardCharsets.UTF_8);
        String readme = summary + "\n\nFichiers trouvés :\n" + (found.isEmpty() ? "  (aucun)" : found.stream().map(s -> "  - " + s).collect(Collectors.joining("\n")))
                + "\n\nChemins et nom d'utilisateur remplacés par <HOME> / <USER>. instance.cfg réduit aux clés name, InstanceType et ManagedPack*, fichiers JSON réduits à une liste de champs (identité du pack, versions du jeu et du loader).\n";
        J8.writeString(out.resolve("LISEZMOI.txt"), readme, StandardCharsets.UTF_8);
        return new Result(out, found);
    }

    static String sanitize(String fileName, String text) {
        if (fileName.equals("instance.cfg")) {
            text = J8.lines(text).stream().filter(l -> INSTANCE_CFG_KEYS.matcher(l).find()).collect(Collectors.joining("\n")) + "\n";
        } else if (fileName.endsWith(".json")) {
            text = filterJson(fileName, text);
        }
        return anonymize(text);
    }

    /**
     * Ne garde d'un fichier JSON du lanceur que les champs de la liste blanche (détection) et de
     * {@link #EXTRA_KEYS} (versions). Un fichier inconnu ou illisible n'est jamais recopié tel quel.
     */
    static String filterJson(String fileName, String text) {
        List<String> paths = new ArrayList<>();
        List<String> white = ModpackDetector.WHITELIST.get(fileName);
        if (white != null) paths.addAll(white);
        List<String> extra = EXTRA_KEYS.get(fileName);
        if (extra != null) paths.addAll(extra);
        JsonElement root;
        try {
            root = J8.parseJson(text);
        } catch (RuntimeException e) {
            return "{\"_erreur\":\"JSON illisible, contenu non copié\"}\n";
        }
        JsonObject out = new JsonObject();
        if (root.isJsonObject() && !paths.isEmpty()) {
            for (String p : paths) copyPath(root.getAsJsonObject(), out, p.split("\\."), 0);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(out) + "\n";
    }

    /** Recopie dans {@code dst} la valeur simple désignée par {@code path} (objets recréés, tableaux parcourus). */
    private static void copyPath(JsonObject src, JsonObject dst, String[] path, int i) {
        String seg = path[i];
        boolean array = seg.endsWith("[]");
        String key = array ? seg.substring(0, seg.length() - 2) : seg;
        JsonElement v = src.get(key);
        if (v == null) return;
        boolean last = i == path.length - 1;
        if (array) {
            if (!v.isJsonArray()) return;
            JsonArray in = v.getAsJsonArray();
            JsonArray outArr = dst.has(key) && dst.get(key).isJsonArray() ? dst.getAsJsonArray(key) : new JsonArray();
            for (int n = 0; n < in.size() && n < 200; n++) {
                JsonElement item = in.get(n);
                if (last) {
                    if (item.isJsonPrimitive()) outArr.add(limit(item.getAsJsonPrimitive()));
                } else if (item.isJsonObject()) {
                    JsonObject target;
                    if (outArr.size() > n && outArr.get(n).isJsonObject()) target = outArr.get(n).getAsJsonObject();
                    else {
                        while (outArr.size() < n) outArr.add(new JsonObject());
                        target = new JsonObject();
                        outArr.add(target);
                    }
                    copyPath(item.getAsJsonObject(), target, path, i + 1);
                }
            }
            if (outArr.size() > 0) dst.add(key, outArr);
        } else if (last) {
            if (v.isJsonPrimitive()) dst.add(key, limit(v.getAsJsonPrimitive()));
        } else if (v.isJsonObject()) {
            JsonObject target = dst.has(key) && dst.get(key).isJsonObject() ? dst.getAsJsonObject(key) : new JsonObject();
            copyPath(v.getAsJsonObject(), target, path, i + 1);
            if (!target.entrySet().isEmpty()) dst.add(key, target);
        }
    }

    private static JsonPrimitive limit(JsonPrimitive p) {
        if (p.isString() && p.getAsString().length() > MAX_VALUE) return new JsonPrimitive(p.getAsString().substring(0, MAX_VALUE) + "…");
        return p;
    }

    /** Remplace le dossier personnel (sous toutes ses écritures, JSON compris) et le nom d'utilisateur. */
    static String anonymize(String text) {
        String home = System.getProperty("user.home", "");
        if (home.length() > 3) {
            for (String variant : J8.listOf(home.replace("\\", "\\\\"), home, home.replace("\\", "/"))) {
                text = Pattern.compile(Pattern.quote(variant), Pattern.CASE_INSENSITIVE).matcher(text).replaceAll("<HOME>");
            }
        }
        String user = System.getProperty("user.name", "");
        if (user.length() >= 3) {
            text = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(user) + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE).matcher(text).replaceAll("<USER>");
        }
        return text;
    }

    /** Résumé lisible du modpack détecté, pour le chat et le LISEZMOI. */
    public static String describe(ModpackDetector.ModpackInfo m) {
        if (m == null) return "?";
        return String.format(Locale.ROOT, "source=%s, projet=%s, nom=%s, version=%s", m.source(), m.projectId(), m.name(), m.version());
    }
}
