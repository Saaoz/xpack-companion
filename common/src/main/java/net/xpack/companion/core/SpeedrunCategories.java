package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Préréglages propres à un modpack (#222) : les catégories de speedrun définies par ses créateurs, lues sur
 * {@code GET /v1/speedrun/categories} (lecture seule, rien n'est envoyé). Pur Java.
 *
 * <p>Une catégorie devient un préréglage dont l'unique étape porte son nom. Sa condition de fin n'est jamais
 * évaluée par le chrono libre : le mode libre reste non vérifié, seule la fin du joueur compte.
 */
public final class SpeedrunCategories {
    /** Préfixe de l'identifiant d'un préréglage de modpack dans la configuration : {@code pack:<catégorie>}. */
    public static final String PREFIX = "pack:";
    /** Clé sous laquelle {@link ApiClient} range un tableau JSON reçu à la racine de la réponse. */
    public static final String ITEMS = "items";
    static final int MAX_CATEGORIES = 12;
    static final int MAX_NAME = 32;

    public static final class Category {
        public final String id;
        public final String name;
        /** Progrès Minecraft qui termine la catégorie, ou null (autre condition, ou tous les succès). */
        public final String advancement;

        Category(String id, String name, String advancement) {
            this.id = id;
            this.name = name;
            this.advancement = advancement;
        }

        /** Identifiant enregistré dans la configuration. */
        public String presetId() {
            return PREFIX + id;
        }

        /** Étapes du préréglage : une seule, le nom de la catégorie. */
        public List<String> steps() {
            return Collections.singletonList(name);
        }
    }

    private SpeedrunCategories() {}

    /**
     * Identifiant de modpack côté serveur ({@code resolveModpackId}, non inclus dans ce dépôt) calculé depuis ce que le
     * mod a détecté : {@code mr-<projet>}, {@code cf-<projet>}, sinon {@code pack-<nom>}. Null si le pack n'est pas
     * identifiable (les packs {@code hash-…} n'ont pas de catégories).
     */
    public static String modpackId(ModpackDetector.ModpackInfo m) {
        if (m == null) return null;
        String project = m.projectId();
        if ("modrinth".equals(m.source()) && project != null && !project.isEmpty()) return "mr-" + project;
        if ("curseforge".equals(m.source()) && project != null && !project.isEmpty()) return "cf-" + project;
        String slug = slug(m.name());
        return slug.isEmpty() ? null : "pack-" + slug;
    }

    static String slug(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "").toLowerCase(Locale.ROOT);
        return s.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /**
     * Catégories du modpack {@code modpackId} dans la réponse de {@code /v1/speedrun/categories} (tableau rangé sous
     * {@link #ITEMS}). Ce qui est illisible est ignoré : identifiant invalide ou en double, nom vide.
     */
    public static List<Category> parse(JsonObject body, String modpackId) {
        List<Category> out = new ArrayList<>();
        if (body == null || modpackId == null || !body.has(ITEMS) || !body.get(ITEMS).isJsonArray()) return out;
        for (JsonElement el : body.getAsJsonArray(ITEMS)) {
            if (!el.isJsonObject()) continue;
            JsonObject pack = el.getAsJsonObject();
            if (!modpackId.equals(text(pack, "modpackId"))) continue;
            if (!pack.has("categories") || !pack.get("categories").isJsonArray()) return out;
            JsonArray cats = pack.getAsJsonArray("categories");
            Set<String> seen = new HashSet<>();
            for (JsonElement ce : cats) {
                if (out.size() >= MAX_CATEGORIES) break;
                if (!ce.isJsonObject()) continue;
                JsonObject c = ce.getAsJsonObject();
                String id = text(c, "id");
                String name = clean(text(c, "name"));
                if (id == null || !id.matches("[a-z0-9_%-]{1,40}") || name.isEmpty() || !seen.add(id)) continue;
                out.add(new Category(id, name, finishAdvancement(c.get("finish"))));
            }
            return out;
        }
        return out;
    }

    /** La catégorie dont {@code presetId} est l'identifiant de préréglage, ou null. */
    public static Category find(List<Category> categories, String presetId) {
        if (presetId == null) return null;
        for (Category c : categories) if (c.presetId().equals(presetId)) return c;
        return null;
    }

    /** Vrai pour l'identifiant d'un préréglage de modpack, qu'il soit encore chargé ou non. */
    public static boolean isPackPreset(String presetId) {
        return presetId != null && presetId.startsWith(PREFIX) && presetId.length() > PREFIX.length();
    }

    /** Identifiants proposés dans l'écran : ceux du mod ({@link SpeedrunPresets#ids()}), puis les catégories du modpack. */
    public static List<String> presetIds(List<Category> categories) {
        List<String> ids = SpeedrunPresets.ids();
        for (Category c : categories) ids.add(c.presetId());
        return ids;
    }

    private static String finishAdvancement(JsonElement finish) {
        if (finish == null || !finish.isJsonObject()) return null;
        JsonObject f = finish.getAsJsonObject();
        return "advancement".equals(text(f, "type")) ? text(f, "id") : null;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    /** Nom affichable : sans caractères de contrôle, espaces réduits, borné à la taille d'une case d'étape. */
    static String clean(String name) {
        if (name == null) return "";
        String s = name.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }
}
