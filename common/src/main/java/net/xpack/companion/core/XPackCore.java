package net.xpack.companion.core;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Cœur du mod compagnon, indépendant de Minecraft et du loader. Appelé depuis le thread client ;
 * les réponses réseau reviennent sur ce thread via {@code mainThread}.
 *
 * <p>Flux : liaison du compte (#34) → ouverture de session (règles du modpack) → événements
 * (progrès #35, statistiques #36, intégrité #43) mis en file puis envoyés par lots (#37).
 */
public final class XPackCore {
    /**
     * {@code PRACTICE} : monde de test (créatif dès le départ, ou choisi par le joueur). Rien n'y est
     * envoyé et rien n'y est signalé : pour XPack, ce monde n'existe pas.
     */
    public enum Status { DISABLED, UNLINKED, OFFLINE, COUNTED, NOT_COUNTED, PRACTICE }

    /** Liaison en cours (#34). {@code userCode} reste null tant que le serveur n'a pas répondu. */
    public record LinkState(String minecraftName, String userCode, String url, boolean failed) {}

    /** Contexte d'une partie, fourni par le code Minecraft à l'entrée dans un monde. */
    public record WorldContext(
            Path storeFile,
            String kind,
            String createdAt,
            boolean cheatsAllowed,
            String playerUuid,
            String playerName,
            ModpackDetector.ModpackInfo modpack,
            JsonObject fingerprint,
            /** Créatif ou spectateur à l'entrée, ou monde créé en créatif : candidat « monde de test ». */
            boolean creativeAtJoin) {

        WorldContext withCheatsAllowed() {
            return new WorldContext(storeFile, kind, createdAt, true, playerUuid, playerName, modpack, fingerprint, creativeAtJoin);
        }
    }

    /** État du joueur à chaque tick (#47, #48). */
    public record PlayerState(String gameMode, boolean invulnerable, boolean mayfly) {}

    /**
     * Un progrès du modpack tel que le jeu l'affiche (#80) : titre et description dans la langue
     * du joueur, cadre ({@code task}, {@code goal}, {@code challenge}), objet de l'icône. Ce sont
     * ces progrès qui deviennent les succès XPack ; on ne rédige rien nous-mêmes.
     */
    public record AdvancementInfo(String id, String parent, String title, String description, String frame, String icon, boolean hidden) {}

    static final long FLUSH_INTERVAL_MS = 5_000;
    static final long MAX_BACKOFF_MS = 300_000;
    static final long LINK_RETRY_MS = 30_000;
    static final int MAX_BATCH = 200;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Config config;
    private final Path configFile;
    private final Credentials credentials;
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
    /** Jeton du compte Minecraft qui joue ce monde (lu dans {@link Credentials}). */
    private String token;
    private boolean practice;

    // Réseau
    private boolean inFlight;
    private long nextFlushAt;
    private long backoffMs = FLUSH_INTERVAL_MS;
    private boolean lastSendFailed;

    // Liaison : indépendante du monde, elle peut partir des réglages depuis le menu principal.
    private String linkUuid;
    private String linkName;
    private String deviceCode;
    private String userCode;
    private boolean linkFailed;
    private boolean linkInFlight;
    private long nextPollAt;
    private long pollIntervalMs = 5_000;

    /** Identifiants des mods chargés, comparés à la liste noire (#50). */
    private List<String> loadedMods = List.of();
    private boolean consentShown;

    /** Commande confirmée par le joueur après l'alerte : laissée passer une fois. */
    private String bypassCommand;

    public XPackCore(Config config, Path configFile, Credentials credentials, ApiClient api, Ui ui, Executor mainThread, Clock clock,
            IntegrityRules rules) {
        this.config = config;
        this.configFile = configFile;
        this.credentials = credentials;
        this.api = api;
        this.ui = ui;
        this.mainThread = mainThread;
        this.clock = clock;
        this.rules = rules;
        migrateLegacyToken();
    }

    /** Anciennes versions : jeton rangé dans config/xpack.json, exporté avec les modpacks. On le déplace. */
    private void migrateLegacyToken() {
        if (config.token == null) return;
        if (config.tokenUuid != null) credentials.put(config.apiUrl, config.tokenUuid, null, config.token);
        config.token = null;
        config.tokenUuid = null;
        config.save(configFile);
    }

    public void setLoadedMods(Collection<String> modIds) {
        loadedMods = List.copyOf(modIds);
    }

    /** Choix du joueur sur l'écran de consentement (aussi utilisable depuis un écran de réglages). */
    public void setConsent(boolean accepted) {
        config.consented = accepted;
        config.save(configFile);
        if (accepted && world != null && !practice) startTracking();
    }

    public Config config() {
        return config;
    }

    /** Réglages modifiés depuis l'écran du mod : sauvegardés aussitôt. */
    public void saveConfig() {
        config.save(configFile);
        if (tracking() && token != null) openSession();
    }

    public boolean isLinked(String minecraftUuid) {
        return credentials.token(config.apiUrl, minecraftUuid) != null;
    }

    /** Pseudos Minecraft liés à XPack sur cette machine. */
    public List<String> linkedNames() {
        return credentials.linkedNames(config.apiUrl);
    }

    /** Délie ce compte Minecraft de XPack (le jeton est aussi révoqué côté serveur si possible). */
    public void unlink(String minecraftUuid) {
        String uuid = normalizeUuid(minecraftUuid);
        String removed = credentials.remove(config.apiUrl, uuid);
        if (removed != null) api.unlink(removed);
        if (uuid.equals(linkUuid)) cancelLinking();
        if (world != null && uuid.equals(normalizeUuid(world.playerUuid()))) {
            token = null;
            sessionId = null;
        }
    }

    private boolean active() {
        return config.enabled && Boolean.TRUE.equals(config.consented) && !uuidMismatch;
    }

    /** Ce monde est suivi : consentement, envoi activé, et ce n'est pas un monde de test. */
    private boolean tracking() {
        return world != null && active() && !practice;
    }

    public boolean inWorld() {
        return world != null;
    }

    public boolean isPracticeWorld() {
        return world != null && practice;
    }

    /**
     * Le joueur déclare ce monde « monde de test » (créatif pour le plaisir, essais…). C'est définitif
     * pour ce monde : sinon on pourrait tout préparer en créatif puis le remettre en suivi. Ce qui
     * était déjà en file part d'abord ; ensuite plus rien n'est envoyé. Le monde n'est pas « signalé ».
     */
    public void markWorldPractice() {
        if (world == null || practice) return;
        flushNow();
        practice = true;
        pending.clear();
        sessionId = null;
        saveWorld();
        ui.notify("xpack.practice.manual");
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
        // Chaque compte Minecraft a son propre jeton : changer de compte ne mélange rien.
        token = credentials.token(config.apiUrl, ctx.playerUuid());
        cheatsNoticeShown = store.cheatsNoticeShown;
        playtimeFloor = store.playtime;
        // Monde de test : déjà déclaré, ou monde que le mod découvre alors qu'il est en créatif.
        // Un monde déjà suivi qui passe ensuite en créatif reste suivi, puis signalé.
        practice = store.practice || (store.fresh && ctx.creativeAtJoin());
        if (practice) {
            if (!store.practice) ui.notify("xpack.practice.auto");
            saveWorld();
            return;
        }
        integrity.syncFromServer(store.status, store.flaggedAt, null);
        pending.addAll(store.pending);
        saveWorld();
        if (config.consented == null) {
            if (!consentShown) {
                consentShown = true; // une fois par lancement : le joueur peut l'ignorer
                ui.askConsent(this::setConsent);
            }
            return;
        }
        if (active()) startTracking();
    }

    /**
     * Monde suivi : information sur les triches, puis session. Compte non lié : un seul rappel par
     * lancement vers les réglages (⚙) ; la liaison ne démarre que si le joueur la demande là-bas,
     * jamais de code ni de lien envoyés d'office dans le chat.
     */
    private void startTracking() {
        noticeCheats();
        if (token != null) openSession();
        else if (!linkHintShown) {
            linkHintShown = true;
            ui.notify("xpack.link.hint");
        }
    }

    private boolean linkHintShown;

    private boolean cheatsNoticeShown;

    /**
     * Triches activées en cours de partie (#74) : « Ouvrir au LAN » avec les commandes, ou le
     * joueur nommé opérateur. Vérifié régulièrement, pas seulement à l'entrée dans le monde.
     */
    public void onCheatsAllowed(boolean allowed) {
        if (world == null || !allowed || world.cheatsAllowed()) return;
        world = world.withCheatsAllowed();
        noticeCheats();
    }

    /** #46 : information (pas de signalement) à la première entrée dans un monde avec triches activées. */
    private void noticeCheats() {
        if (!tracking() || !world.cheatsAllowed() || cheatsNoticeShown || integrity.isFlagged()) return;
        cheatsNoticeShown = true;
        ui.notify("xpack.cheats_enabled");
        saveWorld();
    }

    /**
     * #50 : un mod de triche chargé signale le monde. Vérifié à l'ouverture de session, avec la
     * liste du serveur qui exclut déjà les mods fournis volontairement par le modpack.
     */
    private void checkBlacklist() {
        if (!tracking() || integrity.isFlagged()) return;
        List<String> found = rules.blacklisted(loadedMods);
        if (!found.isEmpty()) flagWithNotice(WorldIntegrity.Reason.BLACKLISTED_MOD, String.join(",", found), "xpack.flagged.blacklist", String.join(", ", found));
    }

    public void onLeaveWorld() {
        if (world == null) return;
        flushNow();
        saveWorld();
        flushWorldFile(); // avant que le monde suivant ne remplace l'écriture en attente
        world = null;
        worldId = null;
        sessionId = null;
        sessionPending = false;
        pending.clear();
        knownAdvancements.clear();
        lastStats.clear();
        integrity.reset();
        uuidMismatch = false;
        token = null;
        practice = false;
        cheatsNoticeShown = false;
        allowedCommands = List.of();
        allowsFlight = false;
        trackedStats = Set.of();
        catalog = null;
        catalogSentHash = null;
        catalogInFlight = false;
        nextCatalogAt = 0;
    }

    private void openSession() {
        if (!tracking() || token == null || sessionId != null || sessionPending) return;
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
                ui.notify("xpack.uuid_mismatch");
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
            // Liste à jour du serveur, propre à ce modpack (sinon : liste embarquée).
            if (b.has("blacklist") && b.get("blacklist").isJsonObject()) applyBlacklist(b.getAsJsonObject("blacklist"));
            checkBlacklist();
            lastSendFailed = false;
            nextFlushAt = 0;
            sendCatalog(); // le catalogue arrivé avant l'ouverture de la session part maintenant
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

    private void applyBlacklist(JsonObject b) {
        if (b == null || !b.has("modIds")) return;
        rules = rules.withBlacklist(IntegrityRules.strings(b.getAsJsonArray("modIds")));
    }

    private void syncIntegrity(JsonObject integ) {
        JsonElement s = integ.get("status");
        JsonElement f = integ.get("flaggedAt");
        JsonElement c = integ.get("clearedAt");
        integrity.syncFromServer(text(s), text(f), text(c));
        saveWorld();
    }

    private static String text(JsonElement e) {
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    // ------------------------------------------------------------------ captures (#35, #36)

    /**
     * Progrès terminés. {@code initialSync} : premier paquet après la connexion (état complet),
     * envoyé comme instantané — le serveur ne le compte ni dans la vraisemblance ni en speedrun.
     */
    public void onAdvancements(Collection<String> doneIds, boolean initialSync, long playtimeTicks) {
        if (world == null || practice) return;
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
        if (world == null || practice) return;
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

    // ------------------------------------------------------------------ catalogue des progrès (#80)

    static final int MAX_CATALOG = 5_000;
    static final long CATALOG_RETRY_MS = 60_000;
    private List<AdvancementInfo> catalog;
    private String catalogLang;
    private String catalogHash;
    /** Empreinte du dernier catalogue envoyé pendant cette partie : on ne renvoie que s'il change. */
    private String catalogSentHash;
    private boolean catalogInFlight;
    private long nextCatalogAt;

    /**
     * Catalogue des progrès du modpack (solo : l'arbre complet du serveur intégré ; multijoueur :
     * ce que le serveur a rendu visible). Envoyé une fois par partie, puis seulement s'il change.
     */
    public void onAdvancementCatalog(List<AdvancementInfo> advancements, String lang) {
        if (world == null || practice) return;
        List<AdvancementInfo> kept = advancements.stream().filter(a -> !a.id().contains(":recipes/")).limit(MAX_CATALOG).toList();
        if (kept.isEmpty()) return;
        catalog = kept;
        catalogLang = lang;
        catalogHash = hashOf(kept, lang);
        sendCatalog();
    }

    private static String hashOf(List<AdvancementInfo> catalog, String lang) {
        StringBuilder sb = new StringBuilder(lang).append('|');
        catalog.stream().map(AdvancementInfo::id).sorted().forEach(id -> sb.append(id).append(','));
        return shortHash(sb.toString());
    }

    private void sendCatalog() {
        if (catalog == null || catalogInFlight || catalogHash.equals(catalogSentHash) || sessionId == null || token == null || !tracking()) return;
        String hash = catalogHash;
        JsonArray list = new JsonArray();
        for (AdvancementInfo a : catalog) {
            JsonObject o = new JsonObject();
            o.addProperty("id", a.id());
            if (a.parent() != null) o.addProperty("parent", a.parent());
            o.addProperty("title", a.title());
            if (a.description() != null && !a.description().isBlank()) o.addProperty("description", a.description());
            o.addProperty("frame", a.frame());
            if (a.icon() != null) o.addProperty("icon", a.icon());
            if (a.hidden()) o.addProperty("hidden", true);
            list.add(o);
        }
        JsonObject body = new JsonObject();
        body.addProperty("lang", catalogLang);
        body.add("advancements", list);
        catalogInFlight = true;
        String wid = worldId;
        api.postCatalog(sessionId, body, token).thenAcceptAsync(res -> {
            catalogInFlight = false;
            if (!wid.equals(worldId)) return;
            if (res.ok() || res.status() == 422) catalogSentHash = hash; // 422 : inutile de renvoyer le même
            else nextCatalogAt = clock.millis() + CATALOG_RETRY_MS;
        }, mainThread);
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
        if (!tracking() || token == null || integrity.isFlagged()) return true;
        if (rules.classify(command, allowedCommands) != IntegrityRules.Verdict.CHEAT) return true;
        ui.showCheatWarning(command, () -> {
            flag(WorldIntegrity.Reason.COMMAND, "/" + stripSlash(command));
            bypassCommand = command;
            resend.accept(command);
        });
        return false;
    }

    public void onPlayerState(PlayerState s, long playtimeTicks) {
        if (!tracking() || integrity.isFlagged()) return;
        switch (s.gameMode()) {
            case "creative" -> flagWithNotice(WorldIntegrity.Reason.CREATIVE, null, "xpack.flagged.creative");
            case "spectator" -> flagWithNotice(WorldIntegrity.Reason.SPECTATOR, null, "xpack.flagged.spectator");
            default -> {
                if (s.invulnerable()) flagWithNotice(WorldIntegrity.Reason.ABILITIES, "invulnerable", "xpack.flagged.godmode");
                else if (s.mayfly() && !allowsFlight) flagWithNotice(WorldIntegrity.Reason.ABILITIES, "mayfly", "xpack.flagged.flight");
            }
        }
    }

    /** Détection automatique : on ne bloque rien, on prévient pourquoi le monde ne compte plus. */
    private void flagWithNotice(WorldIntegrity.Reason reason, String detail, String messageKey, Object... args) {
        if (flag(reason, detail)) ui.integrityAlert(messageKey, args);
    }

    // ------------------------------------------------------------------ support (contestation)

    /** Le support peut répondre : il faut un compte lié (son e-mail sert à la réponse). */
    public boolean canContactSupport() {
        return world != null && token != null;
    }

    /**
     * Rapport joint à une demande de support : uniquement ce qui sert à comprendre une détection.
     * Ni chat, ni journal du jeu, ni chemins : le joueur voit ce résumé avant l'envoi.
     */
    public JsonObject supportReport() {
        JsonObject r = new JsonObject();
        r.addProperty("status", status().name());
        if (world != null) {
            r.addProperty("worldId", worldId);
            r.addProperty("kind", world.kind());
            ModpackDetector.ModpackInfo info = world.modpack();
            JsonObject m = new JsonObject();
            m.addProperty("source", info.source());
            if (info.projectId() != null) m.addProperty("projectId", info.projectId());
            if (info.name() != null) m.addProperty("name", info.name());
            if (info.version() != null) m.addProperty("version", info.version());
            r.add("modpack", m);
            r.add("fingerprint", world.fingerprint() == null ? new JsonObject() : world.fingerprint().deepCopy());
        }
        JsonArray detections = new JsonArray();
        for (WorldIntegrity.Flag f : integrity.reasons()) {
            JsonObject d = new JsonObject();
            d.addProperty("reason", f.reason().wire);
            d.addProperty("at", f.at());
            if (f.detail() != null) d.addProperty("detail", f.detail());
            detections.add(d);
        }
        r.add("detections", detections);
        JsonArray mods = new JsonArray();
        loadedMods.stream().sorted().forEach(mods::add);
        r.add("mods", mods);
        return r;
    }

    /** Envoie la demande ; {@code done} reçoit vrai si le serveur l'a enregistrée. */
    public void sendSupport(String message, Consumer<Boolean> done) {
        if (!canContactSupport()) {
            done.accept(false);
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("message", message == null ? "" : message.strip());
        body.add("report", supportReport());
        api.supportReport(body, token).thenAcceptAsync(res -> done.accept(res.ok()), mainThread);
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
    /** Dernier temps de jeu connu de ce monde (sauvegardé) : plancher en multijoueur, voir {@link #playtimeFloor()}. */
    private long playtimeFloor;

    public void setPlaytime(long ticks) {
        currentPlaytime = ticks;
    }

    /**
     * Multijoueur (#74) : le temps du joueur n'arrive qu'avec ses statistiques, quelques secondes
     * après la connexion. D'ici là, on repart du dernier temps envoyé pour qu'il ne recule pas.
     */
    public long playtimeFloor() {
        return Math.max(playtimeFloor, currentPlaytime);
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
        tickLinking(t);
        if (!tracking() || token == null) return;
        if (sessionId == null && !sessionPending && t >= nextFlushAt) {
            nextFlushAt = t + backoffMs;
            openSession();
        }
        if (t >= nextFlushAt) flushNow();
        if (catalog != null && !catalogHash.equals(catalogSentHash) && t >= nextCatalogAt) sendCatalog();
    }

    private void flushNow() {
        if (sessionId == null || inFlight || pending.isEmpty() || token == null) return;
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
        api.postEvents(sid, body, token).thenAcceptAsync(res -> {
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
        // Paliers du modpack (#60) : toast spécial, après ceux des succès du lot. 100 % dans « completed »,
        // 25/50/75 % dans « milestone ».
        String name = world != null ? world.modpack().name() : null;
        if (b.has("completed") && b.get("completed").isJsonObject()) {
            JsonObject c = b.getAsJsonObject("completed");
            ui.toastCompleted(name, c.has("achievements") ? c.get("achievements").getAsInt() : 0, c.has("playtimeTicks") ? c.get("playtimeTicks").getAsLong() : 0);
        } else if (b.has("milestone") && b.get("milestone").isJsonObject()) {
            JsonObject m = b.getAsJsonObject("milestone");
            if (m.has("tier")) ui.toastMilestone(name, m.get("tier").getAsInt(), m.has("achievements") ? m.get("achievements").getAsInt() : 0, m.has("total") ? m.get("total").getAsInt() : 0);
        }
    }

    // ------------------------------------------------------------------ liaison du compte (#34)

    /**
     * Démarre la liaison de ce compte Minecraft : depuis un monde suivi, ou depuis les réglages,
     * menu principal compris. Sans effet si elle est déjà en cours pour ce compte.
     */
    public void startLinking(String minecraftUuid, String minecraftName) {
        String uuid = normalizeUuid(minecraftUuid);
        if (uuid.equals(linkUuid) && !linkFailed) return;
        cancelLinking();
        linkUuid = uuid;
        linkName = minecraftName;
        nextPollAt = 0;
    }

    public void cancelLinking() {
        linkUuid = null;
        linkName = null;
        deviceCode = null;
        userCode = null;
        linkFailed = false;
        linkInFlight = false;
    }

    /** Liaison en cours, ou null. */
    public LinkState linkState() {
        if (linkUuid == null) return null;
        return new LinkState(linkName, userCode, userCode == null ? null : linkUrl(userCode), linkFailed);
    }

    /** Lien direct vers la page de liaison, code prérempli : le joueur n'a plus qu'à confirmer. */
    String linkUrl(String code) {
        return config.linkUrl + (config.linkUrl.contains("?") ? "&" : "?") + "code=" + code;
    }

    private void tickLinking(long t) {
        if (linkUuid == null || linkInFlight || t < nextPollAt) return;
        String uuid = linkUuid;
        linkInFlight = true;
        if (deviceCode == null) {
            api.linkStart(uuid, linkName).thenAcceptAsync(res -> {
                if (!uuid.equals(linkUuid)) return; // liaison annulée ou relancée entre-temps
                linkInFlight = false;
                if (!res.ok()) {
                    linkFailed = true; // API injoignable : nouvel essai un peu plus tard
                    nextPollAt = clock.millis() + LINK_RETRY_MS;
                    return;
                }
                linkFailed = false;
                deviceCode = res.body().get("deviceCode").getAsString();
                userCode = res.body().get("userCode").getAsString();
                pollIntervalMs = res.body().get("interval").getAsLong() * 1000;
                nextPollAt = clock.millis() + pollIntervalMs;
            }, mainThread);
            return;
        }
        api.linkPoll(deviceCode).thenAcceptAsync(res -> {
            if (!uuid.equals(linkUuid)) return;
            linkInFlight = false;
            if (res.status() == 200) {
                String newToken = res.body().get("token").getAsString();
                credentials.put(config.apiUrl, uuid, linkName, newToken);
                cancelLinking();
                ui.notify("xpack.link.done");
                if (world != null && uuid.equals(normalizeUuid(world.playerUuid()))) {
                    token = newToken;
                    openSession();
                }
            } else if (res.status() == 202 || res.status() == 0) {
                nextPollAt = clock.millis() + pollIntervalMs;
            } else {
                // Code expiré : pas de relance automatique (sinon un message toutes les 10 min).
                cancelLinking();
                ui.notify("xpack.link.expired");
            }
        }, mainThread);
    }

    private void forgetToken() {
        if (world != null) credentials.remove(config.apiUrl, world.playerUuid());
        token = null;
        sessionId = null;
        ui.notify("xpack.link.revoked"); // « relie ton compte via ⚙ »
    }

    public Status status() {
        if (world != null && practice) return Status.PRACTICE;
        if (!active()) return Status.DISABLED;
        if (integrity.isFlagged()) return Status.NOT_COUNTED;
        if (token == null) return Status.UNLINKED;
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
        s.cheatsNoticeShown = cheatsNoticeShown;
        s.practice = practice;
        s.playtime = playtimeFloor();
        worldWriter.write(world.storeFile(), GSON.toJson(s));
    }

    /**
     * Écritures du fichier de monde (#74) : synchrones par défaut (tests), en arrière-plan en jeu
     * pour ne pas faire saccader le thread client à chaque événement.
     */
    private WorldWriter worldWriter = new WorldWriter(Runnable::run);

    public void writeWorldFilesWith(Executor executor) {
        worldWriter = new WorldWriter(executor);
    }

    /** Écrit tout de suite la dernière version en attente (sortie du monde, fermeture du jeu). */
    public void flushWorldFile() {
        worldWriter.flush();
    }

    /** Fichier {@code xpack.json} du monde : identifiant stable, statut, événements non envoyés. */
    static final class WorldStore {
        String worldId;
        String firstSeen;
        String status;
        String flaggedAt;
        boolean cheatsNoticeShown;
        /** Monde de test : rien n'est envoyé (voir {@link XPackCore#markWorldPractice()}). */
        boolean practice;
        /** Dernier temps de jeu connu (ticks), plancher du temps de jeu en multijoueur (#74). */
        long playtime;
        List<JsonObject> pending = new ArrayList<>();
        /** Premier passage du mod dans ce monde (aucun fichier). Non sauvegardé. */
        transient boolean fresh;

        static WorldStore load(Path file) {
            WorldStore s = null;
            try {
                if (Files.isRegularFile(file)) s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), WorldStore.class);
            } catch (IOException | RuntimeException ignored) {
                // fichier abîmé : on repart d'un état neuf (le serveur garde le statut d'intégrité)
            }
            if (s == null) s = new WorldStore();
            s.fresh = !Files.isRegularFile(file);
            if (s.worldId == null) s.worldId = UUID.randomUUID().toString();
            if (s.pending == null) s.pending = new ArrayList<>();
            return s;
        }

    }

    /** Ne garde que la dernière version à écrire : une rafale d'événements donne une seule écriture. */
    static final class WorldWriter {
        private final Executor executor;
        private final AtomicReference<Map.Entry<Path, String>> latest = new AtomicReference<>();

        WorldWriter(Executor executor) {
            this.executor = executor;
        }

        void write(Path file, String json) {
            if (latest.getAndSet(Map.entry(file, json)) == null) executor.execute(this::flush);
        }

        /** Synchronisé : une écriture plus ancienne ne peut pas passer après une plus récente. */
        synchronized void flush() {
            Map.Entry<Path, String> w = latest.getAndSet(null);
            if (w == null) return;
            try {
                Files.createDirectories(w.getKey().getParent());
                Files.writeString(w.getKey(), w.getValue(), StandardCharsets.UTF_8);
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
