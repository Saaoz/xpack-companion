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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
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
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.xpack.companion.core.IntegrityRules;
import net.xpack.companion.core.ItemArrivals;
import net.xpack.companion.core.ModpackDetector;
import net.xpack.companion.core.QuestRewards;
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
    /** Relevé de l'inventaire pour le journal des objets reçus (#128). */
    private static final int ITEMS_INTERVAL_TICKS = 20;

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
        // Fichier du monde écrit hors du thread de jeu (#74), et une dernière fois à la fermeture.
        ExecutorService disk = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "XPack disk");
            t.setDaemon(true);
            return t;
        });
        core.writeWorldFilesWith(disk);
        Runtime.getRuntime().addShutdownHook(new Thread(core::flushWorldFile, "XPack flush"));
    }

    public static XPackCore core() {
        return core;
    }

    // ------------------------------------------------------------------ alerte de triche → support

    /** Fin de la fenêtre pendant laquelle la touche du support est active (durée du toast). */
    private static long alertUntil;

    /** Appelé après le dessin des toasts du jeu (mixin) : les nôtres, s'ils sont dans un autre coin. */
    public static void renderPlacedToasts(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.components.toasts.ToastComponent toasts) {
        XPackToasts.render(graphics, toasts);
    }

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
            case "check", "verifier", "vérifier" -> core.checkPresence();
            case "team", "equipe", "équipe" -> mc.tell(() -> mc.setScreen(new TeamScreen(null)));
            // Différé : le chat se ferme juste après l'envoi de la commande.
            case "support" -> mc.tell(() -> mc.setScreen(new SupportScreen(null)));
            case "settings", "reglages", "réglages" -> mc.tell(() -> mc.setScreen(new SettingsScreen(null)));
            case "toast", "essai", "test" -> core.previewToast(Texts.tr("xpack.toast.preview").getString());
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
            ticksSinceStats++;
            long playtime = playtime(mc);
            core.setPlaytime(playtime);
            ticks++;
            if (ticks % STATE_INTERVAL_TICKS == 0 && mc.gameMode != null) {
                var abilities = mc.player.getAbilities();
                core.onPlayerState(new XPackCore.PlayerState(mc.gameMode.getPlayerMode().getName(), abilities.invulnerable), playtime);
                core.onCheatsAllowed(cheatsAllowed(mc));
                // Jouer à plusieurs (#119, #126) : les autres joueurs de la liste du client (Tab).
                java.util.Map<String, String> others = new java.util.HashMap<>();
                for (var info : mc.getConnection().getOnlinePlayers()) {
                    var profile = info.getProfile();
                    if (!profile.getId().equals(mc.player.getUUID())) others.put(profile.getId().toString(), profile.getName());
                }
                core.setOtherPlayers(others);
                // Badge de difficulté (#116) : en solo comme en multijoueur, le client connaît la difficulté.
                var data = mc.level.getLevelData();
                String difficulty = data.isHardcore() ? "hardcore" : mc.level.getDifficulty().getKey();
                core.onDifficulty(difficulty, mc.getSingleplayerServer() != null && !data.isDifficultyLocked() && !data.isHardcore(), playtime);
                core.onResourcePacks(resourcePacks(mc), playtime);
                // Mode triche de JEI/REI/EMI, mode édition de FTB Quests (#49).
                core.onCheatModes(CheatModes.active(modIds), CheatModes::disable);
            }
            if (mc.player != lastPlayer) {
                // Réapparition ou changement de dimension : nouveau joueur, inventaire renvoyé par le serveur.
                lastPlayer = mc.player;
                core.resetInventory();
            }
            if (ticks % ITEMS_INTERVAL_TICKS == 0) observeInventory(mc, playtime);
            // Première demande 5 s après l'entrée dans le monde, puis toutes les 60 s.
            if ((ticks == 100 || ticks % STATS_INTERVAL_TICKS == 0) && !core.trackedStats().isEmpty()) {
                mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            }
        }
        core.tick();
    }

    // ------------------------------------------------------------------ objets reçus (#128)

    private static net.minecraft.client.player.LocalPlayer lastPlayer;
    /** Récompenses des quêtes de l'instance : lues une fois par lancement. */
    private static java.util.Set<String> questRewards;

    /** Inventaire complet (armure, seconde main) et objet tenu au curseur, par identifiant d'objet. */
    private static void observeInventory(Minecraft mc, long playtime) {
        Map<String, Integer> counts = new HashMap<>();
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) count(counts, inventory.getItem(i));
        count(counts, mc.player.containerMenu.getCarried());
        boolean nearPlayer = mc.level.players().stream().anyMatch(p -> p != mc.player && p.distanceToSqr(mc.player) < 64);
        core.onInventory(counts, new ItemArrivals.Context(screenId(mc.screen), nearPlayer), playtime);
    }

    private static void count(Map<String, Integer> counts, ItemStack stack) {
        if (!stack.isEmpty()) counts.merge(itemId(stack), stack.getCount(), Integer::sum);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Écran ouvert à l'arrivée d'un objet : type de menu (coffre, four…), chat, ou nom de l'écran (quêtes d'un mod). */
    static String screenId(Screen screen) {
        if (screen == null) return null;
        if (screen instanceof ChatScreen) return "chat";
        if (screen instanceof AbstractContainerScreen<?> container) {
            AbstractContainerMenu menu = container.getMenu();
            if (menu instanceof InventoryMenu) return "inventory";
            try {
                ResourceLocation type = BuiltInRegistries.MENU.getKey(menu.getType());
                if (type != null) return type.toString();
            } catch (UnsupportedOperationException e) {
                // Menu sans type enregistré (inventaire créatif…) : on garde le nom de l'écran.
            }
        }
        return screen.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------ x-ray (#129)

    /** Bloc que le joueur finit de casser, et l'objet qu'il tient (l'outil). */
    public static void onBlockBroken(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (core == null || mc.level == null || mc.player == null) return;
        String block = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
        ItemStack tool = mc.player.getMainHandItem();
        core.onBlockBroken(block, tool.isEmpty() ? null : itemId(tool));
    }

    /** Packs de textures ajoutés par le joueur (dossier resourcepacks), sans ceux du jeu, des mods ou du serveur. */
    private static List<String> resourcePacks(Minecraft mc) {
        return mc.getResourcePackRepository().getSelectedIds().stream().filter(id -> id.startsWith("file/")).map(id -> id.substring(5)).toList();
    }

    /** Le serveur annonce un ramassage (paquet de l'animation) : l'entité de l'objet est encore là. */
    public static void onTakeItem(int itemEntityId, int playerId, int amount) {
        Minecraft mc = Minecraft.getInstance();
        if (core == null || mc.level == null || mc.player == null || playerId != mc.player.getId()) return;
        if (mc.level.getEntity(itemEntityId) instanceof ItemEntity item) core.onItemPickup(itemId(item.getItem()), amount);
    }

    private static void join(Minecraft mc) {
        playerPlaytime = -1;
        ticksSinceStats = 0;
        lastPlayer = mc.player;
        if (questRewards == null) questRewards = QuestRewards.scan(gameDir);
        core.setQuestRewards(questRewards);
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
        // Mods qui minent plusieurs blocs d'un coup ou pour le joueur (#129) : expliquent un ratio de minerais élevé.
        com.google.gson.JsonArray helpers = new com.google.gson.JsonArray();
        core.miningHelpers().forEach(helpers::add);
        fingerprint.add("miningMods", helpers);
        // Horloge de playtimeTicks (#74) : le serveur ne prend pas le changement d'horloge pour un recul.
        fingerprint.addProperty("playtimeClock", server != null ? "world" : "player");
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
        core.onAdvancements(doneIds, initialSync, playtime(mc));
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
        Integer played = out.get(PLAY_TIME);
        if (played != null) {
            playerPlaytime = played;
            ticksSinceStats = 0;
        }
        core.onStats(out, playtime(mc));
    }

    private static final String PLAY_TIME = "minecraft:custom/minecraft:play_time";
    /** Multijoueur : temps joué reçu avec les dernières statistiques (-1 : pas encore reçu). */
    private static long playerPlaytime = -1;
    private static long ticksSinceStats;

    /**
     * Temps de jeu des événements (#74). Solo : l'âge du monde. Multijoueur : le temps joué par le
     * joueur, car l'âge du monde est celui du serveur (il compte aussi le temps des autres joueurs).
     * Les statistiques n'arrivent que toutes les 60 s : on ajoute les ticks écoulés depuis.
     */
    static long playtime(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) return mc.level.getGameTime();
        long played = playerPlaytime < 0 ? 0 : playerPlaytime + ticksSinceStats;
        return Math.max(played, core.playtimeFloor());
    }

    /** Commandes de triche possibles : monde ouvert au LAN avec les commandes, ou joueur opérateur. */
    private static boolean cheatsAllowed(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        return server != null ? Compat.allowCommands(server) : mc.player.hasPermissions(2);
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
