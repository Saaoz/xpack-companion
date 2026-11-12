package net.xpack.companion.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Commande {@code /xpack diagnostic} : copie, anonymisés, les fichiers que les lanceurs laissent
 * dans l'instance et qui décrivent le modpack. Sert à préparer la détection des modpacks sur de
 * vraies installations (docs/DETECTION-MODPACK.md) : le joueur voit ce qui a été trouvé et peut
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
                + "\n\nChemins et nom d'utilisateur remplacés par <HOME> / <USER>. instance.cfg réduit aux clés name, InstanceType et ManagedPack*.\n";
        J8.writeString(out.resolve("LISEZMOI.txt"), readme, StandardCharsets.UTF_8);
        return new Result(out, found);
    }

    static String sanitize(String fileName, String text) {
        if (fileName.equals("instance.cfg")) {
            text = J8.lines(text).stream().filter(l -> INSTANCE_CFG_KEYS.matcher(l).find()).collect(Collectors.joining("\n")) + "\n";
        }
        return anonymize(text);
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
