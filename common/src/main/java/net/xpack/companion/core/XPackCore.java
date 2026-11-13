package net.xpack.companion.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.xpack.companion.api.XPackEvents;
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
    public enum Status { DISABLED, UNLINKED, OFFLINE, COUNTED, NOT_COUNTED, PRACTICE, STARTED_LATE }

    /** Liaison en cours (#34). {@code userCode} reste null tant que le serveur n'a pas répondu. */
    public static final class LinkState {
        private final String minecraftName;
        private final String userCode;
        private final String url;
        private final boolean failed;

        public LinkState(String minecraftName, String userCode, String url, boolean failed) {
            this.minecraftName = minecraftName;
            this.userCode = userCode;
            this.url = url;
            this.failed = failed;
        }

        public String minecraftName() {
            return minecraftName;
        }

        public String userCode() {
            return userCode;
        }

        public String url() {
            return url;
        }

        public boolean failed() {
            return failed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LinkState)) return false;
            LinkState other = (LinkState) o;
            return java.util.Objects.equals(minecraftName, other.minecraftName) && java.util.Objects.equals(userCode, other.userCode) && java.util.Objects.equals(url, other.url) && java.util.Objects.equals(failed, other.failed);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(minecraftName, userCode, url, failed);
        }

        @Override
        public String toString() {
            return "LinkState[" + "minecraftName=" + minecraftName + ", userCode=" + userCode + ", url=" + url + ", failed=" + failed + "]";
        }
    }

    /** Contexte d'une partie, fourni par le code Minecraft à l'entrée dans un monde. */
    public static final class WorldContext {
        private final Path storeFile;
        private final String kind;
        private final String createdAt;
        private final boolean cheatsAllowed;
        private final String playerUuid;
        private final String playerName;
        private final ModpackDetector.ModpackInfo modpack;
        private final JsonObject fingerprint;
        private final boolean creativeAtJoin;

        public WorldContext(Path storeFile, String kind, String createdAt, boolean cheatsAllowed, String playerUuid, String playerName, ModpackDetector.ModpackInfo modpack, JsonObject fingerprint, boolean creativeAtJoin) {
            this.storeFile = storeFile;
            this.kind = kind;
            this.createdAt = createdAt;
            this.cheatsAllowed = cheatsAllowed;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.modpack = modpack;
            this.fingerprint = fingerprint;
            this.creativeAtJoin = creativeAtJoin;
        }

        public Path storeFile() {
            return storeFile;
        }

        public String kind() {
            return kind;
        }

        public String createdAt() {
            return createdAt;
        }

        public boolean cheatsAllowed() {
            return cheatsAllowed;
        }

        public String playerUuid() {
            return playerUuid;
        }

        public String playerName() {
            return playerName;
        }

        public ModpackDetector.ModpackInfo modpack() {
            return modpack;
        }

        public JsonObject fingerprint() {
            return fingerprint;
        }

        public boolean creativeAtJoin() {
            return creativeAtJoin;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WorldContext)) return false;
            WorldContext other = (WorldContext) o;
            return java.util.Objects.equals(storeFile, other.storeFile) && java.util.Objects.equals(kind, other.kind) && java.util.Objects.equals(createdAt, other.createdAt) && java.util.Objects.equals(cheatsAllowed, other.cheatsAllowed) && java.util.Objects.equals(playerUuid, other.playerUuid) && java.util.Objects.equals(playerName, other.playerName) && java.util.Objects.equals(modpack, other.modpack) && java.util.Objects.equals(fingerprint, other.fingerprint) && java.util.Objects.equals(creativeAtJoin, other.creativeAtJoin);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(storeFile, kind, createdAt, cheatsAllowed, playerUuid, playerName, modpack, fingerprint, creativeAtJoin);
        }

        @Override
        public String toString() {
            return "WorldContext[" + "storeFile=" + storeFile + ", kind=" + kind + ", createdAt=" + createdAt + ", cheatsAllowed=" + cheatsAllowed + ", playerUuid=" + playerUuid + ", playerName=" + playerName + ", modpack=" + modpack + ", fingerprint=" + fingerprint + ", creativeAtJoin=" + creativeAtJoin + "]";
        }


        WorldContext withCheatsAllowed() {
            return new WorldContext(storeFile, kind, createdAt, true, playerUuid, playerName, modpack, fingerprint, creativeAtJoin);
        }

    }

    /** État du joueur à chaque tick (#47, #48). */
    public static final class PlayerState {
        private final String gameMode;
        private final boolean invulnerable;

        public PlayerState(String gameMode, boolean invulnerable) {
            this.gameMode = gameMode;
            this.invulnerable = invulnerable;
        }

        public String gameMode() {
            return gameMode;
        }

        public boolean invulnerable() {
            return invulnerable;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlayerState)) return false;
            PlayerState other = (PlayerState) o;
            return java.util.Objects.equals(gameMode, other.gameMode) && java.util.Objects.equals(invulnerable, other.invulnerable);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(gameMode, invulnerable);
        }

        @Override
        public String toString() {
            return "PlayerState[" + "gameMode=" + gameMode + ", invulnerable=" + invulnerable + "]";
        }
    }

    /**
     * Un progrès du modpack tel que le jeu l'affiche (#80) : titre et description dans la langue
     * du joueur, cadre ({@code task}, {@code goal}, {@code challenge}), objet de l'icône. Ce sont
     * ces progrès qui deviennent les succès XPack ; on ne rédige rien nous-mêmes.
     */
    public static final class AdvancementInfo {
        private final String id;
        private final String parent;
        private final String title;
        private final String description;
        private final String frame;
        private final String icon;
        private final boolean hidden;
        /** Onglet où le jeu le range (1.7.10 : page d'achievements du mod), ou null : son arbre. */
        private final String tab;
        /** Position du succès sur la page du jeu (1.7.10 : displayColumn / displayRow), ou null. */
        private final Integer column;
        private final Integer row;

        public AdvancementInfo(String id, String parent, String title, String description, String frame, String icon, boolean hidden) {
            this(id, parent, title, description, frame, icon, hidden, null);
        }

        public AdvancementInfo(String id, String parent, String title, String description, String frame, String icon, boolean hidden, String tab) {
            this(id, parent, title, description, frame, icon, hidden, tab, null, null);
        }

        public AdvancementInfo(String id, String parent, String title, String description, String frame, String icon, boolean hidden, String tab, Integer column, Integer row) {
            this.column = column;
            this.row = row;
            this.id = id;
            this.parent = parent;
            this.title = title;
            this.description = description;
            this.frame = frame;
            this.icon = icon;
            this.hidden = hidden;
            this.tab = tab;
        }

        public String tab() {
            return tab;
        }

        public Integer column() {
            return column;
        }

        public Integer row() {
            return row;
        }

        /** Le même succès avec sa position sur la page du jeu. */
        public AdvancementInfo withPosition(int column, int row) {
            return new AdvancementInfo(id, parent, title, description, frame, icon, hidden, tab, column, row);
        }

        public String id() {
            return id;
        }

        public String parent() {
            return parent;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        public String frame() {
            return frame;
        }

        public String icon() {
            return icon;
        }

        public boolean hidden() {
            return hidden;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AdvancementInfo)) return false;
            AdvancementInfo other = (AdvancementInfo) o;
            return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(parent, other.parent) && java.util.Objects.equals(title, other.title) && java.util.Objects.equals(description, other.description) && java.util.Objects.equals(frame, other.frame) && java.util.Objects.equals(icon, other.icon) && java.util.Objects.equals(hidden, other.hidden) && java.util.Objects.equals(tab, other.tab) && java.util.Objects.equals(column, other.column) && java.util.Objects.equals(row, other.row);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, parent, title, description, frame, icon, hidden, tab, column, row);
        }

        @Override
        public String toString() {
            return "AdvancementInfo[" + "id=" + id + ", parent=" + parent + ", title=" + title + ", description=" + description + ", frame=" + frame + ", icon=" + icon + ", hidden=" + hidden + ", tab=" + tab + ", column=" + column + ", row=" + row + "]";
        }
    }

    static final long FLUSH_INTERVAL_MS = 5_000;
    static final long MAX_BACKOFF_MS = 300_000;
    static final long LINK_RETRY_MS = 30_000;
    static final int MAX_BATCH = 200;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Config config;
    private final Path configFile;
    private final Credentials credentials;
    private final Backend api;
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
    /**
     * Le serveur a-t-il reconnu le modpack ? Lu dans la réponse d'ouverture de session
     * ({@code modpack.recognized}, booléen facultatif) ; {@code null} tant que le serveur ne le dit pas
     * (ancien serveur, hors ligne) : le mod n'affiche alors rien.
     */
    private Boolean modpackRecognized;
    private boolean sessionPending;
    private int nextSeq;
    private List<String> allowedCommands = J8.listOf();
    private Set<String> trackedStats = J8.setOf();
    private boolean uuidMismatch;
    /** Jeton du compte Minecraft qui joue ce monde (lu dans {@link Credentials}). */
    private String token;
    private boolean practice;

    // Réseau
    private boolean inFlight;
    /** Un progrès ou une quête attend : envoi au tick suivant, pas à la fin de l'intervalle (#210). */
    private boolean flushSoon;
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
    private List<String> loadedMods = J8.listOf();
    private boolean consentShown;

    /** Commande confirmée par le joueur après l'alerte : laissée passer une fois. */
    private String bypassCommand;

    public XPackCore(Config config, Path configFile, Credentials credentials, Backend api, Ui ui, Executor mainThread, Clock clock,
            IntegrityRules rules) {
        this.config = config;
        this.configFile = configFile;
        this.credentials = credentials;
        this.api = api;
        this.ui = ui;
        this.mainThread = mainThread;
        this.clock = clock;
        this.rules = rules;
        // API d'événements locale : l'option est relue dans la configuration à chaque événement (donc après chaque sauvegarde).
        XPackEvents.bind(() -> this.config.eventsApi);
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
        loadedMods = J8.copyOf(modIds);
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

    // ------------------------------------------------------------------ serveur local (développement)

    /** Une API XPack tourne sur ce PC (Docker local) : les réglages proposent de s'en servir. */
    private volatile boolean localServerFound;

    /** Client vers {@link Config#LOCAL_API} ; la détection part au premier tick (le jeu n'existe pas encore à l'init sous NeoForge). */
    private Backend localProbe;
    private boolean localProbed;

    public void setLocalProbe(Backend probe) {
        this.localProbe = probe;
    }

    /** Nouvelle recherche du serveur local (ouverture des réglages : Docker a pu démarrer entre-temps). */
    public void refreshLocalServer() {
        if (localProbe != null) detectLocalServer(localProbe);
    }

    public boolean localServerFound() {
        return localServerFound;
    }

    /**
     * Au lancement : cherche une API XPack sur ce PC ({@code probe} vise {@link Config#LOCAL_API}).
     * Rien n'est jamais choisi d'office : le joueur bascule lui-même dans les réglages. Si le mod
     * est resté sur le serveur local et qu'il ne répond pas, on le dit sans rebasculer en ligne :
     * des parties de test ne doivent pas partir sur le vrai site. Les succès attendent dans le
     * fichier du monde que Docker redémarre.
     */
    public void detectLocalServer(Backend probe) {
        probe.health().thenAcceptAsync(res -> {
            boolean was = localServerFound;
            localServerFound = res.ok() && res.body().has("service") && "xpack".equals(text(res.body().get("service")));
            if (!localServerFound && config.usesLocalServer() && (was || !localProbedOnce)) ui.notify("xpack.server.local_gone");
            localProbedOnce = true;
        }, mainThread);
    }

    private boolean localProbedOnce;

    /** Changement de serveur : seulement hors d'un monde (la session en cours appartient à l'autre). */
    public boolean canSwitchServer() {
        return world == null;
    }

    public void useLocalServer(boolean local) {
        if (!canSwitchServer() || local == config.usesLocalServer() || (local && !localServerFound)) return;
        cancelLinking(); // un code de liaison vaut pour un seul serveur
        config.useLocalServer(local);
        config.save(configFile);
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

    /** Identifiant du monde ouvert (propre à XPack, stable d'une session à l'autre), ou null hors d'un monde. */
    public String worldId() {
        return worldId;
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
        XPackEvents.dispatch(l -> l.onWorldTracking(new XPackEvents.WorldTracking(false, "practice")));
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
        // Serveur de développement : le rappeler à chaque monde, pour ne pas croire que rien ne compte en ligne.
        if (config.usesLocalServer()) ui.notify("xpack.server.local_active");
        cheatsNoticeShown = store.cheatsNoticeShown;
        startedLate = store.startedLate;
        ranked = store.ranked;
        rankedMayStart = store.fresh && store.ranked == null && ctx.fingerprint() != null && ctx.fingerprint().has("playtimeClock")
                && "world".equals(ctx.fingerprint().get("playtimeClock").getAsString()); // solo : l'âge du monde est connu tout de suite
        lateNoticeShown = store.lateNoticeShown;
        difficultyWarned = store.difficultyWarned;
        teamReminderDay = store.teamReminderDay;
        playtimeFloor = store.playtime;
        questsSavedAt = store.questsSavedAt;
        questsLeftAt = store.leftAt;
        // Le temps de jeu du monde précédent ne doit pas servir de plancher à celui-ci.
        currentPlaytime = 0;
        sentPlaytime = 0;
        // Monde de test : déjà déclaré, ou monde que le mod découvre alors qu'il est en créatif.
        // Un monde déjà suivi qui passe ensuite en créatif reste suivi, puis signalé.
        practice = store.practice || (store.fresh && ctx.creativeAtJoin());
        if (practice) {
            if (!store.practice) ui.notify("xpack.practice.auto");
            saveWorld();
            XPackEvents.dispatch(l -> l.onWorldTracking(new XPackEvents.WorldTracking(false, "practice")));
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
            announceJoin();
            return;
        }
        if (active()) startTracking();
        announceJoin();
    }

    /** Entrée dans un monde non « de test » : suivi réel (consentement, envoi activé, monde non signalé), ou signalé déjà. */
    private void announceJoin() {
        boolean flagged = integrity.isFlagged();
        boolean tracked = tracking() && !flagged;
        XPackEvents.dispatch(l -> l.onWorldTracking(new XPackEvents.WorldTracking(tracked, flagged ? "flagged" : "joined")));
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

    // ---- Speedrun classé (#225)
    private RankedRun ranked;
    /** Première observation du temps de jeu pas encore faite dans un monde neuf, solo : la run peut encore démarrer. */
    private boolean rankedMayStart;

    private boolean cheatsNoticeShown;
    /** Monde commencé sans le mod (#116), d'après le serveur : il ne compte pas. */
    private boolean startedLate;
    private boolean lateNoticeShown;
    /** Avertissement « badge de difficulté » déjà montré dans ce monde : une seule fois. */
    private boolean difficultyWarned;
    /** Dernière difficulté envoyée pendant cette partie. */
    private String lastDifficulty;
    /** Un autre joueur est connecté (#119) : les progrès et statistiques partent avec « team ». */
    private boolean othersOnline;

    /**
     * Triches activées en cours de partie (#74) : « Ouvrir au LAN » avec les commandes, ou le
     * joueur nommé opérateur. Vérifié régulièrement, pas seulement à l'entrée dans le monde.
     */
    public void onCheatsAllowed(boolean allowed) {
        if (world == null || !allowed || world.cheatsAllowed()) return;
        world = world.withCheatsAllowed();
        invalidateRanked("cheats");
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
        XPackEvents.dispatch(l -> l.onWorldTracking(new XPackEvents.WorldTracking(false, "left")));
        flushNow();
        leftAt = clock.millis(); // fin de partie suivie : les quêtes finies juste avant comptent (onQuests)
        saveWorld();
        flushWorldFile(); // avant que le monde suivant ne remplace l'écriture en attente
        world = null;
        worldId = null;
        invulnerableSince = -1;
        sessionId = null;
        modpackRecognized = null;
        sessionPending = false;
        pending.clear();
        knownAdvancements.clear();
        lastStats.clear();
        itemLog.reset();
        mining.reset();
        cheatModeAsked.clear();
        lastMining = null;
        lastHand = null;
        lastPacks = null;
        integrity.reset();
        uuidMismatch = false;
        token = null;
        practice = false;
        cheatsNoticeShown = false;
        startedLate = false;
        ranked = null;
        rankedMayStart = false;
        freshSpawn = false;
        lateNoticeShown = false;
        difficultyWarned = false;
        lastDifficulty = null;
        othersOnline = false;
        otherPlayers = J8.mapOf();
        presenceStatuses = J8.mapOf();
        presenceAt = -1;
        nextPresenceAt = 0;
        afterPresence.clear();
        teamState = null;
        teamError = null;
        allowedCommands = J8.listOf();
        trackedStats = J8.setOf();
        catalog = null;
        advancementCatalog = J8.listOf();
        questCatalog = J8.listOf();
        catalogSentHash = null;
        catalogInFlight = false;
        nextCatalogAt = 0;
        iconQueue.clear();
        iconGapsAsked = false;
        iconGapsInFlight = false;
        nextIconGapsAt = 0;
        questsSynced = false;
        questsSavedAt = 0;
        questsLeftAt = 0;
        leftAt = 0;
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
            modpackRecognized = parseModpackRecognized(b);
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

    /** {@code modpack.recognized} de la réponse de session : {@code null} si absent ou pas un booléen. */
    static Boolean parseModpackRecognized(JsonObject body) {
        if (body == null || !body.has("modpack") || !body.get("modpack").isJsonObject()) return null;
        JsonElement r = body.getAsJsonObject("modpack").get("recognized");
        return r != null && r.isJsonPrimitive() && r.getAsJsonPrimitive().isBoolean() ? r.getAsBoolean() : null;
    }

    /** Réponse du serveur sur le modpack de ce monde : vrai (reconnu), faux (non reconnu) ou null (inconnu : rien à afficher). */
    public Boolean modpackRecognized() {
        return modpackRecognized;
    }

    private void applyRules(JsonObject r) {
        if (r == null) return;
        JsonObject integ = r.has("integrity") && r.get("integrity").isJsonObject() ? r.getAsJsonObject("integrity") : new JsonObject();
        allowedCommands = IntegrityRules.strings(integ.getAsJsonArray("allowedCommands"));
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
            if (!initialSync) rankedAdvancement(id, playtimeTicks);
            // Succès obtenu en jouant : le toast dépend de la réponse du serveur, on n'attend pas les 5 s
            // de l'envoi groupé (#210). Sauf pendant un repli après une erreur réseau.
            if (!initialSync && backoffMs == FLUSH_INTERVAL_MS) {
                flushSoon = true;
                nextFlushAt = 0;
            }
        }
    }

    public void onStats(Map<String, Integer> stats, long playtimeTicks) {
        if (world == null || practice) return;
        miningEvent(stats, playtimeTicks);
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

    static final int MAX_CATALOG = 10_000;
    /** Taille d'un envoi du catalogue (caractères JSON) : sous la limite de 1 Mo de l'API. */
    static final int CATALOG_PART_CHARS = 700_000;
    static final long CATALOG_RETRY_MS = 60_000;
    private List<AdvancementInfo> advancementCatalog = J8.listOf();
    private List<AdvancementInfo> questCatalog = J8.listOf();
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
        advancementCatalog = advancements.stream().filter(a -> !a.id().contains(":recipes/")).collect(java.util.stream.Collectors.toList());
        catalogLang = lang;
        updateCatalog();
    }

    /** Livre de quêtes du modpack (BetterQuesting), envoyé avec les progrès : chaque quête est un succès. */
    public void onQuestCatalog(List<AdvancementInfo> quests, String lang) {
        if (world == null || practice) return;
        questCatalog = J8.copyOf(quests);
        catalogLang = lang;
        updateCatalog();
    }

    /**
     * Le livre de quêtes BetterQuesting compte pour cette partie, quel que soit le nombre de progrès
     * (04/11/2026 : les quêtes ont leur jauge « Avancement » sur le site, à part des succès ; le
     * seuil de 300 progrès, qui écartait GT: New Horizons, n'a plus lieu d'être).
     */
    public boolean wantsQuests() {
        return world != null && !practice;
    }

    private void updateCatalog() {
        List<AdvancementInfo> all = new ArrayList<>(advancementCatalog);
        all.addAll(questCatalog);
        if (all.isEmpty() || catalogLang == null) return;
        catalog = all.size() > MAX_CATALOG ? new ArrayList<>(all.subList(0, MAX_CATALOG)) : all;
        catalogHash = hashOf(catalog, catalogLang);
        sendCatalog();
    }

    private static String hashOf(List<AdvancementInfo> catalog, String lang) {
        StringBuilder sb = new StringBuilder(lang).append('|');
        catalog.stream().map(AdvancementInfo::id).sorted().forEach(id -> sb.append(id).append(','));
        return shortHash(sb.toString());
    }

    private void sendCatalog() {
        if (catalog == null || catalogInFlight || catalogHash.equals(catalogSentHash) || sessionId == null || token == null || !tracking()) return;
        // Un gros livre de quêtes (GT: New Horizons : ~3 800 quêtes) part en plusieurs envois.
        List<JsonArray> parts = new ArrayList<>();
        JsonArray part = new JsonArray();
        int size = 0;
        int order = 0;
        for (AdvancementInfo a : catalog) {
            JsonObject o = new JsonObject();
            o.addProperty("id", a.id());
            // Rang dans la liste du jeu : le site range les onglets (pages 1.7.10, chapitres) dans cet ordre.
            o.addProperty("order", order++);
            if (a.parent() != null) o.addProperty("parent", a.parent());
            o.addProperty("title", a.title());
            if (a.description() != null && !J8.isBlank(a.description())) o.addProperty("description", a.description());
            o.addProperty("frame", a.frame());
            if (a.icon() != null) o.addProperty("icon", a.icon());
            if (a.hidden()) o.addProperty("hidden", true);
            if (a.tab() != null && !J8.isBlank(a.tab())) o.addProperty("tab", a.tab());
            if (a.column() != null && a.row() != null) {
                o.addProperty("col", a.column());
                o.addProperty("row", a.row());
            }
            int length = o.toString().length() + 1;
            if (size > 0 && size + length > CATALOG_PART_CHARS) {
                parts.add(part);
                part = new JsonArray();
                size = 0;
            }
            part.add(o);
            size += length;
        }
        parts.add(part);
        catalogInFlight = true;
        sendCatalogPart(parts, 0, catalogHash, worldId);
    }

    private void sendCatalogPart(List<JsonArray> parts, int index, String hash, String wid) {
        JsonObject body = new JsonObject();
        body.addProperty("lang", catalogLang);
        body.add("advancements", parts.get(index));
        api.postCatalog(sessionId, body, token).thenAcceptAsync(res -> {
            if (!wid.equals(worldId) || sessionId == null) {
                catalogInFlight = false;
                return;
            }
            boolean ok = res.ok() || res.status() == 422; // 422 : inutile de renvoyer le même
            if (ok && index + 1 < parts.size() && hash.equals(catalogHash)) {
                sendCatalogPart(parts, index + 1, hash, wid);
                return;
            }
            catalogInFlight = false;
            if (ok && index + 1 == parts.size()) catalogSentHash = hash;
            else if (!ok) nextCatalogAt = clock.millis() + CATALOG_RETRY_MS;
            // Catalogue changé pendant l'envoi : le nouveau part au prochain tick.
        }, mainThread);
    }

    // ------------------------------------------------------------------ icônes d'objets (#189)

    /** Au plus ce nombre d'objets demandés par partie (un pack n'en a que quelques dizaines à fournir). */
    static final int MAX_ICON_REQUESTS = 400;
    static final long ICON_RETRY_MS = 60_000;
    private final java.util.ArrayDeque<String> iconQueue = new java.util.ArrayDeque<>();
    private boolean iconGapsAsked;
    private boolean iconGapsInFlight;
    private long nextIconGapsAt;

    /** Prochain objet dont le serveur attend l'icône (identifiant « mod:objet »), ou null. Fil du jeu. */
    public String nextIconToDraw() {
        return iconQueue.poll();
    }

    /** Envoie l'icône dessinée (PNG). Le serveur refuse tout objet qu'il n'a pas demandé : rien à gérer ici. */
    public void submitIcon(String item, byte[] png) {
        if (sessionId == null || token == null || png == null || png.length == 0) return;
        api.postIcon(sessionId, item, java.util.Base64.getEncoder().encodeToString(png), token);
    }

    /**
     * Une fois le catalogue du modpack reçu par le serveur, demande les objets dont l'import n'a pas pu
     * lire la texture. Une seule demande par partie ; en cas d'échec, une nouvelle plus tard.
     */
    private void tickIcons(long t) {
        if (iconGapsAsked || iconGapsInFlight || t < nextIconGapsAt || sessionId == null || token == null) return;
        if (catalogHash == null || !catalogHash.equals(catalogSentHash)) return;
        iconGapsInFlight = true;
        String wid = worldId;
        api.iconGaps(sessionId, token).thenAcceptAsync(res -> {
            iconGapsInFlight = false;
            if (!wid.equals(worldId)) return;
            if (!res.ok()) {
                nextIconGapsAt = clock.millis() + ICON_RETRY_MS;
                return;
            }
            iconGapsAsked = true;
            JsonArray items = res.body().has("items") && res.body().get("items").isJsonArray() ? res.body().getAsJsonArray("items") : new JsonArray();
            for (int i = 0; i < items.size() && iconQueue.size() < MAX_ICON_REQUESTS; i++) {
                if (items.get(i).isJsonPrimitive()) iconQueue.add(items.get(i).getAsString());
            }
        }, mainThread);
    }

    // ------------------------------------------------------------------ quêtes BetterQuesting

    /** Le premier relevé des quêtes de la partie est passé (les suivants sont des quêtes du moment). */
    private boolean questsSynced;
    /** Date du fichier de progression lu en dernier pendant une partie suivie (ms). */
    private long questsSavedAt;
    /** Sortie du monde à la partie précédente (ms, 0 : inconnue). */
    private long questsLeftAt;
    /** Sortie de ce monde (ms) : écrit à la sortie pour la partie suivante. */
    private long leftAt;

    /**
     * Quêtes terminées par le joueur (identifiant → date de fin en ms, 0 si inconnue), lues dans le
     * fichier que BetterQuesting écrit à chaque sauvegarde ({@code savedAt} : sa date). Le premier
     * relevé est un instantané, comme le paquet initial des progrès ; une quête finie à la fin de la
     * partie précédente, après la dernière sauvegarde lue mais avant la sortie, compte quand même.
     */
    public void onQuests(Map<String, Long> completed, long savedAt, long playtimeTicks) {
        if (!wantsQuests()) return;
        if (!questsSynced) {
            questsSynced = true;
            List<String> snapshot = new ArrayList<>();
            List<String> tail = new ArrayList<>();
            for (Map.Entry<String, Long> e : completed.entrySet()) {
                long at = e.getValue() == null ? 0 : e.getValue();
                boolean lastSession = questsSavedAt > 0 && questsLeftAt > 0 && at > questsSavedAt && at <= questsLeftAt;
                (lastSession ? tail : snapshot).add(e.getKey());
            }
            if (!snapshot.isEmpty()) onAdvancements(snapshot, true, playtimeTicks);
            if (!tail.isEmpty()) onAdvancements(tail, false, playtimeTicks);
        } else {
            onAdvancements(completed.keySet(), false, playtimeTicks);
        }
        if (savedAt != questsSavedAt) {
            questsSavedAt = savedAt;
            saveWorld();
        }
    }

    public Set<String> trackedStats() {
        return J8.copyOfSet(trackedStats);
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
        if (joinsTeam(command)) remindTeam();
        if (!tracking() || token == null || integrity.isFlagged()) return true;
        if (rules.classify(command, allowedCommands) != IntegrityRules.Verdict.CHEAT) return true;
        ui.showCheatWarning(command, () -> {
            flag(WorldIntegrity.Reason.COMMAND, "/" + stripSlash(command));
            bypassCommand = command;
            resend.accept(command);
        });
        return false;
    }

    /** Après « Désactiver », délai avant de reposer la question si le mode est resté allumé. */
    static final long CHEAT_MODE_RECHECK_MS = 5_000;
    /** Mode triche → moment où l'on peut reposer la question (Long.MAX_VALUE : écran ouvert). */
    private final Map<String, Long> cheatModeAsked = new HashMap<>();

    /**
     * Modes triche actifs (#49) : JEI/REI/EMI donnent des objets, le mode édition de FTB Quests
     * valide quêtes et récompenses. Sans droits de triche ils ne donnent rien : contrôlés seulement
     * quand le monde les autorise. Comme pour une commande (#44), on prévient avant : désactiver,
     * ou garder et le monde est signalé. Une question à la fois.
     */
    public void onCheatModes(Set<String> active, Consumer<String> disable) {
        cheatModeAsked.keySet().retainAll(active); // éteint : la prochaine activation repose la question
        if (!tracking() || integrity.isFlagged() || !world.cheatsAllowed() || cheatModeAsked.containsValue(Long.MAX_VALUE)) return;
        long t = clock.millis();
        for (String mode : new java.util.TreeSet<>(active)) {
            Long until = cheatModeAsked.get(mode);
            if (until != null && t < until) continue;
            cheatModeAsked.put(mode, Long.MAX_VALUE);
            ui.showCheatModeWarning(mode, () -> {
                cheatModeAsked.put(mode, clock.millis() + CHEAT_MODE_RECHECK_MS);
                disable.accept(mode);
            }, () -> flag(WorldIntegrity.Reason.CHEAT_MODE, mode));
            return;
        }
    }

    /**
     * Invulnérabilité en survie tolérée tant qu'elle ne dure pas plus d'une minute de jeu d'affilée :
     * des mods la donnent un instant à l'apparition et entre les dimensions (AngerMod dans GT:NH,
     * 10 s par défaut, jusqu'au premier pas ou coup).
     */
    static final long INVULNERABLE_GRACE_TICKS = 60 * 20;
    /** Début de l'invulnérabilité en cours (temps de jeu), -1 sans invulnérabilité. */
    private long invulnerableSince = -1;

    public void onPlayerState(PlayerState s, long playtimeTicks) {
        if (!s.invulnerable() || playtimeTicks < invulnerableSince) invulnerableSince = -1;
        else if (invulnerableSince < 0) invulnerableSince = playtimeTicks;
        if (!tracking() || integrity.isFlagged()) return;
        switch (s.gameMode()) {
            case "creative":
                flagWithNotice(WorldIntegrity.Reason.CREATIVE, null, "xpack.flagged.creative");
                break;
            case "spectator":
                flagWithNotice(WorldIntegrity.Reason.SPECTATOR, null, "xpack.flagged.spectator");
                break;
            default:
                // Le vol en survie n'est pas signalé (#48) : jetpacks, anneaux, sorts, /home et /back… trop de façons légitimes.
                // Certains modpacks (GT: New Horizons) donnent ou font gagner l'invulnérabilité : jamais signalée.
                if (rules.ignoresInvulnerability(loadedMods)) break;
                if (invulnerableSince >= 0 && playtimeTicks - invulnerableSince >= INVULNERABLE_GRACE_TICKS) flagWithNotice(WorldIntegrity.Reason.ABILITIES, "invulnerable", "xpack.flagged.godmode");
        }
    }

    // ------------------------------------------------------------------ objets reçus (#128)

    private final ItemArrivals itemLog = ItemArrivals.bundled();

    /** Objets des récompenses de quêtes du modpack : une arrivée de l'un d'eux est expliquée. */
    public void setQuestRewards(Set<String> items) {
        itemLog.setQuestRewards(items);
    }

    /** Le serveur annonce que le joueur ramasse un objet par terre. */
    public void onItemPickup(String item, int count) {
        if (tracking()) itemLog.onPickup(item, count);
    }

    /** Nouveau joueur côté client (réapparition, changement de dimension) : l'inventaire repart de zéro. */
    public void resetInventory() {
        itemLog.reset();
    }

    /**
     * Relevé de l'inventaire, chaque seconde : les objets de valeur arrivés partent avec leur
     * provenance, pour une analyse après coup sur le serveur. Rien n'est signalé ici.
     */
    public void onInventory(Map<String, Integer> inventory, ItemArrivals.Context ctx, long playtimeTicks) {
        if (!tracking()) {
            itemLog.reset();
            return;
        }
        for (ItemArrivals.Arrival a : itemLog.observe(inventory, ctx, clock.millis())) {
            JsonObject e = event("item", playtimeTicks);
            e.addProperty("key", a.item());
            e.addProperty("value", a.count());
            e.addProperty("via", a.via().name().toLowerCase(java.util.Locale.ROOT));
            if (a.screen() != null) e.addProperty("screen", a.screen());
            if (a.nearPlayer()) e.addProperty("nearPlayer", true);
            if (a.quest()) e.addProperty("quest", true);
            enqueue(e);
        }
    }

    // ------------------------------------------------------------------ x-ray (#129)

    private final Mining mining = Mining.bundled();
    private Mining.Summary lastMining;
    private Map<String, Integer> lastHand;
    private List<String> lastPacks;

    /** Mods du modpack qui minent plusieurs blocs d'un coup ou à la place du joueur (empreinte de session). */
    public List<String> miningHelpers() {
        return mining.helpers(loadedMods);
    }

    /** Bloc cassé par le joueur lui-même, avec l'objet tenu. */
    public void onBlockBroken(String blockId, String toolId) {
        if (tracking()) mining.onBlockBroken(blockId, toolId);
    }

    /** Packs de textures choisis par le joueur : envoyés à l'entrée puis à chaque changement. */
    public void onResourcePacks(List<String> packs, long playtimeTicks) {
        if (!tracking()) return;
        List<String> sorted = packs.stream().sorted().collect(java.util.stream.Collectors.toList());
        if (sorted.equals(lastPacks)) return;
        lastPacks = sorted;
        JsonObject e = event("resource_packs", playtimeTicks);
        JsonArray list = new JsonArray();
        sorted.forEach(item -> list.add(new com.google.gson.JsonPrimitive(item)));
        e.add("packs", list);
        enqueue(e);
    }

    /**
     * Minerais et roche minés (statistiques), minerais cassés à la main et outils de la session :
     * envoyés avec les statistiques quand ils changent. Analysés après coup, jamais en direct.
     */
    private void miningEvent(Map<String, Integer> stats, long playtimeTicks) {
        if (!tracking()) return;
        Mining.Summary s = mining.summarize(stats);
        Map<String, Integer> hand = mining.hand();
        if (s.equals(lastMining) && hand.equals(lastHand)) return;
        lastMining = s;
        lastHand = hand;
        if (s.ores().isEmpty() && s.stone() == 0 && hand.isEmpty()) return;
        JsonObject e = event("mining", playtimeTicks);
        e.add("ores", counts(s.ores()));
        e.addProperty("stone", s.stone());
        if (!hand.isEmpty()) e.add("hand", counts(hand));
        JsonArray tools = new JsonArray();
        mining.tools().stream().sorted().forEach(item -> tools.add(new com.google.gson.JsonPrimitive(item)));
        if (tools.size() > 0) e.add("tools", tools);
        enqueue(e);
    }

    private static JsonObject counts(Map<String, Integer> map) {
        JsonObject o = new JsonObject();
        new java.util.TreeMap<>(map).forEach(o::addProperty);
        return o;
    }

    /** Du plus facile au plus dur, comme le serveur (#116). */
    static final List<String> DIFFICULTIES = J8.listOf("peaceful", "easy", "normal", "hard", "hardcore");

    /**
     * Difficulté du monde (#116), relevée avec l'état du joueur : envoyée à l'entrée puis à chaque
     * changement. Elle donne au serveur le badge de difficulté des succès. Quand elle baisse, on
     * prévient une seule fois par monde, sans rien bloquer : quelques minutes plus bas ne changent
     * rien, mais le badge baisse si le monde y reste. {@code canChange} : le joueur peut la remettre
     * (solo, difficulté non verrouillée).
     */
    public void onDifficulty(String difficulty, boolean canChange, long playtimeTicks) {
        if (!tracking() || !DIFFICULTIES.contains(difficulty) || difficulty.equals(lastDifficulty)) return;
        String previous = lastDifficulty;
        lastDifficulty = difficulty;
        JsonObject e = event("difficulty", playtimeTicks);
        e.addProperty("key", difficulty);
        enqueue(e);
        if (previous != null && DIFFICULTIES.indexOf(difficulty) < DIFFICULTIES.indexOf(previous) && !difficultyWarned && !startedLate) {
            difficultyWarned = true;
            saveWorld();
            ui.difficultyWarning(previous, difficulty, canChange);
        }
    }

    /**
     * Au moins un autre joueur connecté (serveur, ou monde ouvert au LAN) : les événements le disent
     * (#119). Le serveur ne compte « en équipe » que si un coéquipier XPack était là (#145).
     */
    public void setOthersOnline(boolean others) {
        othersOnline = others;
    }

    // ------------------------------------------------------------------ jeu en groupe (#126)

    static final long PRESENCE_INTERVAL_MS = 60_000;
    /** Un mod sans compte ne peut pas renvoyer sa présence plus souvent (le serveur répond 429). */
    static final long PRESENCE_FRESH_MS = 30_000;
    /** Le serveur n'en garde pas plus. */
    static final int MAX_OTHERS = 100;
    /**
     * Commandes qui font rejoindre une équipe (FTB Teams, équipes de plugins, vanilla) : les progrès
     * deviennent souvent partagés, d'où le rappel. Rien n'est bloqué.
     */
    private static final List<String> TEAM_JOIN = J8.listOf("ftbteams party join", "ftbteams party accept", "team join", "teams join", "party join",
            "party accept");

    /** Autres joueurs de la liste (Tab) : UUID → pseudo. */
    private Map<String, String> otherPlayers = J8.mapOf();
    private long nextPresenceAt;
    private boolean presenceInFlight;
    /** Dernière réponse du serveur (empreinte → statut) et son heure, pour {@code /xpack check}. */
    private Map<String, Presence.Status> presenceStatuses = J8.mapOf();
    private long presenceAt = -1;
    /** Jour (UTC) de la dernière proposition « crée ton équipe XPack » dans ce monde (#145). */
    private String teamReminderDay;
    /** Équipe XPack pour le modpack de ce monde, telle que le serveur l'a renvoyée ; null tant qu'inconnue. */
    private Team.State teamState;
    /** Dernière erreur d'une action de l'écran Équipe (clé de traduction), ou null. */
    private String teamError;
    /** Invitations déjà annoncées dans le chat, pour ne pas les répéter à chaque minute. */
    private final Set<String> invitesShown = new HashSet<>();
    private final List<Runnable> afterPresence = new ArrayList<>();

    /** Autres joueurs connectés, lus régulièrement dans la liste du client. */
    public void setOtherPlayers(Map<String, String> players) {
        otherPlayers = J8.copyOf(players);
        setOthersOnline(!players.isEmpty());
    }

    /** Rejoindre une équipe d'un autre mod (FTB Teams…) : elle ne compte pas pour XPack, on le rappelle à chaque fois. */
    private void remindTeam() {
        if (world == null || practice || !active()) return;
        ui.teamNotice("xpack.team.reminder");
    }

    /**
     * Proposer l'équipe XPack (#145) : d'autres joueurs avec le mod lié sont là et on n'a pas d'équipe.
     * Une fois par jour et par monde. Sans équipe, tout est compté en solo.
     */
    private void suggestTeam() {
        if (token == null || teamState == null || teamState.inTeam() || !presenceStatuses.containsValue(Presence.Status.LINKED)) return;
        String today = java.time.LocalDate.now(clock).toString();
        if (today.equals(teamReminderDay)) return;
        teamReminderDay = today;
        saveWorld();
        ui.teamNotice("xpack.team.suggest");
    }

    /** Nouvel état de l'équipe : on annonce une fois chaque invitation reçue. */
    private void applyTeam(JsonObject body) {
        teamState = Team.parse(body);
        for (Team.Invite i : teamState.invites()) {
            if (invitesShown.add(i.id())) ui.teamNotice("xpack.team.invited", i.from());
        }
    }

    private static boolean joinsTeam(String command) {
        String c = stripSlash(command.trim()).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        return TEAM_JOIN.stream().anyMatch(c::startsWith);
    }

    private void tickPresence(long t) {
        if (world == null || practice || !active() || otherPlayers.isEmpty() || presenceInFlight || t < nextPresenceAt) return;
        sendPresence(t);
    }

    private void sendPresence(long t) {
        nextPresenceAt = t + PRESENCE_INTERVAL_MS;
        presenceInFlight = true;
        JsonObject body = new JsonObject();
        JsonArray others = new JsonArray();
        otherPlayers.keySet().stream().limit(MAX_OTHERS).map(Presence::hash).forEach(item -> others.add(new com.google.gson.JsonPrimitive(item)));
        body.add("others", others);
        // Compte lié : le serveur connaît déjà l'UUID du joueur, il ne le demande pas. Avec la session,
        // il renvoie aussi l'équipe XPack du modpack (#145).
        if (token == null) body.addProperty("uuidHash", Presence.hash(world.playerUuid()));
        else if (sessionId != null) body.addProperty("sessionId", sessionId);
        String wid = worldId;
        api.presence(body, token).thenAcceptAsync(res -> {
            presenceInFlight = false;
            if (res.ok() && res.body() != null && res.body().has("players") && wid.equals(worldId)) {
                Map<String, Presence.Status> statuses = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : res.body().getAsJsonObject("players").entrySet()) {
                    statuses.put(e.getKey(), Presence.parse(e.getValue().isJsonNull() ? null : e.getValue().getAsString()));
                }
                presenceStatuses = statuses;
                presenceAt = t;
                if (res.body().has("team") && res.body().get("team").isJsonObject()) applyTeam(res.body().getAsJsonObject("team"));
                suggestTeam();
            }
            List<Runnable> waiting = new ArrayList<>(afterPresence);
            afterPresence.clear();
            waiting.forEach(Runnable::run);
        }, mainThread);
    }

    /** Premiers succès du jeu, dans l'ordre de préférence pour le toast d'essai. */
    private static final List<String> FIRST_ADVANCEMENTS = J8.listOf("minecraft:story/root", "achievement:openInventory");

    /**
     * Toast d'essai ({@code /xpack toast}, bouton « Essai » des réglages) : le premier succès du
     * modpack s'il est connu, sinon {@code fallbackName}. Affiché même si les toasts sont coupés ;
     * rien n'est envoyé au serveur.
     */
    public void previewToast(String fallbackName) {
        AdvancementInfo first = null;
        if (catalog != null) {
            for (String id : FIRST_ADVANCEMENTS) {
                for (AdvancementInfo a : catalog) if (first == null && a.id().equals(id)) first = a;
            }
            for (AdvancementInfo a : catalog) if (first == null && a.parent() == null) first = a;
        }
        String name = first != null ? first.title() : fallbackName;
        String item = first != null && !"minecraft:air".equals(first.icon()) ? first.icon() : null;
        Ui.Unlock unlock = new Ui.Unlock(name, 5, "commun", item, 87.5, "normal");
        // Avec le délai des réglages : l'essai montre ce que verra le joueur.
        later(() -> ui.toastUnlocked(unlock));
    }

    /** {@code /xpack check} : qui, parmi les joueurs connectés, a le mod XPack (lié ou non). */
    public void checkPresence() {
        if (world == null) {
            ui.notify("xpack.check.no_world");
            return;
        }
        if (practice || !active()) {
            ui.notify("xpack.check.inactive");
            return;
        }
        if (otherPlayers.isEmpty()) {
            // Seul : on dit au moins si son propre compte est lié.
            ui.notify(token != null ? "xpack.check.alone" : "xpack.check.alone_unlinked");
            return;
        }
        long t = clock.millis();
        if (presenceAt >= 0 && t - presenceAt < PRESENCE_FRESH_MS) {
            reportPresence();
            return;
        }
        afterPresence.add(this::reportPresence);
        if (!presenceInFlight) sendPresence(t);
    }

    private void reportPresence() {
        if (presenceAt < 0) {
            ui.notify("xpack.check.offline");
            return;
        }
        List<Presence.Player> players = new ArrayList<>();
        otherPlayers.forEach((uuid, name) -> players.add(new Presence.Player(name, presenceStatuses.getOrDefault(Presence.hash(uuid), Presence.Status.UNKNOWN))));
        players.sort(java.util.Comparator.comparing(Presence.Player::name, String.CASE_INSENSITIVE_ORDER));
        ui.presenceReport(token != null, players);
    }

    // ------------------------------------------------------------------ équipe XPack (#145)

    /** L'écran Équipe peut agir : monde suivi, compte lié et session ouverte. */
    public boolean teamsAvailable() {
        return world != null && !practice && active() && token != null && sessionId != null;
    }

    public boolean isLinkedHere() {
        return token != null;
    }

    /** Équipe connue (null tant que le serveur n'a pas répondu). */
    public Team.State team() {
        return teamState;
    }

    public String teamError() {
        return teamError;
    }

    /** Joueurs du serveur pour l'écran Équipe, triés par pseudo. */
    public List<Team.Candidate> teamCandidates() {
        List<Team.Candidate> out = new ArrayList<>();
        otherPlayers.forEach((uuid, name) -> {
            String hash = Presence.hash(uuid);
            boolean mate = teamState != null && teamState.isMember(hash);
            boolean invited = teamState != null && teamState.invited().contains(hash);
            out.add(new Team.Candidate(name, hash, presenceStatuses.getOrDefault(hash, Presence.Status.UNKNOWN), mate, invited));
        });
        out.sort(java.util.Comparator.comparing(Team.Candidate::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Ouverture de l'écran Équipe : relevé de présence tout de suite (il rapporte l'équipe). */
    public void refreshTeam() {
        teamError = null;
        if (!teamsAvailable() || presenceInFlight) return;
        sendPresence(clock.millis());
    }

    public void inviteToTeam(String uuidHash) {
        if (!teamsAvailable()) return;
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        body.addProperty("uuidHash", uuidHash);
        teamAction(api.teamInvite(body, token));
    }

    /** Répondre à une invitation, dans son propre jeu. */
    public void answerTeamInvite(String inviteId, boolean accept) {
        if (!teamsAvailable()) return;
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        teamAction(api.teamAnswer(inviteId, accept, body, token));
    }

    public void leaveTeam() {
        if (!teamsAvailable() || teamState == null || !teamState.inTeam()) return;
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        teamAction(api.teamLeave(teamState.teamId(), body, token));
    }

    private void teamAction(java.util.concurrent.CompletableFuture<ApiClient.Response> call) {
        teamError = null;
        String wid = worldId;
        call.thenAcceptAsync(res -> {
            if (!wid.equals(worldId)) return;
            if (res.ok() && res.body() != null) {
                applyTeam(res.body());
                return;
            }
            String code = res.body() != null && res.body().has("error") ? res.body().get("error").getAsString() : "";
            switch (code) {
                case "not_on_server":
                case "already_teammate":
                case "team_full":
                case "too_many_invites":
                case "invite_expired":
                case "unknown_invite":
                    teamError = "xpack.team.error." + code;
                    break;
                default:
                    teamError = "xpack.team.error.offline";
            }
        }, mainThread);
    }

    /** Verdict du serveur sur le monde (#116) : commencé sans le mod, il ne compte pas, et on le dit une fois. */
    private void syncWorld(JsonObject w) {
        if (!w.has("startedWithoutMod") || !w.get("startedWithoutMod").getAsBoolean() || startedLate) return;
        startedLate = true;
        if (!lateNoticeShown) {
            lateNoticeShown = true;
            ui.notify("xpack.started_late");
        }
        saveWorld();
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
            r.add("fingerprint", world.fingerprint() == null ? new JsonObject() : J8.copy(world.fingerprint()));
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
        loadedMods.stream().sorted().forEach(item -> mods.add(new com.google.gson.JsonPrimitive(item)));
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
        body.addProperty("message", message == null ? "" : message.trim());
        body.add("report", supportReport());
        api.supportReport(body, token).thenAcceptAsync(res -> done.accept(res.ok()), mainThread);
    }

    private boolean flag(WorldIntegrity.Reason reason, String detail) {
        String at = now();
        boolean first = integrity.flag(reason, at, detail);
        invalidateRanked("flagged");
        JsonObject e = event("integrity", currentPlaytime);
        e.addProperty("reason", reason.wire);
        if (detail != null) e.addProperty("detail", detail);
        enqueue(e);
        saveWorld();
        if (first) XPackEvents.dispatch(l -> l.onWorldTracking(new XPackEvents.WorldTracking(false, "flagged")));
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
        maybeStartRanked(ticks);
    }

    // ------------------------------------------------------------------ speedrun classé (#225)

    /**
     * Première observation du temps de jeu dans un monde neuf (solo) : si le monde vient d'être créé, la run démarre
     * à cet instant. Un monde de test, signalé, avec triches activées ou commencé sans le mod ne démarre rien.
     */
    private void maybeStartRanked(long worldAgeTicks) {
        if (!rankedMayStart || world == null) return;
        rankedMayStart = false; // une seule chance : la première observation
        if (!config.speedrunEnabled) return; // option du joueur : un monde ouvert sans elle n'aura jamais de run
        if (RankedRun.isFresh(worldAgeTicks)) freshSpawn = true; // le chrono libre peut aussi démarrer ici (SpeedrunMode)
        if (practice || startedLate || integrity.isFlagged() || world.cheatsAllowed()) return;
        if (!SpeedrunMode.parse(config.speedrunMode).ranked()) return; // départ ou fin à la touche : chrono libre, jamais classé
        RankedRun run = RankedRun.start(worldAgeTicks, clock.millis());
        if (run == null) return;
        ranked = run;
        JsonObject e = event("speedrun_start", worldAgeTicks);
        e.addProperty("category", RankedRun.CATEGORY);
        e.addProperty("igtTicks", 0);
        enqueue(e);
        ui.notify("xpack.speedrun.ranked.started");
    }

    /** Un progrès obtenu en jouant : étape du parcours. */
    private void rankedAdvancement(String id, long playtimeTicks) {
        if (ranked == null) return;
        sendRanked(ranked.onAdvancement(id, playtimeTicks, clock.millis()), playtimeTicks);
    }

    /** Début des crédits (poème final) : fin de la run, comme sur speedrun.com. À appeler une fois, à l'ouverture de l'écran. */
    public void onCredits(long playtimeTicks) {
        if (world == null || ranked == null) return;
        sendRanked(ranked.onCredits(playtimeTicks, clock.millis()), playtimeTicks);
    }

    private void sendRanked(RankedRun.Outcome o, long playtimeTicks) {
        if (o == null) return;
        JsonObject e = event(o.kind == RankedRun.Kind.END ? "speedrun_end" : "speedrun_split", playtimeTicks);
        e.addProperty("category", RankedRun.CATEGORY);
        if (o.kind == RankedRun.Kind.SPLIT) e.addProperty("split", o.advancement);
        e.addProperty("igtTicks", o.igtTicks);
        e.addProperty("rtaMs", o.rtaMs);
        enqueue(e);
        if (o.kind == RankedRun.Kind.END) {
            ui.notify("xpack.speedrun.ranked.finished");
            flushSoon = true;
            nextFlushAt = 0;
        }
    }

    /** Monde signalé ou triches activées : la run ne compte plus (le serveur le sait par les événements d'intégrité). */
    private void invalidateRanked(String reason) {
        if (ranked == null || !ranked.invalidate(reason, currentPlaytime, clock.millis())) return;
        saveWorld();
        ui.notify("xpack.speedrun.ranked.invalid");
    }

    private boolean freshSpawn;

    /** Vrai une fois, au premier tick d'un monde neuf en solo : le moment du départ automatique. */
    public boolean takeFreshSpawn() {
        boolean was = freshSpawn;
        freshSpawn = false;
        return was;
    }

    /** Run classée du monde ouvert, ou null (pour l'affichage). */
    /** Client de l'API, pour les lectures sans état (catégories de speedrun, #222). */
    public Backend api() {
        return api;
    }

    public RankedRun rankedRun() {
        return ranked;
    }

    public long rankedIgtTicks() {
        return ranked == null ? 0 : ranked.igtTicks(currentPlaytime);
    }

    public long rankedRtaMs() {
        return ranked == null ? 0 : ranked.rtaMs(clock.millis());
    }

    /**
     * Multijoueur (#74) : le temps du joueur n'arrive qu'avec ses statistiques, quelques secondes
     * après la connexion. D'ici là, on repart du dernier temps envoyé pour qu'il ne recule pas.
     */
    public long playtimeFloor() {
        return Math.max(playtimeFloor, currentPlaytime);
    }

    /**
     * Plus grand temps de jeu envoyé pour ce monde pendant cette session. En solo, l'horloge du client
     * prend de l'avance pendant un chargement lourd puis le serveur la recale en arrière : sans ce
     * plancher, le serveur y verrait un recul du temps de jeu.
     */
    private long sentPlaytime;

    private JsonObject event(String type, long playtimeTicks) {
        long ticks = Math.max(sentPlaytime, playtimeTicks);
        sentPlaytime = ticks;
        currentPlaytime = Math.max(currentPlaytime, ticks);
        JsonObject e = new JsonObject();
        e.addProperty("id", "e-" + UUID.randomUUID());
        e.addProperty("type", type);
        e.addProperty("at", now());
        e.addProperty("playtimeTicks", ticks);
        // Partie à plusieurs (#119) : seulement ce booléen, ni pseudos ni nombre de joueurs.
        if (othersOnline && (type.equals("advancement") || type.equals("stat"))) e.addProperty("team", true);
        return e;
    }

    private void enqueue(JsonObject e) {
        pending.add(e);
        saveWorld();
    }

    /** À appeler à chaque tick client : liaison du compte et envoi périodique. */
    public void tick() {
        long t = clock.millis();
        if (!localProbed && localProbe != null) {
            localProbed = true;
            detectLocalServer(localProbe);
        }
        tickDelayedToasts(t);
        tickLinking(t);
        tickPresence(t);
        if (!tracking() || token == null) return;
        if (sessionId == null && !sessionPending && t >= nextFlushAt) {
            nextFlushAt = t + backoffMs;
            openSession();
        }
        if (t >= nextFlushAt) flushNow();
        if (catalog != null && !catalogHash.equals(catalogSentHash) && t >= nextCatalogAt) sendCatalog();
        tickIcons(t);
    }

    private void flushNow() {
        if (sessionId == null || inFlight || pending.isEmpty() || token == null) return;
        List<JsonObject> batch = new ArrayList<>(pending.subList(0, Math.min(MAX_BATCH, pending.size())));
        JsonArray events = new JsonArray();
        for (JsonObject e : batch) {
            JsonObject copy = J8.copy(e);
            copy.addProperty("seq", nextSeq++);
            events.add(copy);
        }
        JsonObject body = new JsonObject();
        body.add("events", events);
        inFlight = true;
        flushSoon = false; // ce lot les emporte ; un succès arrivé pendant l'envoi le remet
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
                nextFlushAt = flushSoon ? t : t + FLUSH_INTERVAL_MS;
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
        if (b.has("world") && b.get("world").isJsonObject()) syncWorld(b.getAsJsonObject("world"));
        if (!b.has("unlocked")) return;
        // Les toasts peuvent être coupés ; l'API d'événements locale (désactivée par défaut) part quand même, tout de suite.
        boolean toasts = config.toasts;
        for (JsonElement el : b.getAsJsonArray("unlocked")) {
            JsonObject u = el.getAsJsonObject();
            // Objet, part des joueurs et difficulté : absents d'un ancien serveur.
            String rarity = text(u.get("rarity"));
            Ui.Unlock unlock = new Ui.Unlock(u.get("name").getAsString(), u.get("points").getAsInt(), rarity != null ? rarity : "commun", text(u.get("item")),
                    u.has("percent") && u.get("percent").isJsonPrimitive() ? u.get("percent").getAsDouble() : -1, text(u.get("difficulty")),
                    // Quête : points d'avancement (serveur du 04/11/2026 ; absent avant).
                    u.has("quest") && u.get("quest").isJsonPrimitive() && u.get("quest").getAsBoolean());
            XPackEvents.dispatch(l -> l.onAchievementUnlocked(new XPackEvents.AchievementUnlocked(unlock.name, unlock.points, unlock.rarity, unlock.item,
                    unlock.percent, unlock.difficulty, unlock.quest)));
            if (toasts) later(() -> ui.toastUnlocked(unlock));
        }
        // Paliers du modpack (#60) : toast spécial, après ceux des succès du lot. 100 % dans « completed »,
        // 25/50/75 % dans « milestone ».
        String name = world != null ? world.modpack().name() : null;
        if (b.has("completed") && b.get("completed").isJsonObject()) {
            JsonObject c = b.getAsJsonObject("completed");
            int doneCount = c.has("achievements") ? c.get("achievements").getAsInt() : 0;
            long donePlaytime = c.has("playtimeTicks") ? c.get("playtimeTicks").getAsLong() : 0;
            XPackEvents.dispatch(l -> l.onModpackCompleted(new XPackEvents.ModpackCompleted(name, doneCount, donePlaytime)));
            if (toasts) later(() -> ui.toastCompleted(name, doneCount, donePlaytime));
        } else if (b.has("milestone") && b.get("milestone").isJsonObject()) {
            JsonObject m = b.getAsJsonObject("milestone");
            if (m.has("tier")) {
                int tier = m.get("tier").getAsInt();
                int count = m.has("achievements") ? m.get("achievements").getAsInt() : 0;
                int total = m.has("total") ? m.get("total").getAsInt() : 0;
                XPackEvents.dispatch(l -> l.onMilestone(new XPackEvents.Milestone(name, tier, count, total)));
                if (toasts) later(() -> ui.toastMilestone(name, tier, count, total));
            }
        }
    }

    /** Toasts de succès en attente du délai choisi par le joueur : heure d'affichage, puis l'affichage. */
    private final java.util.ArrayDeque<Map.Entry<Long, Runnable>> delayedToasts = new java.util.ArrayDeque<>();

    /** Affiche après le délai des réglages (tout de suite sans délai), dans l'ordre d'arrivée. */
    private void later(Runnable toast) {
        long delay = config.toastDelayMs();
        if (delay == 0 && delayedToasts.isEmpty()) toast.run();
        else delayedToasts.add(new java.util.AbstractMap.SimpleImmutableEntry<>(clock.millis() + delay, toast));
    }

    private void tickDelayedToasts(long t) {
        while (!delayedToasts.isEmpty() && delayedToasts.peek().getKey() <= t) delayedToasts.poll().getValue().run();
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
        if (startedLate) return Status.STARTED_LATE;
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
        s.startedLate = startedLate;
        s.lateNoticeShown = lateNoticeShown;
        s.difficultyWarned = difficultyWarned;
        s.teamReminderDay = teamReminderDay;
        s.practice = practice;
        s.playtime = playtimeFloor();
        s.questsSavedAt = questsSavedAt;
        s.leftAt = leftAt;
        s.ranked = ranked;
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
        /** Monde commencé sans le mod (#116) et message déjà montré. */
        boolean startedLate;
        boolean lateNoticeShown;
        /** Avertissement de baisse de difficulté déjà montré (#116). */
        boolean difficultyWarned;
        /** Jour (UTC) du dernier rappel « tout le groupe doit avoir le mod » (#126). */
        String teamReminderDay;
        /** Monde de test : rien n'est envoyé (voir {@link XPackCore#markWorldPractice()}). */
        boolean practice;
        /** Dernier temps de jeu connu (ticks), plancher du temps de jeu en multijoueur (#74). */
        long playtime;
        /** Quêtes BetterQuesting : date du fichier de progression lu en dernier pendant la partie (ms). */
        long questsSavedAt;
        /** Sortie du monde (ms) ; 0 si la partie s'est mal terminée (plantage). */
        long leftAt;
        /** Run de speedrun classée de ce monde (#225), ou null. */
        RankedRun ranked;
        List<JsonObject> pending = new ArrayList<>();
        /** Premier passage du mod dans ce monde (aucun fichier). Non sauvegardé. */
        transient boolean fresh;

        static WorldStore load(Path file) {
            WorldStore s = null;
            try {
                if (Files.isRegularFile(file)) s = GSON.fromJson(J8.readString(file, StandardCharsets.UTF_8), WorldStore.class);
            } catch (IOException | RuntimeException e) {
                XLog.warn("état du monde illisible, état neuf", e);
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
            if (latest.getAndSet(J8.entry(file, json)) == null) executor.execute(this::flush);
        }

        /** Synchronisé : une écriture plus ancienne ne peut pas passer après une plus récente. */
        synchronized void flush() {
            Map.Entry<Path, String> w = latest.getAndSet(null);
            if (w == null) return;
            try {
                Files.createDirectories(w.getKey().getParent());
                J8.writeString(w.getKey(), w.getValue(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                XLog.warn("état du monde non enregistré", e);
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
            return J8.hex(d, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
