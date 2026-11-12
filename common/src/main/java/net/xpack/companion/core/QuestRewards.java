package net.xpack.companion.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Objets que les quêtes du modpack peuvent donner (#128) : un objet qui y figure est expliqué,
 * même s'il n'est pas encore fabricable (récompense aléatoire). On lit les fichiers de quêtes de
 * l'instance : FTB Quests ({@code config/ftbquests/quests/}, tables de récompenses comprises) et
 * Heracles ({@code config/heracles/}). Seuls les blocs {@code rewards} comptent : les objets à
 * fournir (tâches) ne sont pas des récompenses.
 */
public final class QuestRewards {
    static final List<String> FOLDERS = J8.listOf("config/ftbquests/quests", "config/heracles");
    static final int MAX_FILES = 3_000;
    static final long MAX_FILE_BYTES = 2_000_000;

    private static final Pattern REWARDS = Pattern.compile("\"?rewards\"?\\s*:\\s*([\\[{])");
    private static final Pattern ITEM_ID = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    private QuestRewards() {}

    public static Set<String> scan(Path gameDir) {
        Set<String> items = new HashSet<>();
        for (String folder : FOLDERS) {
            Path dir = gameDir.resolve(folder);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(p -> p.toString().endsWith(".snbt") || p.toString().endsWith(".json")).limit(MAX_FILES).forEach(p -> read(p, items));
            } catch (IOException | RuntimeException e) {
                // Dossier illisible : pas d'explication par les quêtes, rien de plus.
            }
        }
        return items;
    }

    private static void read(Path file, Set<String> items) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) return;
            items.addAll(rewardItems(J8.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            // Fichier illisible : ignoré.
        }
    }

    /** Identifiants d'objets cités dans les blocs {@code rewards} d'un fichier SNBT ou JSON. */
    static Set<String> rewardItems(String text) {
        Set<String> items = new HashSet<>();
        Matcher m = REWARDS.matcher(text);
        int from = 0;
        while (m.find(from)) {
            int end = closing(text, m.start(1));
            Matcher id = ITEM_ID.matcher(text.substring(m.start(1), end));
            while (id.find()) items.add(id.group(1));
            from = Math.max(end, m.end());
        }
        return items;
    }

    /** Fin du bloc ouvert à {@code start} ([ ou {), chaînes entre guillemets ignorées. */
    private static int closing(String text, int start) {
        int depth = 0;
        boolean quoted = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '\\') i++;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '[' || c == '{') depth++;
            else if ((c == ']' || c == '}') && --depth == 0) return i + 1;
        }
        return text.length();
    }
}
