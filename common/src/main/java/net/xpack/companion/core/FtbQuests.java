package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Quêtes FTB Quests (ATM10…, 04/11/2026) : chaque quête du livre compte dans la jauge
 * « Avancement » du site, comme les quêtes BetterQuesting. Solo seulement : on lit
 * <ul>
 *   <li>le livre de l'instance ({@code config/ftbquests/quests/} : chapitres, textes dans
 *       {@code lang/<langue>/}) — le site lit le même livre à l'import, il fait foi pour les
 *       modpacks des plateformes ;</li>
 *   <li>la progression de l'équipe du joueur ({@code <monde>/ftbquests/<équipe>.snbt}, clé
 *       {@code completed} : identifiant hexadécimal → date de fin en ms), réécrite par FTB Quests à
 *       chaque sauvegarde du monde.</li>
 * </ul>
 * Même lecture que {@code server/src/ftbQuests.ts}.
 */
public final class FtbQuests {
    /** Préfixe des identifiants XPack : « ftbquests:quest/27F62C4B42B16015 ». */
    public static final String PREFIX = "ftbquests:quest/";
    static final int MAX_DESCRIPTION = 600;

    private FtbQuests() {}

    /** Quête telle que le livre la décrit. */
    public static final class Quest {
        public final String id;
        public final String parent;
        /** Titre déjà traduit (fichier de langue du livre), ou null : le nom de l'objet de la quête. */
        public final String title;
        public final String description;
        public final String icon;
        /** Objet qui nomme une quête sans titre : celui de sa première tâche, sinon l'icône. */
        public final String named;
        /** Type de la première tâche (« checkmark »…), ou null. */
        public final String taskType;
        public final boolean hidden;
        public final String chapter;
        /** Position dans le livre, en demi-cases ; null si absente. */
        public final Integer column;
        public final Integer row;

        Quest(String id, String parent, String title, String description, String icon, String named, String taskType, boolean hidden, String chapter, Integer column, Integer row) {
            this.taskType = taskType;
            this.id = id;
            this.parent = parent;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.named = named;
            this.hidden = hidden;
            this.chapter = chapter;
            this.column = column;
            this.row = row;
        }
    }

    public static Path booksDir(Path gameDir) {
        return gameDir.resolve("config").resolve("ftbquests").resolve("quests");
    }

    // ------------------------------------------------------------------ livre

    /** Le livre de l'instance dans la langue {@code lang} (repli : anglais), ou une liste vide. */
    public static List<Quest> readBook(Path gameDir, String lang) {
        Path root = booksDir(gameDir);
        Path chaptersDir = root.resolve("chapters");
        if (!Files.isDirectory(chaptersDir)) return Collections.emptyList();
        List<String> groups = new ArrayList<>();
        JsonObject groupFile = readObject(root.resolve("chapter_groups.snbt"));
        for (JsonObject g : objects(groupFile == null ? null : groupFile.get("chapter_groups"))) groups.add(str(g, "id"));
        String code = lang == null ? "en_us" : lang.toLowerCase(Locale.ROOT);
        // Textes : un dossier par langue, ou un seul fichier fusionné par FTB Quests (lang/<langue>.snbt,
        // ATM10 8.4), qui l'emporte : c'est le plus récent. Anglais d'abord, puis la langue du joueur.
        JsonObject singleEn = readObject(root.resolve("lang").resolve("en_us.snbt"));
        JsonObject singleCode = readObject(root.resolve("lang").resolve(code + ".snbt"));
        JsonObject chapterLang = merged(merged(merged(readObject(root.resolve("lang").resolve("en_us").resolve("chapter.snbt")), singleEn),
                readObject(root.resolve("lang").resolve(code).resolve("chapter.snbt"))), singleCode);

        List<JsonObject> chapters = new ArrayList<>();
        Map<JsonObject, String> files = new LinkedHashMap<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(chaptersDir, "*.snbt")) {
            for (Path p : dir) {
                JsonObject c = readObject(p);
                if (c == null || str(c, "id") == null) continue;
                String file = p.getFileName().toString();
                files.put(c, file.substring(0, file.length() - ".snbt".length()));
                chapters.add(c);
            }
        } catch (IOException | RuntimeException e) {
            return Collections.emptyList();
        }
        // Ordre du livre : sans groupe, puis groupe par groupe, puis order_index, puis le nom du fichier.
        chapters.sort((a, b) -> {
            int ga = groupRank(str(a, "group"), groups);
            int gb = groupRank(str(b, "group"), groups);
            if (ga != gb) return Integer.compare(ga, gb);
            int oa = (int) num(a, "order_index", 0);
            int ob = (int) num(b, "order_index", 0);
            if (oa != ob) return Integer.compare(oa, ob);
            return files.get(a).compareTo(files.get(b));
        });

        List<Quest> out = new ArrayList<>();
        for (JsonObject ch : chapters) {
            String chapterId = str(ch, "id");
            String file = files.get(ch);
            JsonObject questLang = merged(merged(merged(readObject(root.resolve("lang").resolve("en_us").resolve("chapters").resolve(file + ".snbt")), singleEn),
                    readObject(root.resolve("lang").resolve(code).resolve("chapters").resolve(file + ".snbt"))), singleCode);
            String chapterTitle = clean(firstNonNull(text(chapterLang.get("chapter." + chapterId + ".title")), text(ch.get("title"))));
            if (chapterTitle.isEmpty()) chapterTitle = pretty(file);
            List<JsonObject> quests = objects(ch.get("quests"));
            Set<String> inChapter = new HashSet<>();
            for (JsonObject q : quests) if (str(q, "id") != null) inChapter.add(str(q, "id"));
            for (JsonObject q : quests) {
                String id = str(q, "id");
                if (id == null) continue;
                String title = clean(firstNonNull(text(questLang.get("quest." + id + ".title")), text(q.get("title"))));
                String subtitle = clean(firstNonNull(text(questLang.get("quest." + id + ".quest_subtitle")), text(q.get("subtitle"))));
                String desc = clean(firstNonNull(text(questLang.get("quest." + id + ".quest_desc")), text(q.get("description"))));
                String d = subtitle.isEmpty() ? desc : subtitle;
                if (d.length() > MAX_DESCRIPTION) d = d.substring(0, MAX_DESCRIPTION - 1).trim() + "…";
                String parent = null;
                for (JsonElement dep : array(q.get("dependencies"))) {
                    String depId = dep.isJsonPrimitive() ? dep.getAsString() : null;
                    if (depId != null && inChapter.contains(depId) && !depId.equals(id)) {
                        parent = PREFIX + depId;
                        break;
                    }
                }
                JsonElement x = q.get("x");
                JsonElement y = q.get("y");
                boolean placed = x != null && y != null && x.isJsonPrimitive() && y.isJsonPrimitive();
                String icon = iconOf(q);
                String task = taskItem(q);
                out.add(new Quest(PREFIX + id, parent, title.isEmpty() ? null : title, d, icon, task != null ? task : icon, firstTaskType(q), bool(q, "invisible"), chapterTitle,
                        placed ? (int) Math.round(x.getAsDouble() * 2) : null, placed ? (int) Math.round(y.getAsDouble() * 2) : null));
            }
        }
        return out;
    }

    /**
     * Le livre sous la forme des progrès du jeu. Une quête sans titre prend le nom de l'objet de sa
     * première tâche, comme dans FTB Quests ({@code translate} : texte du jeu pour une clé, la clé
     * telle quelle s'il ne la connaît pas).
     */
    public static List<XPackCore.AdvancementInfo> catalog(List<Quest> quests, Function<String, String> translate) {
        List<XPackCore.AdvancementInfo> out = new ArrayList<>();
        for (Quest q : quests) {
            // Ni titre ni objet (tâche « checkmark »…) : le début de la description, plus parlant que le type de tâche.
            String title = q.title != null ? q.title : q.named != null ? itemName(q.named, translate) : !q.description.isEmpty() ? shortLead(q.description) : q.taskType != null ? pretty(q.taskType) : q.id;
            out.add(new XPackCore.AdvancementInfo(q.id, q.parent, title, q.description, "task", q.icon == null ? "minecraft:air" : q.icon, q.hidden, q.chapter,
                    q.column, q.row));
        }
        return out;
    }

    static String itemName(String item, Function<String, String> translate) {
        int colon = item.indexOf(':');
        String key = (colon < 0 ? "minecraft." + item : item.substring(0, colon) + "." + item.substring(colon + 1)).replace('/', '.');
        for (String prefix : new String[] {"item.", "block."}) {
            String t = translate.apply(prefix + key);
            if (t != null && !t.equals(prefix + key)) return t;
        }
        return pretty(item);
    }

    // ------------------------------------------------------------------ progression

    /**
     * Fichier de progression de l'équipe du joueur : {@code <monde>/ftbquests/<uuid>.snbt} (équipe
     * personnelle FTB Teams = UUID du joueur), sinon le fichier de l'équipe (partie FTB Teams) qui
     * cite le joueur, sinon le seul fichier du dossier (monde solo).
     */
    public static Path progressFile(Path worldDir, String playerUuid) {
        Path dir = worldDir.resolve("ftbquests");
        Path own = dir.resolve(playerUuid + ".snbt");
        if (Files.isRegularFile(own)) return own;
        Path parties = worldDir.resolve("ftbteams").resolve("party");
        if (Files.isDirectory(parties)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(parties, "*.snbt")) {
                for (Path p : files) {
                    String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    if (content.toLowerCase(Locale.ROOT).contains(playerUuid.toLowerCase(Locale.ROOT))) {
                        Path team = dir.resolve(p.getFileName().toString());
                        if (Files.isRegularFile(team)) return team;
                    }
                }
            } catch (IOException | RuntimeException e) {
                // dossier illisible : on essaie le repli suivant
            }
        }
        List<Path> all = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.snbt")) {
                for (Path p : files) all.add(p);
            } catch (IOException | RuntimeException e) {
                return own;
            }
        }
        return all.size() == 1 ? all.get(0) : own;
    }

    /**
     * Quêtes terminées (identifiant XPack → date de fin en ms) parmi {@code known} (null : toutes
     * les entrées, chapitres compris) ; {@code null} si le fichier est illisible (en cours
     * d'écriture), une table vide s'il n'existe pas encore.
     */
    public static Map<String, Long> readCompleted(Path file, Set<String> known) {
        if (!Files.isRegularFile(file)) return Collections.emptyMap();
        JsonObject data = readObject(file);
        if (data == null) return null;
        Map<String, Long> out = new LinkedHashMap<>();
        JsonElement completed = data.get("completed");
        if (completed == null || !completed.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : completed.getAsJsonObject().entrySet()) {
            String id = PREFIX + e.getKey().toUpperCase(Locale.ROOT);
            if (known != null && !known.contains(id)) continue;
            long at = 0;
            try {
                at = e.getValue().isJsonPrimitive() ? e.getValue().getAsLong() : 0;
            } catch (RuntimeException ex) {
                at = 0;
            }
            out.put(id, at);
        }
        return out;
    }

    // ------------------------------------------------------------------ source pour QuestWatcher

    /** Suivi FTB Quests d'un monde solo, pour {@link QuestWatcher}. */
    public static QuestWatcher.Source source(Path gameDir, Path worldDir, String playerUuid, java.util.function.Supplier<String> lang) {
        return new QuestWatcher.Source() {
            private volatile Set<String> known;

            @Override
            public Path progressFile() {
                return FtbQuests.progressFile(worldDir, playerUuid);
            }

            @Override
            public List<XPackCore.AdvancementInfo> readCatalog(Function<String, String> translate) {
                List<Quest> book = readBook(gameDir, lang.get());
                Set<String> ids = new HashSet<>();
                for (Quest q : book) ids.add(q.id);
                known = ids;
                return catalog(book, translate);
            }

            @Override
            public Map<String, Long> readCompleted() {
                return FtbQuests.readCompleted(progressFile(), known);
            }
        };
    }

    // ------------------------------------------------------------------ outils

    /** Texte d'affichage : sans codes de couleur (« &7 », « §a »), sur une ligne. */
    static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[&§][0-9a-fk-or]", "").replace("\\n", " ").replace("{@pagebreak}", " ").replaceAll("\\s+", " ").trim();
    }

    /** Début d'une description, pour titre : la première phrase, 60 caractères au plus. */
    static String shortLead(String desc) {
        String first = desc.split("(?<=[.!?])\\s", 2)[0].trim();
        return first.length() > 60 ? first.substring(0, 59).trim() + "…" : first;
    }

    private static String firstTaskType(JsonObject q) {
        List<JsonObject> tasks = objects(q.get("tasks"));
        return tasks.isEmpty() ? null : str(tasks.get(0), "type");
    }

    static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("[_/]")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Image personnalisée de FTB Quests (texture dans ses données) : pas un objet à afficher ni à nommer. */
    static final String CUSTOM_ICON = "ftbquests:custom_icon";

    /** Icône d'une quête : la sienne (sauf image personnalisée), sinon l'objet de sa première tâche. */
    private static String iconOf(JsonObject q) {
        JsonElement icon = q.get("icon");
        String id = icon != null && icon.isJsonPrimitive() ? icon.getAsString() : icon != null && icon.isJsonObject() ? str(icon.getAsJsonObject(), "id") : null;
        if (id != null && !id.equals(CUSTOM_ICON)) return id;
        return taskItem(q);
    }

    /** Objet de la première tâche (FTB Quests nomme ainsi une quête sans titre). */
    private static String taskItem(JsonObject q) {
        for (JsonObject t : objects(q.get("tasks"))) {
            JsonElement item = t.get("item");
            String id = item != null && item.isJsonPrimitive() ? item.getAsString() : item != null && item.isJsonObject() ? str(item.getAsJsonObject(), "id") : null;
            if (id != null && !id.equals(CUSTOM_ICON)) return id;
        }
        return null;
    }

    private static int groupRank(String group, List<String> groups) {
        if (group == null || group.isEmpty()) return 0;
        int i = groups.indexOf(group);
        return i < 0 ? groups.size() + 1 : i + 1;
    }

    private static JsonObject readObject(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            JsonElement e = Snbt.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null; // fichier en cours d'écriture ou abîmé : ignoré
        }
    }

    /** `b` par-dessus `a` (langue du joueur par-dessus l'anglais). */
    private static JsonObject merged(JsonObject a, JsonObject b) {
        JsonObject out = new JsonObject();
        if (a != null) for (Map.Entry<String, JsonElement> e : a.entrySet()) out.add(e.getKey(), e.getValue());
        if (b != null) for (Map.Entry<String, JsonElement> e : b.entrySet()) out.add(e.getKey(), e.getValue());
        return out;
    }

    private static List<JsonObject> objects(JsonElement e) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement item : array(e)) if (item.isJsonObject()) out.add(item.getAsJsonObject());
        return out;
    }

    private static JsonArray array(JsonElement e) {
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    /** Texte d'une valeur : une chaîne, ou une liste de lignes (descriptions). */
    private static String text(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonPrimitive()) return e.getAsString();
        if (e.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement l : e.getAsJsonArray()) if (l.isJsonPrimitive()) sb.append(sb.length() > 0 ? " " : "").append(l.getAsString());
            return sb.toString();
        }
        return null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null && !clean(a).isEmpty() ? a : b;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static long num(JsonObject o, String key, long fallback) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return false;
        return e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : "1".equals(e.getAsString());
    }
}
