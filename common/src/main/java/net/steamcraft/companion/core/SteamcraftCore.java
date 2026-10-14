package net.steamcraft.companion.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Cœur du mod compagnon, indépendant de Minecraft et du loader. Appelé depuis le thread client ;
 * les réponses réseau reviennent sur ce thread via {@code mainThread}.
 *
 * <p>Flux : liaison du compte (#34) → ouverture de session (règles du modpack) → événements
 * (progrès #35, statistiques #36, intégrité #43) mis en file puis envoyés par lots (#37).
 */
public final class SteamcraftCore {
    public enum Status { DISABLED, UNLINKED, OFFLINE, COUNTED, NOT_COUNTED }

    /** Contexte d'une partie, fourni par le code Minecraft à l'entrée dans un monde. */
    public record WorldContext(
            Path storeFile,
            String kind,
            String createdAt,
            boolean cheatsAllowed,
            String playerUuid,
            String playerName,
            ModpackDetector.ModpackInfo modpack,
            JsonObject fingerprint) {}

    /** État du joueur à chaque tick (#47, #48). */
    public record PlayerState(String gameMode, boolean invulnerable, boolean mayfly) {}

    static final long FLUSH_INTERVAL_MS = 5_000;
    static final long MAX_BACKOFF_MS = 300_000;
    static final int MAX_BATCH = 200;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Config config;
    private final Path configFile;
    private final ApiClient api;
    private final Ui ui;
    private final Executor mainThread;
    private final Clock clock;
    private IntegrityRules rules;

    // Partie en cours
    private WorldContext world;
    private String worldId;
    private String firstSeen;
    private final WorldIntegrity integrity = new WorldIntegrity();
    private final List<JsonObject> pending = new ArrayList<>();
    private final Set<String> knownAdvancements = new HashSet<>();
    private final Map<String, Integer> lastStats = new HashMap<>();
    private String sessionId;
    private boolean sessionPending;
    private int nextSeq;
    private List<String> allowedCommands = List.of();
    private boolean allowsFlight;
    private Set<String> trackedStats = Set.of();
    private boolean uuidMismatch;

    // Réseau
    private boolean inFlight;
    private long nextFlushAt;
    private long backoffMs = FLUSH_INTERVAL_MS;
    private boolean lastSendFailed;

    // Liaison
    private String deviceCode;
    private long nextPollAt;
    private long pollIntervalMs = 5_000;
    private boolean linking;

    /** Commande confirmée par le joueur après l'alerte : laissée passer une fois. */
    private String bypassCommand;

    public SteamcraftCore(Config config, Path configFile, ApiClient api, Ui ui, Executor mainThread, Clock clock, IntegrityRules rules) {
        this.config = config;
        this.configFile = configFile;
        this.api = api;
        this.ui = ui;
        this.mainThread = mainThread;
        this.clock = clock;
        this.rules = rules;
    }

    private boolean active() {
        return config.enabled && config.consented && !uuidMismatch;
    }

    private String now() {
        return Instant.now(clock).toString();
    }

    // ------------------------------------------------------------------ cycle de vie d'un monde

    public void onJoinWorld(WorldContext ctx) {
        onLeaveWorld();
        world = ctx;
        WorldStore store = WorldStore.load(ctx.storeFile());
        worldId = store.worldId;
        // Date de création connue du mod : la première fois qu'il voit ce monde.
        firstSeen = store.firstSeen != null ? store.firstSeen : ctx.createdAt();
        integrity.syncFromServer(store.status, store.flaggedAt);
        pending.addAll(store.pending);
        if (config.token != null && config.tokenUuid != null && !config.tokenUuid.equalsIgnoreCase(normalizeUuid(ctx.playerUuid()))) {
            // Le jeton appartient à un autre compte Minecraft : on n'envoie rien pour celui-ci.
            uuidMismatch = true;
            ui.notify("steamcraft.uuid_mismatch");
        }
        saveWorld();
        openSession();
    }

    public void onLeaveWorld() {
        if (world == null) return;
        flushNow();
        saveWorld();
        world = null;
        worldId = null;
        sessionId = null;
        sessionPending = false;
        pending.clear();
        knownAdvancements.clear();
        lastStats.clear();
        integrity.reset();
        uuidMismatch = false;
        allowedCommands = List.of();
        allowsFlight = false;
        trackedStats = Set.of();
    }

    private void openSession() {
        if (world == null || !active() || config.token == null || sessionId != null || sessionPending) return;
        sessionPending = true;
        JsonObject body = new JsonObject();
        JsonObject w = new JsonObject();
        w.addProperty("id", worldId);
        w.addProperty("kind", world.kind());
        w.addProperty("createdAt", firstSeen);
        w.addProperty("cheatsAllowed", world.cheatsAllowed());
        body.add("world", w);
        JsonObject m = new JsonObject();
        ModpackDetector.ModpackInfo info = world.modpack();
        m.addProperty("source", info.source());
        if (info.projectId() != null) m.addProperty("projectId", info.projectId());
        if (info.name() != null) m.addProperty("name", info.name());
        if (info.version() != null) m.addProperty("version", info.version());
        body.add("modpack", m);
        JsonObject p = new JsonObject();
        p.addProperty("uuid", world.playerUuid());
        body.add("player", p);
        body.add("fingerprint", world.fingerprint() == null ? new JsonObject() : world.fingerprint());
        String token = config.token;
        String wid = worldId;
        api.openSession(body, token).thenAcceptAsync(res -> {
            sessionPending = false;
            if (!wid.equals(worldId)) return; // le joueur a changé de monde entre-temps
            if (res.status() == 401) {
                forgetToken();
                return;
            }
            if (res.status() == 409) {
                uuidMismatch = true;
                ui.notify("steamcraft.uuid_mismatch");
                return;
            }
            if (!res.ok()) {
                lastSendFailed = true;
                return;
            }
            JsonObject b = res.body();
            sessionId = b.get("sessionId").getAsString();
            nextSeq = 0;
            applyRules(b.getAsJsonObject("rules"));
            JsonObject integ = b.getAsJsonObject("integrity");
            if (integ != null) syncIntegrity(integ);
            lastSendFailed = false;
            nextFlushAt = 0;
        }, mainThread);
    }

    private void applyRules(JsonObject r) {
        if (r == null) return;
        JsonObject integ = r.has("integrity") && r.get("integrity").isJsonObject() ? r.getAsJsonObject("integrity") : new JsonObject();
        allowedCommands = IntegrityRules.strings(integ.getAsJsonArray("allowedCommands"));
        allowsFlight = integ.has("allowsFlight") && integ.get("allowsFlight").getAsBoolean();
        trackedStats = new HashSet<>(IntegrityRules.strings(r.getAsJsonArray("trackedStats")));
        rules = rules.withServerLists(IntegrityRules.strings(r.getAsJsonArray("cheatCommands")), IntegrityRules.strings(r.getAsJsonArray("alwaysAllowedCommands")));
    }

    private void syncIntegrity(JsonObject integ) {
        JsonElement s = integ.get("status");
        JsonElement f = integ.get("flaggedAt");
        integrity.syncFromServer(s == null || s.isJsonNull() ? null : s.getAsString(), f == null || f.isJsonNull() ? null : f.getAsString());
        saveWorld();
    }

    // ------------------------------------------------------------------ captures (#35, #36)

    /**
     * Progrès terminés. {@code initialSync} : premier paquet après la connexion (état complet),
     * envoyé comme instantané — le serveur ne le compte ni dans la vraisemblance ni en speedrun.
     */
    public void onAdvancements(Collection<String> doneIds, boolean initialSync, long playtimeTicks) {
        if (world == null) return;
        for (String id : doneIds) {
            if (id.contains(":recipes/")) continue; // les recettes débloquées ne sont pas des succès
            if (!knownAdvancements.add(id) && !initialSync) continue;
            JsonObject e = event("advancement", playtimeTicks);
            // Identifiant déterministe : un progrès n'est obtenu qu'une fois par monde (dédoublonnage serveur).
            e.addProperty("id", "adv-" + shortHash(worldId + "|" + id));
            e.addProperty("key", id);
            if (initialSync) e.addProperty("snapshot", true);
            enqueue(e);
        }
    }

    public void onStats(Map<String, Integer> stats, long playtimeTicks) {
        if (world == null) return;
        for (Map.Entry<String, Integer> s : stats.entrySet()) {
            if (!trackedStats.contains(s.getKey())) continue;
            Integer prev = lastStats.put(s.getKey(), s.getValue());
            if (prev != null && prev.equals(s.getValue())) continue;
            JsonObject e = event("stat", playtimeTicks);
            e.addProperty("key", s.getKey());
            e.addProperty("value", s.getValue());
            enqueue(e);
        }
    }

    public Set<String> trackedStats() {
        return Set.copyOf(trackedStats);
    }

    // ------------------------------------------------------------------ anti-triche (#44, #47, #48)

    /**
     * Appelé avant l'envoi d'une commande. Renvoie {@code true} si elle peut partir tout de suite ;
     * sinon l'alerte est affichée et {@code resend} sera appelé si le joueur confirme.
     */
    public boolean onCommand(String command, Consumer<String> resend) {
        if (command.equals(bypassCommand)) {
            bypassCommand = null;
            return true;
        }
        if (world == null || !active() || config.token == null || integrity.isFlagged()) return true;
        if (rules.classify(command, allowedCommands) != IntegrityRules.Verdict.CHEAT) return true;
        ui.showCheatWarning(command, () -> {
            flag(WorldIntegrity.Reason.COMMAND, "/" + stripSlash(command));
            bypassCommand = command;
            resend.accept(command);
        });
        return false;
    }

    public void onPlayerState(PlayerState s, long playtimeTicks) {
        if (world == null || !active() || integrity.isFlagged()) return;
        switch (s.gameMode()) {
            case "creative" -> flagWithNotice(WorldIntegrity.Reason.CREATIVE, null, "steamcraft.flagged.creative");
            case "spectator" -> flagWithNotice(WorldIntegrity.Reason.SPECTATOR, null, "steamcraft.flagged.spectator");
            default -> {
                if (s.invulnerable()) flagWithNotice(WorldIntegrity.Reason.ABILITIES, "invulnerable", "steamcraft.flagged.godmode");
                else if (s.mayfly() && !allowsFlight) flagWithNotice(WorldIntegrity.Reason.ABILITIES, "mayfly", "steamcraft.flagged.flight");
            }
        }
    }

    private void flagWithNotice(WorldIntegrity.Reason reason, String detail, String messageKey) {
        if (flag(reason, detail)) ui.notify(messageKey);
    }

    private boolean flag(WorldIntegrity.Reason reason, String detail) {
        String at = now();
        boolean first = integrity.flag(reason, at, detail);
        JsonObject e = event("integrity", currentPlaytime);
        e.addProperty("reason", reason.wire);
        if (detail != null) e.addProperty("detail", detail);
        enqueue(e);
        saveWorld();
        return first;
    }

    public WorldIntegrity integrity() {
        return integrity;
    }

    // ------------------------------------------------------------------ envoi (#37)

    private long currentPlaytime;

    public void setPlaytime(long ticks) {
        currentPlaytime = ticks;
    }

    private JsonObject event(String type, long playtimeTicks) {
        currentPlaytime = Math.max(currentPlaytime, playtimeTicks);
        JsonObject e = new JsonObject();
        e.addProperty("id", "e-" + UUID.randomUUID());
        e.addProperty("type", type);
        e.addProperty("at", now());
        e.addProperty("playtimeTicks", playtimeTicks);
        return e;
    }

    private void enqueue(JsonObject e) {
        pending.add(e);
        saveWorld();
    }

    /** À appeler à chaque tick client : liaison du compte et envoi périodique. */
    public void tick() {
        long t = clock.millis();
        if (!active()) return;
        if (config.token == null) {
            tickLinking(t);
            return;
        }
        if (world != null && sessionId == null && !sessionPending && t >= nextFlushAt) {
            nextFlushAt = t + backoffMs;
            openSession();
        }
        if (t >= nextFlushAt) flushNow();
    }

    private void flushNow() {
        if (sessionId == null || inFlight || pending.isEmpty() || config.token == null) return;
        List<JsonObject> batch = new ArrayList<>(pending.subList(0, Math.min(MAX_BATCH, pending.size())));
        JsonArray events = new JsonArray();
        for (JsonObject e : batch) {
            JsonObject copy = e.deepCopy();
            copy.addProperty("seq", nextSeq++);
            events.add(copy);
        }
        JsonObject body = new JsonObject();
        body.add("events", events);
        inFlight = true;
        String sid = sessionId;
        String wid = worldId;
        api.postEvents(sid, body, config.token).thenAcceptAsync(res -> {
            inFlight = false;
            // Réponse d'un monde quitté : les événements restent sauvegardés dans son fichier et
            // seront renvoyés (dédoublonnés par identifiant) au prochain passage.
            if (!wid.equals(worldId)) return;
            long t = clock.millis();
            if (res.ok()) {
                pending.removeAll(batch);
                backoffMs = FLUSH_INTERVAL_MS;
                nextFlushAt = t + FLUSH_INTERVAL_MS;
                lastSendFailed = false;
                handleBatchResult(res.body());
                saveWorld();
            } else if (res.status() == 401) {
                forgetToken();
            } else if (res.status() == 404) {
                sessionId = null; // session inconnue (serveur réinitialisé) : on en rouvre une
            } else if (res.status() == 422) {
                pending.removeAll(batch); // lot refusé : inutile de le renvoyer
                saveWorld();
            } else {
                lastSendFailed = true;
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                nextFlushAt = t + backoffMs;
            }
        }, mainThread);
    }

    private void handleBatchResult(JsonObject b) {
        if (b.has("integrity") && b.get("integrity").isJsonObject()) syncIntegrity(b.getAsJsonObject("integrity"));
        if (!config.toasts || !b.has("unlocked")) return;
        for (JsonElement el : b.getAsJsonArray("unlocked")) {
            JsonObject u = el.getAsJsonObject();
            ui.toastUnlocked(u.get("name").getAsString(), u.get("points").getAsInt(), u.has("rarity") ? u.get("rarity").getAsString() : "commun");
        }
    }

    // ------------------------------------------------------------------ liaison du compte (#34)

    private void tickLinking(long t) {
        if (world == null || linking && t < nextPollAt) return;
        if (deviceCode == null) {
            if (linking) return;
            linking = true;
            nextPollAt = Long.MAX_VALUE;
            api.linkStart(normalizeUuid(world.playerUuid()), world.playerName()).thenAcceptAsync(res -> {
                if (!res.ok()) {
                    linking = false;
                    nextPollAt = clock.millis() + MAX_BACKOFF_MS; // API injoignable : on réessaiera plus tard
                    return;
                }
                deviceCode = res.body().get("deviceCode").getAsString();
                pollIntervalMs = res.body().get("interval").getAsLong() * 1000;
                nextPollAt = clock.millis() + pollIntervalMs;
                ui.notify("steamcraft.link.code", config.linkUrl, res.body().get("userCode").getAsString());
            }, mainThread);
            return;
        }
        nextPollAt = Long.MAX_VALUE;
        String uuid = normalizeUuid(world.playerUuid());
        api.linkPoll(deviceCode).thenAcceptAsync(res -> {
            if (res.status() == 200) {
                config.token = res.body().get("token").getAsString();
                config.tokenUuid = uuid;
                config.save(configFile);
                deviceCode = null;
                linking = false;
                ui.notify("steamcraft.link.done");
                openSession();
            } else if (res.status() == 202 || res.status() == 0) {
                nextPollAt = clock.millis() + pollIntervalMs;
            } else {
                // Code expiré ou inconnu : nouvelle demande au prochain tick.
                deviceCode = null;
                linking = false;
                nextPollAt = 0;
            }
        }, mainThread);
    }

    private void forgetToken() {
        config.token = null;
        config.tokenUuid = null;
        config.save(configFile);
        sessionId = null;
        ui.notify("steamcraft.link.revoked");
    }

    public Status status() {
        if (!active()) return Status.DISABLED;
        if (integrity.isFlagged()) return Status.NOT_COUNTED;
        if (config.token == null) return Status.UNLINKED;
        if (sessionId == null || lastSendFailed) return Status.OFFLINE;
        return Status.COUNTED;
    }

    // ------------------------------------------------------------------ persistance par monde

    private void saveWorld() {
        if (world == null) return;
        WorldStore s = new WorldStore();
        s.worldId = worldId;
        s.firstSeen = firstSeen;
        s.status = integrity.isFlagged() ? "signale" : "propre";
        s.flaggedAt = integrity.flaggedAt();
        s.pending = new ArrayList<>(pending);
        s.save(world.storeFile());
    }

    /** Fichier {@code steamcraft.json} du monde : identifiant stable, statut, événements non envoyés. */
    static final class WorldStore {
        String worldId;
        String firstSeen;
        String status;
        String flaggedAt;
        List<JsonObject> pending = new ArrayList<>();

        static WorldStore load(Path file) {
            WorldStore s = null;
            try {
                if (Files.isRegularFile(file)) s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), WorldStore.class);
            } catch (IOException | RuntimeException ignored) {
                // fichier abîmé : on repart d'un état neuf (le serveur garde le statut d'intégrité)
            }
            if (s == null) s = new WorldStore();
            if (s.worldId == null) s.worldId = UUID.randomUUID().toString();
            if (s.pending == null) s.pending = new ArrayList<>();
            return s;
        }

        void save(Path file) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // disque indisponible : les événements restent en mémoire
            }
        }
    }

    // ------------------------------------------------------------------ utilitaires

    static String normalizeUuid(String uuid) {
        return uuid == null ? null : uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static String stripSlash(String c) {
        return c.startsWith("/") ? c.substring(1) : c;
    }

    static String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
