package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Classification des commandes (#44, #45). Portage de {@code src/domain/integrity.ts} :
 * mêmes règles, même fichier source ({@code spec/integrity-rules.json}), mêmes cas de test.
 */
public final class IntegrityRules {
    public enum Verdict { ALLOWED, CHEAT, NEUTRAL }

    private final Set<String> cheatCommands;
    private final Set<String> alwaysAllowed;
    private final Set<String> dangerousIfAllowed;
    private final Set<String> blacklistedMods;
    private final Set<String> invulnerabilityIgnoredWithMods;

    public IntegrityRules(Collection<String> cheatCommands, Collection<String> alwaysAllowed, Collection<String> dangerousIfAllowed) {
        this(cheatCommands, alwaysAllowed, dangerousIfAllowed, J8.listOf());
    }

    public IntegrityRules(Collection<String> cheatCommands, Collection<String> alwaysAllowed, Collection<String> dangerousIfAllowed,
            Collection<String> blacklistedMods) {
        this(cheatCommands, alwaysAllowed, dangerousIfAllowed, blacklistedMods, J8.listOf());
    }

    public IntegrityRules(Collection<String> cheatCommands, Collection<String> alwaysAllowed, Collection<String> dangerousIfAllowed,
            Collection<String> blacklistedMods, Collection<String> invulnerabilityIgnoredWithMods) {
        this.cheatCommands = J8.copyOfSet(cheatCommands);
        this.alwaysAllowed = J8.copyOfSet(alwaysAllowed);
        this.dangerousIfAllowed = J8.copyOfSet(dangerousIfAllowed);
        this.blacklistedMods = J8.copyOfSet(blacklistedMods);
        this.invulnerabilityIgnoredWithMods = J8.copyOfSet(invulnerabilityIgnoredWithMods);
    }

    /** Règles embarquées dans le jar (copie de spec/integrity-rules.json). */
    public static IntegrityRules bundled() {
        try (InputStream in = IntegrityRules.class.getResourceAsStream("/xpack/integrity-rules.json")) {
            if (in == null) throw new IllegalStateException("integrity-rules.json introuvable dans le jar");
            JsonObject json = J8.parseJson(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new IntegrityRules(strings(json.getAsJsonArray("cheatCommands")), strings(json.getAsJsonArray("alwaysAllowedCommands")),
                    strings(json.getAsJsonArray("dangerousIfAllowed")), strings(json.getAsJsonArray("blacklistedMods")),
                    strings(json.getAsJsonArray("invulnerabilityIgnoredWithMods")));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Remplace les listes par celles renvoyées par l'API (mises à jour sans nouvelle version du mod). */
    public IntegrityRules withServerLists(List<String> cheat, List<String> allowed) {
        return new IntegrityRules(cheat.isEmpty() ? cheatCommands : cheat, allowed.isEmpty() ? alwaysAllowed : allowed, dangerousIfAllowed, blacklistedMods, invulnerabilityIgnoredWithMods);
    }

    /**
     * Liste noire envoyée par le serveur pour ce modpack (#50) : déjà privée des mods que le
     * modpack fournit volontairement. {@code null} (ancien serveur) garde la liste embarquée.
     */
    public IntegrityRules withBlacklist(List<String> modIds) {
        return modIds == null ? this : new IntegrityRules(cheatCommands, alwaysAllowed, dangerousIfAllowed, modIds, invulnerabilityIgnoredWithMods);
    }

    /** Mods chargés qui figurent sur la liste noire, triés. */
    public List<String> blacklisted(Collection<String> loadedModIds) {
        return loadedModIds.stream().map(id -> id.toLowerCase(Locale.ROOT)).filter(blacklistedMods::contains).distinct().sorted().collect(java.util.stream.Collectors.toList());
    }

    /** Un mod chargé rend l'invulnérabilité normale dans ce modpack (GT: New Horizons…) : jamais signalée. */
    public boolean ignoresInvulnerability(Collection<String> loadedModIds) {
        return loadedModIds.stream().map(id -> id.toLowerCase(Locale.ROOT)).anyMatch(invulnerabilityIgnoredWithMods::contains);
    }

    static List<String> strings(JsonArray array) {
        List<String> out = new java.util.ArrayList<>();
        if (array != null) array.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    /** Nom de la commande, sans « / », sans namespace ({@code minecraft:give} → {@code give}), en minuscules. */
    public static String commandName(String input) {
        String trimmed = input.trim();
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        String first = trimmed.split("\\s+", 2)[0];
        int colon = first.indexOf(':');
        String bare = colon >= 0 ? first.substring(colon + 1) : first;
        return bare.toLowerCase(Locale.ROOT);
    }

    /**
     * Verdict pour une commande tapée par le joueur. La liste du modpack est prioritaire sur la
     * liste de triche : un créateur peut autoriser {@code tp}.
     */
    public Verdict classify(String input, Collection<String> modpackAllowed) {
        String name = commandName(input);
        if (name.isEmpty()) return Verdict.NEUTRAL;
        Set<String> allowed = new LinkedHashSet<>(alwaysAllowed);
        for (String c : modpackAllowed) allowed.add(commandName(c));
        if (allowed.contains(name)) return Verdict.ALLOWED;
        if (cheatCommands.contains(name)) return Verdict.CHEAT;
        return Verdict.NEUTRAL;
    }

    public List<String> dangerousAllowed(Collection<String> modpackAllowed) {
        return modpackAllowed.stream().map(IntegrityRules::commandName).filter(dangerousIfAllowed::contains).collect(java.util.stream.Collectors.toList());
    }
}
