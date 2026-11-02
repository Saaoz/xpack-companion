package net.xpack.companion.core;

import java.util.Locale;

/**
 * Statistiques de Minecraft 1.12.2 (« stat.mineBlock.minecraft.stone ») traduites au format commun
 * du site et du serveur (« minecraft:mined/minecraft:stone »), pour que les succès basés sur des
 * statistiques soient les mêmes que dans les versions récentes.
 */
public final class LegacyStats {
    private LegacyStats() {}

    /** Familles d'objets et de blocs : préfixe 1.12.2 → type de statistique récent. */
    private static final String[][] FAMILIES = {
        {"stat.mineBlock.", "minecraft:mined"},
        {"stat.craftItem.", "minecraft:crafted"},
        {"stat.useItem.", "minecraft:used"},
        {"stat.breakItem.", "minecraft:broken"},
        {"stat.pickup.", "minecraft:picked_up"},
        {"stat.drop.", "minecraft:dropped"},
    };

    /** Clé commune, ou {@code null} si la statistique n'existe pas dans ce format (succès, kills par entité…). */
    public static String toKey(String legacyId) {
        if (legacyId == null || !legacyId.startsWith("stat.")) return null;
        for (String[] f : FAMILIES) {
            if (legacyId.startsWith(f[0])) {
                String rest = legacyId.substring(f[0].length());
                int dot = rest.indexOf('.');
                if (dot <= 0 || dot == rest.length() - 1) return null;
                return f[1] + "/" + rest.substring(0, dot) + ":" + rest.substring(dot + 1);
            }
        }
        String name = legacyId.substring("stat.".length());
        if (name.isEmpty() || name.indexOf('.') >= 0) return null; // famille inconnue (kills par entité, mods…)
        if (name.equals("playOneMinute")) return "minecraft:custom/minecraft:play_time";
        return "minecraft:custom/minecraft:" + snake(name);
    }

    private static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
