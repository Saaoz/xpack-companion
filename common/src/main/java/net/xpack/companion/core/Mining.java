package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * X-ray (#129) : de quoi comparer après coup ce qu'un joueur mine, jamais d'alerte en direct. Les
 * statistiques donnent les minerais et la roche minés ; le mod compte en plus les blocs que le
 * joueur casse lui-même, avec l'outil tenu. L'écart entre les deux trahit le minage par veine (FTB
 * Ultimine…), les marteaux 3×3 et les enchantements de zone : autant de faux positifs à écarter.
 * Les machines (quarry, mineurs Mekanism) ne comptent pas dans les statistiques du joueur.
 */
public final class Mining {
    /** Minerais par bloc et roche minée, d'après les statistiques {@code minecraft:mined/…}. */
    public static final class Summary {
        private final Map<String, Integer> ores;
        private final int stone;

        public Summary(Map<String, Integer> ores, int stone) {
            this.ores = ores;
            this.stone = stone;
        }

        public Map<String, Integer> ores() {
            return ores;
        }

        public int stone() {
            return stone;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Summary)) return false;
            Summary other = (Summary) o;
            return java.util.Objects.equals(ores, other.ores) && java.util.Objects.equals(stone, other.stone);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(ores, stone);
        }

        @Override
        public String toString() {
            return "Summary[" + "ores=" + ores + ", stone=" + stone + "]";
        }
    }

    static final String MINED = "minecraft:mined/";
    /** Outils retenus par session : au-delà, le joueur change d'outil sans arrêt (rien de plus à apprendre). */
    static final int MAX_TOOLS = 8;

    private final List<String> oreSuffixes;
    private final Set<String> oreIds;
    private final Set<String> stone;
    private final List<String> helperMods;
    private final Map<String, Integer> hand = new TreeMap<>();
    private final Set<String> tools = new TreeSet<>();

    public Mining(Collection<String> oreSuffixes, Collection<String> oreIds, Collection<String> stone, Collection<String> helperMods) {
        this.oreSuffixes = J8.copyOf(oreSuffixes);
        this.oreIds = J8.copyOfSet(oreIds);
        this.stone = J8.copyOfSet(stone);
        this.helperMods = J8.copyOf(helperMods);
    }

    /** Listes embarquées dans le jar (copie de spec/integrity-rules.json). */
    public static Mining bundled() {
        try (InputStream in = Mining.class.getResourceAsStream("/xpack/integrity-rules.json")) {
            if (in == null) throw new IllegalStateException("integrity-rules.json introuvable dans le jar");
            JsonObject json = J8.parseJson(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Mining(strings(json, "miningOreSuffixes"), strings(json, "miningOreIds"), strings(json, "miningStone"), strings(json, "miningHelperMods"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        JsonArray array = json.getAsJsonArray(key);
        if (array != null) array.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    public boolean isOre(String blockId) {
        return oreIds.contains(blockId) || oreSuffixes.stream().anyMatch(blockId::endsWith);
    }

    /** Mods chargés qui minent plusieurs blocs d'un coup ou à la place du joueur. */
    public List<String> helpers(Collection<String> loadedModIds) {
        return helperMods.stream().filter(loadedModIds::contains).sorted().collect(java.util.stream.Collectors.toList());
    }

    /** Toutes les statistiques reçues (le serveur les envoie toutes à la demande) → minerais et roche. */
    public Summary summarize(Map<String, Integer> stats) {
        Map<String, Integer> ores = new TreeMap<>();
        int rock = 0;
        for (Map.Entry<String, Integer> s : stats.entrySet()) {
            if (!s.getKey().startsWith(MINED) || s.getValue() <= 0) continue;
            String block = s.getKey().substring(MINED.length());
            if (isOre(block)) ores.put(block, s.getValue());
            else if (stone.contains(block)) rock += s.getValue();
        }
        return new Summary(ores, rock);
    }

    /** Bloc cassé par le joueur lui-même (clic) ; seuls les minerais sont gardés, avec l'outil. */
    public void onBlockBroken(String blockId, String toolId) {
        if (!isOre(blockId)) return;
        hand.merge(blockId, 1, Integer::sum);
        if (toolId != null && (tools.size() < MAX_TOOLS || tools.contains(toolId))) tools.add(toolId);
    }

    /** Minerais cassés à la main pendant cette session, par bloc. */
    public Map<String, Integer> hand() {
        return J8.copyOf(hand);
    }

    public Set<String> tools() {
        return J8.copyOfSet(tools);
    }

    public void reset() {
        hand.clear();
        tools.clear();
    }
}
