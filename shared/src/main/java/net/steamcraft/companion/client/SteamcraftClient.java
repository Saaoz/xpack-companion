package net.steamcraft.companion.client;

import com.google.gson.JsonObject;
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
import net.minecraft.world.level.storage.LevelResource;
import net.steamcraft.companion.core.ApiClient;
import net.steamcraft.companion.core.Config;
import net.steamcraft.companion.core.IntegrityRules;
import net.steamcraft.companion.core.ModpackDetector;
import net.steamcraft.companion.core.SteamcraftCore;

/**
 * Pont entre Minecraft (vanilla, mappings Mojang) et le cœur du mod. Identique pour Fabric et
 * NeoForge : les loaders n'appellent que {@link #init}, les mixins appellent les autres méthodes.
 */
public final class SteamcraftClient {
    /** Fréquence de demande des statistiques au serveur (#36). */
    private static final int STATS_INTERVAL_TICKS = 20 * 60;
    /** Vérification du mode de jeu et des capacités (#47, #48). */
    private static final int STATE_INTERVAL_TICKS = 10;

    private static SteamcraftCore core;
    private static Path configDir;
    private static Path gameDir;
    private static String loader;
    private static List<String> modIds = List.of();
    private static String modVersion;
    private static boolean inWorld;
    private static int ticks;

    private SteamcraftClient() {}

    public static void init(Path configDirectory, Path gameDirectory, String loaderName, List<String> loadedModIds, String version) {
        configDir = configDirectory;
        gameDir = gameDirectory;
        loader = loaderName;
        modIds = List.copyOf(loadedModIds);
        modVersion = version;
        Path configFile = configDir.resolve("steamcraft.json");
        Config config = Config.load(configFile);
        config.save(configFile); // écrit les valeurs par défaut au premier lancement
        ApiClient api = new ApiClient(ApiClient.http(config.apiUrl, "SteamcraftCompanion/" + version + " (" + loaderName + ")"));
        // Minecraft.getInstance() n'existe pas encore sous NeoForge au moment de l'init : résolution différée.
        core = new SteamcraftCore(config, configFile, api, new MinecraftUi(), r -> Minecraft.getInstance().execute(r), Clock.systemUTC(),
                IntegrityRules.bundled());
    }

    public static SteamcraftCore core() {
        return core;
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

        if (inWorld) {
            long playtime = mc.level.getGameTime();
            core.setPlaytime(playtime);
            ticks++;
            if (ticks % STATE_INTERVAL_TICKS == 0 && mc.gameMode != null) {
                var abilities = mc.player.getAbilities();
                core.onPlayerState(new SteamcraftCore.PlayerState(mc.gameMode.getPlayerMode().getName(), abilities.invulnerable, abilities.mayfly), playtime);
            }
            // Première demande 5 s après l'entrée dans le monde, puis toutes les 60 s.
            if ((ticks == 100 || ticks % STATS_INTERVAL_TICKS == 0) && !core.trackedStats().isEmpty()) {
                mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            }
        }
        core.tick();
    }

    private static void join(Minecraft mc) {
        SteamcraftCore.WorldContext ctx = worldContext(mc);
        SmokeTest.log("joined " + ctx.kind() + " world, modpack=" + ctx.modpack());
        core.onJoinWorld(ctx);
    }

    private static SteamcraftCore.WorldContext worldContext(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        Path storeFile;
        String kind;
        boolean cheatsAllowed;
        if (server != null) {
            storeFile = server.getWorldPath(LevelResource.ROOT).resolve("steamcraft.json");
            kind = "singleplayer";
            cheatsAllowed = server.getWorldData().isAllowCommands();
        } else {
            ServerData data = mc.getCurrentServer();
            String address = data != null ? data.ip : "inconnu";
            storeFile = configDir.resolve("steamcraft").resolve("servers").resolve(sha256(address.toLowerCase()).substring(0, 24) + ".json");
            kind = "multiplayer";
            cheatsAllowed = mc.player.hasPermissions(2);
        }
        JsonObject fingerprint = new JsonObject();
        fingerprint.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        fingerprint.addProperty("loader", loader);
        fingerprint.addProperty("modsHash", ModpackDetector.modsHash(modIds));
        fingerprint.addProperty("modCount", modIds.size());
        fingerprint.addProperty("modVersion", modVersion);
        return new SteamcraftCore.WorldContext(storeFile, kind, Instant.now().toString(), cheatsAllowed, mc.player.getUUID().toString(),
                mc.player.getGameProfile().getName(), ModpackDetector.detect(gameDir, modIds), fingerprint);
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
