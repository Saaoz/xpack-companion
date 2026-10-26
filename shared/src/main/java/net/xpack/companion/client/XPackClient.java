package net.xpack.companion.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.xpack.companion.core.Diagnostics;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.xpack.companion.core.ApiClient;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.Credentials;
import net.xpack.companion.core.IntegrityRules;
import net.xpack.companion.core.ModpackDetector;
import net.xpack.companion.core.XPackCore;

/**
 * Pont entre Minecraft (vanilla, mappings Mojang) et le cœur du mod. Identique pour Fabric et
 * NeoForge : les loaders n'appellent que {@link #init}, les mixins appellent les autres méthodes.
 */
public final class XPackClient {
    /** Fréquence de demande des statistiques au serveur (#36). */
    private static final int STATS_INTERVAL_TICKS = 20 * 60;
    /** Vérification du mode de jeu et des capacités (#47, #48). */
    private static final int STATE_INTERVAL_TICKS = 10;

    private static XPackCore core;
    private static Path configDir;
    private static Path gameDir;
    private static String loader;
    private static List<String> modIds = List.of();
    private static String modVersion;
    private static boolean inWorld;
    private static int ticks;

    private XPackClient() {}

    public static void init(Path configDirectory, Path gameDirectory, String loaderName, List<String> loadedModIds, String version) {
        configDir = configDirectory;
        gameDir = gameDirectory;
        loader = loaderName;
        modIds = List.copyOf(loadedModIds);
        modVersion = version;
        Path configFile = configDir.resolve("xpack.json");
        Config config = Config.load(configFile);
        config.save(configFile); // écrit les valeurs par défaut au premier lancement
        ApiClient api = new ApiClient(ApiClient.http(config.apiUrl, "XPackCompanion/" + version + " (" + loaderName + ")"));
        // Jetons hors de l'instance (dossier de l'utilisateur) : config/ part avec les exports de modpack.
        Credentials credentials = new Credentials(Credentials.defaultLocation());
        // Minecraft.getInstance() n'existe pas encore sous NeoForge au moment de l'init : résolution différée.
        core = new XPackCore(config, configFile, credentials, api, new MinecraftUi(), r -> Minecraft.getInstance().execute(r), Clock.systemUTC(),
                IntegrityRules.bundled());
        core.setLoadedMods(modIds);
    }

    public static XPackCore core() {
        return core;
    }

    // ------------------------------------------------------------------ alerte de triche → support

    /** Fin de la fenêtre pendant laquelle la touche du support est active (durée du toast). */
    private static long alertUntil;

    static void alertShown() {
        alertUntil = System.currentTimeMillis() + IntegrityToast.DISPLAY_MS;
    }

    private static InputConstants.Key supportKey() {
        try {
            return InputConstants.getKey(core.config().supportKey);
        } catch (RuntimeException e) {
            return InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_K);
        }
    }

    static String supportKeyName() {
        return core == null ? "K" : supportKey().getDisplayName().getString();
    }

    /** Pendant l'alerte, et seulement en jeu (aucun écran ouvert) : la touche ouvre le support. */
    private static void checkSupportKey(Minecraft mc) {
        if (alertUntil == 0 || System.currentTimeMillis() > alertUntil || mc.screen != null) return;
        InputConstants.Key key = supportKey();
        if (key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue())) {
            alertUntil = 0;
            mc.setScreen(new SupportScreen(null));
        }
    }

    // ------------------------------------------------------------------ commandes locales /xpack

    /**
     * Commandes du mod, traitées dans le client et jamais envoyées au serveur (aucune API de
     * commandes nécessaire, identique sur Fabric et NeoForge). Renvoie vrai si la commande est à nous.
     */
    public static boolean handleLocalCommand(String command) {
        String c = command.strip();
        if (core == null || !(c.equalsIgnoreCase("xpack") || c.toLowerCase(Locale.ROOT).startsWith("xpack "))) return false;
        String sub = c.length() > 5 ? c.substring(5).strip().toLowerCase(Locale.ROOT) : "";
        Minecraft mc = Minecraft.getInstance();
        switch (sub) {
            case "diagnostic", "diag" -> runDiagnostic(mc);
            // Différé : le chat se ferme juste après l'envoi de la commande.
            case "support" -> mc.tell(() -> mc.setScreen(new SupportScreen(null)));
            case "settings", "reglages", "réglages" -> mc.tell(() -> mc.setScreen(new SettingsScreen(null)));
            default -> XPackCommands.help(); // /xpack, /xpack help, ou commande inconnue
        }
        return true;
    }

    /** /xpack diagnostic : fichiers du lanceur (anonymisés) + modpack détecté, dans un dossier à envoyer. */
    private static void runDiagnostic(Minecraft mc) {
        ModpackDetector.ModpackInfo detected = ModpackDetector.detect(gameDir, modIds);
        String summary = "XPack Companion " + modVersion + " (" + loader + "), Minecraft " + SharedConstants.getCurrentVersion().getName()
                + "\nModpack détecté : " + Diagnostics.describe(detected) + "\nMods chargés : " + modIds.size();
        try {
            Diagnostics.Result r = Diagnostics.write(gameDir, gameDir.resolve("xpack-diagnostic"), summary, modIds, Clock.systemUTC());
            String files = r.found().isEmpty() ? "-" : String.join(", ", r.found());
            MutableComponent open = Texts.tr("xpack.diagnostic.open").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, r.folder().toAbsolutePath().toString())));
            MinecraftUi.say(Texts.tr("xpack.diagnostic.done", Diagnostics.describe(detected), files).append(" ").append(open));
        } catch (java.io.IOException e) {
            MinecraftUi.say(Texts.tr("xpack.diagnostic.failed", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ tick client

    public static void onClientTick(Minecraft mc) {
        if (core == null) return;
        boolean nowInWorld = mc.level != null && mc.player != null && mc.getConnection() != null;
        if (nowInWorld && !inWorld) join(mc);
        if (!nowInWorld && inWorld) {
            ticks = 0;
            core.onLeaveWorld();
            SmokeTest.log("left world");
        }
        inWorld = nowInWorld;
        SmokeTest.tick(mc, inWorld);
        checkSupportKey(mc);
        XPackToasts.tick(mc);
        XPackSounds.tick(mc);

        if (inWorld) {
            long playtime = mc.level.getGameTime();
            core.setPlaytime(playtime);
            ticks++;
            if (ticks % STATE_INTERVAL_TICKS == 0 && mc.gameMode != null) {
                var abilities = mc.player.getAbilities();
                core.onPlayerState(new XPackCore.PlayerState(mc.gameMode.getPlayerMode().getName(), abilities.invulnerable, abilities.mayfly), playtime);
            }
            // Première demande 5 s après l'entrée dans le monde, puis toutes les 60 s.
            if ((ticks == 100 || ticks % STATS_INTERVAL_TICKS == 0) && !core.trackedStats().isEmpty()) {
                mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            }
        }
        core.tick();
    }

    private static void join(Minecraft mc) {
        XPackCore.WorldContext ctx = worldContext(mc);
        SmokeTest.log("joined " + ctx.kind() + " world, modpack=" + ctx.modpack());
        core.onJoinWorld(ctx);
        refreshCatalog(mc);
    }

    private static XPackCore.WorldContext worldContext(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        Path storeFile;
        String kind;
        boolean cheatsAllowed;
        GameType mode = mc.gameMode != null ? mc.gameMode.getPlayerMode() : GameType.SURVIVAL;
        boolean creative = mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
        if (server != null) {
            storeFile = server.getWorldPath(LevelResource.ROOT).resolve("xpack.json");
            kind = "singleplayer";
            cheatsAllowed = Compat.allowCommands(server);
            // Monde créé en créatif : un monde de test, même si le joueur est repassé en survie.
            creative |= server.getWorldData().getGameType() == GameType.CREATIVE;
        } else {
            ServerData data = mc.getCurrentServer();
            String address = data != null ? data.ip : "inconnu";
            storeFile = configDir.resolve("xpack").resolve("servers").resolve(sha256(address.toLowerCase()).substring(0, 24) + ".json");
            kind = "multiplayer";
            cheatsAllowed = mc.player.hasPermissions(2);
        }
        JsonObject fingerprint = new JsonObject();
        fingerprint.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        fingerprint.addProperty("loader", loader);
        fingerprint.addProperty("modsHash", ModpackDetector.modsHash(modIds));
        fingerprint.addProperty("modCount", modIds.size());
        fingerprint.addProperty("modVersion", modVersion);
        return new XPackCore.WorldContext(storeFile, kind, Instant.now().toString(), cheatsAllowed, mc.player.getUUID().toString(),
                mc.player.getGameProfile().getName(), ModpackDetector.detect(gameDir, modIds), fingerprint, creative);
    }

    // ------------------------------------------------------------------ crochets des mixins

    /** Progrès terminés reçus du serveur (#35). */
    public static void onAdvancements(List<String> doneIds, boolean initialSync) {
        Minecraft mc = Minecraft.getInstance();
        if (core == null || mc.level == null) return;
        if (!inWorld) {
            // Le paquet initial arrive avant le premier tick « en jeu » : on ouvre la partie tout de suite.
            join(mc);
            inWorld = true;
        }
        SmokeTest.log("advancements done=" + doneIds + " initial=" + initialSync);
        core.onAdvancements(doneIds, initialSync, mc.level.getGameTime());
    }

    /** Catalogue des progrès du modpack (#80), relu à l'entrée dans le monde et à chaque mise à jour des progrès. */
    public static void refreshCatalog(Minecraft mc) {
        if (core == null || mc.level == null) return;
        String lang = mc.options == null ? "en_us" : mc.options.languageCode;
        core.onAdvancementCatalog(Compat.advancementCatalog(mc), lang);
    }

    /** Statistiques reçues après notre demande (#36). */
    public static void onStats(Map<Stat<?>, Integer> stats) {
        Minecraft mc = Minecraft.getInstance();
        if (core == null || mc.level == null) return;
        Map<String, Integer> out = new HashMap<>();
        stats.forEach((stat, value) -> {
            String key = statKey(stat);
            if (key != null) out.put(key, value);
        });
        SmokeTest.log("stats received=" + out.size());
        core.onStats(out, mc.level.getGameTime());
    }

    /** Même format que le site et le serveur : {@code minecraft:mined/minecraft:diamond_ore}. */
    static <T> String statKey(Stat<T> stat) {
        StatType<T> type = stat.getType();
        ResourceLocation typeId = BuiltInRegistries.STAT_TYPE.getKey(type);
        ResourceLocation valueId = type.getRegistry().getKey(stat.getValue());
        return typeId == null || valueId == null ? null : typeId + "/" + valueId;
    }

    /** Avant l'envoi d'une commande (#44). Renvoie {@code false} si elle doit être retenue. */
    public static boolean beforeCommand(String command, Consumer<String> resend) {
        boolean allowed = core == null || core.onCommand(command, resend);
        SmokeTest.log("command '" + command + "' allowed=" + allowed);
        return allowed;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
