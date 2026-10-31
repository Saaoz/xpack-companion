package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Journal des objets reçus (#128) : savoir d'où vient un objet de valeur au lieu de supposer la
 * triche. Un œuf d'apparition peut venir d'une récompense de quête, des blocs de diamant d'un
 * coffre cassé. L'inventaire est comparé d'un relevé au suivant ; chaque arrivée part avec son
 * contexte (ramassée par terre, écran ouvert, ou arrivée sans explication) et le serveur l'analyse
 * après coup. Jamais de signalement ni de sanction côté mod.
 *
 * <p>Limites connues : fermes automatiques et stockage moddé (AE2, Refined Storage) : on voit
 * l'objet sortir du stockage, pas d'où il y est entré. Les mods qui ramassent directement dans
 * l'inventaire (minage par veine, aimants sans entité) passent pour des arrivées directes.
 */
public final class ItemArrivals {
    public enum Via { PICKUP, SCREEN, DIRECT }

    /** {@code screen} : type de menu ({@code minecraft:generic_9x3}), {@code inventory}, {@code chat} ou nom de l'écran d'un mod. */
    public record Arrival(String item, int count, Via via, String screen, boolean nearPlayer, boolean quest) {}

    /** Contexte du relevé : écran ouvert ({@code null} : aucun), autre joueur à moins de 8 blocs. */
    public record Context(String screen, boolean nearPlayer) {}

    /** Arrivée directe d'un objet ordinaire : notée à partir de ce nombre (un /give donne souvent une pile). */
    static final int BULK = 16;
    /** Au plus ce nombre d'arrivées notées par minute : le journal reste un indice, pas un flot. */
    static final int MAX_PER_MINUTE = 30;
    /** Un ramassage explique une arrivée pendant deux relevés (l'inventaire suit le paquet de ramassage). */
    static final int PICKUP_LIFETIME = 2;

    private final Set<String> valuable;
    private final List<String> patterns;
    private Set<String> questRewards = Set.of();
    /** Dernier relevé ; {@code null} : prochain relevé = point de départ, rien n'est noté. */
    private Map<String, Integer> last;
    private final List<Pickup> pickups = new ArrayList<>();
    private long windowStart = -60_000;
    private int inWindow;

    private static final class Pickup {
        final String item;
        int count;
        int age;

        Pickup(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    public ItemArrivals(Collection<String> valuable, Collection<String> patterns) {
        this.valuable = Set.copyOf(valuable);
        this.patterns = List.copyOf(patterns);
    }

    /** Liste embarquée dans le jar (copie de spec/integrity-rules.json). */
    public static ItemArrivals bundled() {
        try (InputStream in = ItemArrivals.class.getResourceAsStream("/xpack/integrity-rules.json")) {
            if (in == null) throw new IllegalStateException("integrity-rules.json introuvable dans le jar");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new ItemArrivals(strings(json.getAsJsonArray("valuableItems")), strings(json.getAsJsonArray("valuableItemPatterns")));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        if (array != null) array.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    public boolean isValuable(String item) {
        return valuable.contains(item) || patterns.stream().anyMatch(item::contains);
    }

    /** Objets des récompenses de quêtes du modpack ({@link QuestRewards}). */
    public void setQuestRewards(Set<String> items) {
        questRewards = Set.copyOf(items);
    }

    /** Nouveau joueur (entrée dans un monde, réapparition, changement de dimension) : on repart de zéro. */
    public void reset() {
        last = null;
        pickups.clear();
    }

    /** Le serveur annonce que le joueur ramasse un objet par terre. */
    public void onPickup(String item, int count) {
        if (count > 0) pickups.add(new Pickup(item, count));
    }

    /** Relevé de l'inventaire (objet tenu au curseur compris) : les arrivées à noter depuis le précédent. */
    public List<Arrival> observe(Map<String, Integer> inventory, Context ctx, long nowMillis) {
        List<Arrival> out = new ArrayList<>();
        Map<String, Integer> before = last;
        last = new HashMap<>(inventory);
        if (before != null) {
            for (Map.Entry<String, Integer> e : inventory.entrySet()) {
                int gained = e.getValue() - before.getOrDefault(e.getKey(), 0);
                if (gained > 0) arrive(e.getKey(), gained, ctx, nowMillis, out);
            }
        }
        for (Iterator<Pickup> it = pickups.iterator(); it.hasNext(); ) {
            Pickup p = it.next();
            if (p.count <= 0 || ++p.age >= PICKUP_LIFETIME) it.remove();
        }
        return out;
    }

    private void arrive(String item, int gained, Context ctx, long nowMillis, List<Arrival> out) {
        int picked = 0;
        for (Pickup p : pickups) {
            if (!p.item.equals(item) || picked == gained) continue;
            int used = Math.min(p.count, gained - picked);
            p.count -= used;
            picked += used;
        }
        boolean quest = questRewards.contains(item);
        boolean worth = isValuable(item);
        if (picked > 0 && worth) note(new Arrival(item, picked, Via.PICKUP, null, ctx.nearPlayer(), quest), nowMillis, out);
        int rest = gained - picked;
        if (rest <= 0) return;
        Via via = ctx.screen() != null ? Via.SCREEN : Via.DIRECT;
        if (worth || (via == Via.DIRECT && rest >= BULK)) note(new Arrival(item, rest, via, ctx.screen(), ctx.nearPlayer(), quest), nowMillis, out);
    }

    private void note(Arrival a, long nowMillis, List<Arrival> out) {
        if (nowMillis - windowStart >= 60_000) {
            windowStart = nowMillis;
            inWindow = 0;
        }
        if (inWindow >= MAX_PER_MINUTE) return;
        inWindow++;
        out.add(a);
    }
}
