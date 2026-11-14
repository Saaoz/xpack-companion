package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Quêtes BetterQuesting (RLCraft, GT: New Horizons…) : chaque quête du livre devient un succès
 * XPack, comme un progrès du jeu. On lit les fichiers du monde solo ({@code betterquesting/}) :
 * {@code QuestDatabase.json} pour le livre, {@code QuestProgress.json} (1.12.2) ou
 * {@code QuestProgress/<joueur>.json} (GT: New Horizons) pour les quêtes terminées. Le mod
 * BetterQuesting enregistre ces fichiers à chaque sauvegarde du monde.
 *
 * <p>Les fichiers sont du NBT écrit en JSON : chaque clé porte son type (« questID:3 »). Deux
 * formats d'identifiant : un entier (1.12.2) ou un UUID en deux nombres (GT: New Horizons).
 */
public final class BetterQuesting {
    /** Préfixe des identifiants XPack : « betterquesting:quest/12 », « betterquesting:quest/<uuid> ». */
    public static final String PREFIX = "betterquesting:quest/";
    static final int MAX_DESCRIPTION = 600;

    private BetterQuesting() {}

    /** Quête telle que le livre la montre (textes encore à traduire par le jeu). */
    public static final class Quest {
        public final String id;
        public final String parent;
        public final String name;
        public final String description;
        public final String icon;
        public final boolean main;
        public final boolean hidden;
        /** Chapitre (ligne de quêtes) où le livre la range. */
        public final String line;

        Quest(String id, String parent, String name, String description, String icon, boolean main, boolean hidden, String line) {
            this.id = id;
            this.parent = parent;
            this.name = name;
            this.description = description;
            this.icon = icon;
            this.main = main;
            this.hidden = hidden;
            this.line = line;
        }
    }

    // ------------------------------------------------------------------ fichiers

    public static Path folder(Path worldDir) {
        return worldDir.resolve("betterquesting");
    }

    /** Le livre du monde, ou une liste vide (pas de BetterQuesting, fichier illisible). */
    public static List<Quest> readQuests(Path worldDir) {
        JsonObject db = readJson(folder(worldDir).resolve("QuestDatabase.json"));
        return db == null ? Collections.<Quest>emptyList() : parseQuests(db);
    }

    /** Fichier de progression du joueur : par joueur (GT: New Horizons), sinon commun à tous. */
    public static Path progressFile(Path worldDir, String playerUuid) {
        Path own = folder(worldDir).resolve("QuestProgress").resolve(playerUuid + ".json");
        return Files.isRegularFile(own) ? own : folder(worldDir).resolve("QuestProgress.json");
    }

    /** Quêtes terminées par le joueur, avec leur date (ms, 0 si inconnue) ; {@code null} si illisible. */
    public static Map<String, Long> readCompleted(Path worldDir, String playerUuid) {
        JsonObject progress = readJson(progressFile(worldDir, playerUuid));
        return progress == null ? null : parseCompleted(progress, playerUuid);
    }

    private static JsonObject readJson(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement e = J8.parseJson(r);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            XLog.warn("fichier BetterQuesting illisible", e);
            return null; // fichier en cours d'écriture ou abîmé : on réessaiera
        }
    }

    // ------------------------------------------------------------------ lecture

    /** Les quêtes rangées dans un chapitre, dans l'ordre du livre (les autres n'y apparaissent pas). */
    static List<Quest> parseQuests(JsonObject db) {
        Map<String, String> lineOf = new LinkedHashMap<>();
        List<JsonObject> lines = entries(field(db, "questLines"));
        // GT: New Horizons range ses chapitres par « order » ; 1.12.2 par leur place dans la liste.
        List<JsonObject> sorted = new ArrayList<>(lines);
        sorted.sort((a, b) -> Long.compare(num(a, "order", lines.indexOf(a)), num(b, "order", lines.indexOf(b))));
        for (JsonObject line : sorted) {
            String name = str(props(line), "name");
            for (JsonObject entry : entries(field(line, "quests"))) {
                String id = questId(entry, "id");
                if (id != null && !lineOf.containsKey(id)) lineOf.put(id, name);
            }
        }
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        for (JsonObject q : entries(field(db, "questDatabase"))) {
            String id = questId(q, "questID");
            if (id != null) byId.put(id, q);
        }
        List<Quest> out = new ArrayList<>();
        for (Map.Entry<String, String> e : lineOf.entrySet()) {
            JsonObject q = byId.get(e.getKey());
            if (q == null) continue;
            JsonObject p = props(q);
            String parent = null;
            for (String pre : prerequisites(q)) {
                if (lineOf.containsKey(pre) && byId.containsKey(pre) && !pre.equals(e.getKey())) {
                    parent = PREFIX + pre;
                    break;
                }
            }
            JsonElement icon = field(p, "icon");
            String visibility = str(p, "visibility");
            out.add(new Quest(PREFIX + e.getKey(), parent, str(p, "name"), str(p, "desc"), icon != null && icon.isJsonObject() ? str(icon.getAsJsonObject(), "id") : null,
                    num(p, "ismain", 0) != 0, "HIDDEN".equals(visibility) || "SECRET".equals(visibility), e.getValue()));
        }
        return out;
    }

    /** Quêtes terminées par {@code playerUuid} : identifiant XPack → date de fin (ms, 0 si absente). */
    static Map<String, Long> parseCompleted(JsonObject progress, String playerUuid) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (JsonObject q : entries(field(progress, "questProgress"))) {
            String id = questId(q, "questID");
            if (id == null) continue;
            for (JsonObject done : entries(field(q, "completed"))) {
                if (playerUuid.equalsIgnoreCase(str(done, "uuid"))) out.put(PREFIX + id, num(done, "timestamp", 0));
            }
        }
        return out;
    }

    /**
     * Le livre sous la forme des progrès du jeu : titre et chapitre traduits par le jeu ({@code
     * translate} rend la clé telle quelle s'il ne la connaît pas). Quête principale : objectif.
     */
    public static List<XPackCore.AdvancementInfo> catalog(List<Quest> quests, Function<String, String> translate) {
        List<XPackCore.AdvancementInfo> out = new ArrayList<>();
        for (Quest q : quests) {
            String title = clean(translate.apply(q.name));
            if (title.isEmpty()) title = q.id;
            String description = clean(translate.apply(q.description));
            if (description.length() > MAX_DESCRIPTION) description = description.substring(0, MAX_DESCRIPTION - 1).trim() + "…";
            String line = clean(translate.apply(q.line));
            out.add(new XPackCore.AdvancementInfo(q.id, q.parent, title, description, q.main ? "goal" : "task", q.icon == null ? "minecraft:air" : q.icon, q.hidden,
                    line.isEmpty() ? "Quêtes" : line));
        }
        return out;
    }

    /** Texte d'affichage : sans codes de couleur (« §a »), sur une ligne. */
    static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ NBT en JSON

    /** Valeur d'une clé NBT, avec ou sans son suffixe de type (« name:8 » ou « name »). */
    static JsonElement field(JsonObject o, String name) {
        if (o == null) return null;
        if (o.has(name)) return o.get(name);
        String prefix = name + ":";
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey().startsWith(prefix)) return e.getValue();
        }
        return null;
    }

    private static String str(JsonObject o, String name) {
        JsonElement e = field(o, name);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static long num(JsonObject o, String name, long fallback) {
        JsonElement e = field(o, name);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static JsonObject props(JsonObject o) {
        JsonElement p = field(o, "properties");
        JsonElement bq = p != null && p.isJsonObject() ? field(p.getAsJsonObject(), "betterquesting") : null;
        return bq != null && bq.isJsonObject() ? bq.getAsJsonObject() : new JsonObject();
    }

    /** Liste NBT : objet indexé (« 0:10 », « 1:10 »…) ou tableau. */
    private static List<JsonObject> entries(JsonElement e) {
        List<JsonObject> out = new ArrayList<>();
        if (e == null) return out;
        Iterable<JsonElement> items = e.isJsonArray() ? e.getAsJsonArray() : e.isJsonObject() ? valuesOf(e.getAsJsonObject()) : Collections.<JsonElement>emptyList();
        for (JsonElement item : items) if (item.isJsonObject()) out.add(item.getAsJsonObject());
        return out;
    }

    private static List<JsonElement> valuesOf(JsonObject o) {
        List<JsonElement> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) out.add(e.getValue());
        return out;
    }

    /** Identifiant d'une quête : « {name}IDHigh/Low » (UUID, GT: New Horizons) ou « {name} » (entier). */
    private static String questId(JsonObject o, String name) {
        JsonElement high = field(o, name + "High");
        JsonElement low = field(o, name + "Low");
        // Dans les chapitres, GT: New Horizons écrit « questIDHigh » là où 1.12.2 écrit « id ».
        if (high == null && "id".equals(name)) {
            high = field(o, "questIDHigh");
            low = field(o, "questIDLow");
        }
        try {
            if (high != null && low != null) return new UUID(high.getAsLong(), low.getAsLong()).toString();
            JsonElement id = field(o, name);
            return id != null && id.isJsonPrimitive() ? String.valueOf(id.getAsLong()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Quêtes requises : tableau d'entiers (1.12.2) ou liste d'UUID en deux nombres (GT: New Horizons). */
    private static List<String> prerequisites(JsonObject q) {
        List<String> out = new ArrayList<>();
        JsonElement pre = field(q, "preRequisites");
        if (pre == null) return out;
        if (pre.isJsonArray()) {
            for (JsonElement e : (JsonArray) pre) {
                if (e.isJsonPrimitive()) out.add(String.valueOf(e.getAsLong()));
                else if (e.isJsonObject()) addId(out, e.getAsJsonObject());
            }
        } else if (pre.isJsonObject()) {
            for (JsonObject e : entries(pre)) addId(out, e);
        }
        return out;
    }

    private static void addId(List<String> out, JsonObject o) {
        String id = questId(o, "questID");
        if (id != null) out.add(id);
    }
}
