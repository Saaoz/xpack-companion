package net.xpack.companion.forge1710;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import io.netty.channel.ChannelPipeline;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S0DPacketCollectItem;
import net.minecraft.network.play.server.S37PacketStatistics;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatCrafting;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.AchievementPage;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.xpack.companion.core.ApiClient;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.Credentials;
import net.xpack.companion.core.Diagnostics;
import net.xpack.companion.core.IntegrityRules;
import net.xpack.companion.core.ItemArrivals;
import net.xpack.companion.core.J8;
import net.xpack.companion.core.LegacyStats;
import net.xpack.companion.core.ModpackDetector;
import net.xpack.companion.core.QuestRewards;
import net.xpack.companion.core.QuestWatcher;
import net.xpack.companion.core.XPackCore;

/**
 * Pont entre Minecraft 1.7.10 (GT: New Horizons…) et le cœur du mod. Pas de Mixin : les statistiques,
 * les « achievements » (qui voyagent avec elles) et les ramassages sont lus dans la connexion par
 * {@link PacketSpy}, les commandes de triche y sont retenues avant l'envoi, le reste est relevé à
 * chaque tick du client. Pas de progrès (advancements) en 1.7.10 : les succès XPack sont les achievements.
 */
public final class Client1710 {
    private static final String MOD_VERSION = "0.3.0";
    /** Fréquence de demande des statistiques au serveur (#36). */
    private static final int STATS_INTERVAL_TICKS = 20 * 60;
    /** Première demande peu après l'entrée dans le monde : elle donne l'état initial des achievements. */
    private static final int FIRST_STATS_TICK = 40;
    /** Vérification du mode de jeu et des capacités (#47, #48). */
    private static final int STATE_INTERVAL_TICKS = 10;
    /** Relevé de l'inventaire pour le journal des objets reçus (#128). */
    private static final int ITEMS_INTERVAL_TICKS = 20;
    private static final int PAUSE_BUTTON_ID = 7_300;
    private static final String PLAY_TIME = "minecraft:custom/minecraft:play_time";
    static final List<String> SUBCOMMANDS = J8.listOf("settings", "toast", "team", "check", "diagnostic", "support", "help");

    private static XPackCore core;
    private static Path configDir;
    private static Path gameDir;
    private static List<String> modIds = J8.listOf();
    private static boolean inWorld;
    private static int ticks;
    /** Quêtes BetterQuesting du monde solo en cours (GT: New Horizons) ; null sans BetterQuesting ou en multijoueur. */
    private static QuestWatcher quests;
    private static long ticksSinceStats;
    /** Multijoueur : temps joué reçu avec les dernières statistiques (-1 : pas encore reçu). */
    private static long playerPlaytime = -1;
    private static Object lastPlayer;
    private static Set<String> questRewards;
    /** Achievements déjà transmis au cœur pendant cette partie, et premier paquet reçu (l'état initial, non vérifié). */
    private static final Set<String> doneAchievements = new HashSet<>();
    private static boolean gotFirstStats;

    /** Actions à jouer au prochain tick sans écran : le chat se ferme juste après l'envoi d'une commande. */
    private static final Queue<Runnable> DEFERRED = new ConcurrentLinkedQueue<>();

    static void defer(Runnable action) {
        DEFERRED.add(action);
    }

    private Client1710() {}

    public static XPackCore core() {
        return core;
    }

    // ------------------------------------------------------------------ démarrage

    static void init() {
        Minecraft mc = Minecraft.getMinecraft();
        gameDir = mc.mcDataDir.toPath();
        configDir = new File(mc.mcDataDir, "config").toPath();
        List<String> ids = new ArrayList<>();
        for (ModContainer m : Loader.instance().getModList()) ids.add(m.getModId());
        modIds = J8.copyOf(ids);

        Path configFile = configDir.resolve("xpack.json");
        Config config = Config.load(configFile);
        config.save(configFile); // écrit les valeurs par défaut au premier lancement
        ApiClient api = new ApiClient(ApiClient.http(config.apiUrl, "XPackCompanion/" + MOD_VERSION + " (forge 1.7.10)"));
        core = new XPackCore(config, configFile, new Credentials(Credentials.defaultLocation()), api, new Ui1710(), r -> mc.func_152344_a(r), Clock.systemUTC(), IntegrityRules.bundled());
        core.setLoadedMods(modIds);
        // Fichier du monde écrit hors du thread de jeu (#74), et une dernière fois à la fermeture.
        ExecutorService disk = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "XPack disk");
            t.setDaemon(true);
            return t;
        });
        core.writeWorldFilesWith(disk);
        Runtime.getRuntime().addShutdownHook(new Thread(core::flushWorldFile, "XPack flush"));
        Client1710 handler = new Client1710();
        FMLCommonHandler.instance().bus().register(handler);
        MinecraftForge.EVENT_BUS.register(handler);
        MinecraftForge.EVENT_BUS.register(new ChatSuggestions());
        MinecraftForge.EVENT_BUS.register(new Toasts1710());
        ClientCommandHandler.instance.registerCommand(new XPackCommand());
    }

    // ------------------------------------------------------------------ connexion : espions de paquets

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // Avant « packet_handler », présent partout : en solo, la connexion locale n'a ni « encoder » ni
        // « decoder » (#181). Les écritures partent de la fin du pipeline : la sortie voit bien C01.
        ChannelPipeline pipeline = event.manager.channel().pipeline();
        try {
            if (pipeline.get(PacketSpy.IN) == null) pipeline.addBefore("packet_handler", PacketSpy.IN, new PacketSpy.Inbound());
            if (pipeline.get(PacketSpy.OUT) == null) pipeline.addBefore("packet_handler", PacketSpy.OUT, new PacketSpy.Outbound());
        } catch (RuntimeException e) {
            // Pipeline inattendu (autre mod) : jamais couper la connexion, on se passe des espions.
            FMLLog.warning("[XPack] espions de paquets non installés : %s", e);
        }
    }

    // ------------------------------------------------------------------ tick client

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || core == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        // Seulement en jeu, sans écran : pas pendant le chargement du monde (qui fermerait l'écran) ni avec le chat ouvert.
        if (mc.currentScreen == null && mc.theWorld != null) for (Runnable r; (r = DEFERRED.poll()) != null; ) r.run();
        boolean nowInWorld = mc.theWorld != null && mc.thePlayer != null && mc.getNetHandler() != null;
        if (nowInWorld && !inWorld) join(mc);
        if (!nowInWorld && inWorld) {
            ticks = 0;
            stopQuests();
            core.onLeaveWorld();
        }
        inWorld = nowInWorld;
        Toasts1710.tick();
        Sounds1710.tick(mc);

        if (inWorld) {
            ticksSinceStats++;
            long playtime = playtime(mc);
            core.setPlaytime(playtime);
            ticks++;
            if (quests != null) quests.tick();
            if (ticks % STATE_INTERVAL_TICKS == 0 && mc.playerController != null) {
                core.onPlayerState(new XPackCore.PlayerState(mc.thePlayer.capabilities.isCreativeMode ? "creative" : "survival", mc.thePlayer.capabilities.disableDamage), playtime);
                core.onCheatsAllowed(cheatsAllowed(mc));
                core.setOtherPlayers(otherPlayers(mc));
                // Badge de difficulté (#116) : pas de verrou de difficulté en 1.7.10.
                boolean hardcore = mc.theWorld.getWorldInfo().isHardcoreModeEnabled();
                String difficulty = hardcore ? "hardcore" : mc.theWorld.difficultySetting.name().toLowerCase(Locale.ROOT);
                core.onDifficulty(difficulty, mc.getIntegratedServer() != null && !hardcore, playtime);
                core.onResourcePacks(resourcePacks(mc), playtime);
            }
            if (mc.thePlayer != lastPlayer) {
                // Réapparition ou changement de dimension : nouveau joueur, inventaire renvoyé par le serveur.
                lastPlayer = mc.thePlayer;
                core.resetInventory();
            }
            if (ticks % ITEMS_INTERVAL_TICKS == 0) observeInventory(mc, playtime);
            // Les achievements arrivent avec les statistiques : première demande peu après l'entrée, même sans statistique suivie.
            if (ticks == FIRST_STATS_TICK || (ticks % STATS_INTERVAL_TICKS == 0 && !core.trackedStats().isEmpty())) {
                mc.getNetHandler().addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.REQUEST_STATS));
            }
        }
        core.tick();
    }

    private static void join(Minecraft mc) {
        playerPlaytime = -1;
        ticksSinceStats = 0;
        lastPlayer = mc.thePlayer;
        doneAchievements.clear();
        gotFirstStats = false;
        if (questRewards == null) questRewards = QuestRewards.scan(gameDir);
        core.setQuestRewards(questRewards);
        core.onJoinWorld(worldContext(mc));
        refreshCatalog(mc);
        startQuests(mc);
    }

    /**
     * Livre BetterQuesting (GT: New Horizons) : chaque quête est un succès. Solo seulement : en
     * multijoueur les fichiers de quêtes restent sur le serveur.
     */
    private static void startQuests(Minecraft mc) {
        stopQuests();
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null || !modIds.contains("betterquesting")) return;
        Path worldDir = new File(mc.mcDataDir, "saves/" + server.getFolderName()).toPath();
        quests = new QuestWatcher(core, worldDir, mc.thePlayer.getUniqueID().toString(), Client1710::translate,
                () -> mc.gameSettings.language == null ? "en_us" : mc.gameSettings.language, () -> playtime(mc));
    }

    private static void stopQuests() {
        if (quests != null) quests.close();
        quests = null;
    }

    /** Texte du jeu pour une clé de traduction ; un texte déjà rédigé (GT: New Horizons) reste tel quel. */
    private static String translate(String key) {
        if (key == null || key.isEmpty() || key.indexOf(' ') >= 0) return key;
        String text = I18n.format(key);
        return text == null || text.startsWith("Format error") ? key : text;
    }

    private static XPackCore.WorldContext worldContext(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        Path storeFile;
        String kind;
        boolean cheatsAllowed;
        boolean creative = mc.thePlayer.capabilities.isCreativeMode;
        if (server != null) {
            storeFile = new File(mc.mcDataDir, "saves/" + server.getFolderName()).toPath().resolve("xpack.json");
            kind = "singleplayer";
            WorldInfo info = overworldInfo(server);
            cheatsAllowed = info != null && info.areCommandsAllowed();
            // Monde créé en créatif : un monde de test, même si le joueur est repassé en survie.
            creative |= info != null && info.getGameType() == WorldSettings.GameType.CREATIVE;
        } else {
            ServerData data = mc.func_147104_D();
            String address = data != null ? data.serverIP : "inconnu";
            storeFile = configDir.resolve("xpack").resolve("servers").resolve(sha256(address.toLowerCase(Locale.ROOT)).substring(0, 24) + ".json");
            kind = "multiplayer";
            cheatsAllowed = mc.thePlayer.canCommandSenderUseCommand(2, "");
        }
        JsonObject fingerprint = new JsonObject();
        fingerprint.addProperty("minecraft", "1.7.10");
        fingerprint.addProperty("loader", "forge");
        fingerprint.addProperty("modsHash", ModpackDetector.modsHash(modIds));
        fingerprint.addProperty("modCount", modIds.size());
        fingerprint.addProperty("modVersion", MOD_VERSION);
        JsonArray helpers = new JsonArray();
        for (String h : core.miningHelpers()) helpers.add(new JsonPrimitive(h));
        fingerprint.add("miningMods", helpers);
        // Horloge de playtimeTicks (#74) : le serveur ne prend pas le changement d'horloge pour un recul.
        fingerprint.addProperty("playtimeClock", server != null ? "world" : "player");
        return new XPackCore.WorldContext(storeFile, kind, Instant.now().toString(), cheatsAllowed, mc.thePlayer.getUniqueID().toString(), mc.thePlayer.getCommandSenderName(),
                ModpackDetector.detect(gameDir, modIds), fingerprint, creative);
    }

    /**
     * Temps de jeu des événements (#74). Solo : l'âge du monde. Multijoueur : le temps joué par le
     * joueur (l'âge du monde est celui du serveur). Les statistiques n'arrivent que toutes les 60 s :
     * on ajoute les ticks écoulés depuis.
     */
    private static long playtime(Minecraft mc) {
        if (mc.getIntegratedServer() != null) return mc.theWorld.getTotalWorldTime();
        long played = playerPlaytime < 0 ? 0 : playerPlaytime + ticksSinceStats;
        return Math.max(played, core.playtimeFloor());
    }

    /** Commandes de triche possibles : monde ouvert avec les commandes, ou joueur opérateur. */
    private static boolean cheatsAllowed(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return mc.thePlayer.canCommandSenderUseCommand(2, "");
        WorldInfo info = overworldInfo(server);
        return info != null && info.areCommandsAllowed();
    }

    /**
     * Infos du monde principal en solo, ou null s'il n'est pas (encore) chargé. Forge range les mondes
     * dans DimensionManager ; server.worldServers peut rester vide (crash au lancement d'un monde GT:NH).
     */
    private static WorldInfo overworldInfo(IntegratedServer server) {
        WorldServer world = DimensionManager.getWorld(0);
        if (world == null && server.worldServers.length > 0) world = server.worldServers[0];
        return world != null ? world.getWorldInfo() : null;
    }

    /** Autres joueurs dont on connaît l'identité (ceux chargés autour de nous). */
    private static Map<String, String> otherPlayers(Minecraft mc) {
        Map<String, String> others = new HashMap<>();
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p != mc.thePlayer && p.getGameProfile() != null && p.getGameProfile().getId() != null) others.put(p.getGameProfile().getId().toString(), p.getCommandSenderName());
        }
        return others;
    }

    private static List<String> resourcePacks(Minecraft mc) {
        List<String> out = new ArrayList<>();
        for (Object o : mc.getResourcePackRepository().getRepositoryEntries()) out.add(((ResourcePackRepository.Entry) o).getResourcePackName());
        return out;
    }

    // ------------------------------------------------------------------ achievements, statistiques, ramassages (paquets)

    /** Nom enregistré d'un objet (« minecraft:stone »), ou null. */
    private static String registryName(Item item) {
        return item == null ? null : Item.itemRegistry.getNameForObject(item);
    }

    /** Identifiant XPack d'un achievement : « achievement:openInventory » (voir {@link LegacyStats#achievementId}). */
    private static String achievementId(StatBase stat) {
        return LegacyStats.achievementId(stat.statId);
    }

    /**
     * Statistiques reçues (#36) : les achievements obtenus (valeur > 0) deviennent des succès (#35),
     * le reste est traduit au format commun (voir {@link LegacyStats}). Le premier paquet d'une partie
     * est l'état initial : il n'est pas vérifié.
     */
    static void onStatsPacket(S37PacketStatistics packet) {
        Map<?, ?> stats = packet.func_148974_c();
        Minecraft mc = Minecraft.getMinecraft();
        mc.func_152344_a(() -> {
            if (core == null || mc.theWorld == null || mc.thePlayer == null) return;
            if (!inWorld) {
                // Le premier paquet peut arriver avant le premier tick « en jeu » : on ouvre la partie tout de suite.
                join(mc);
                inWorld = true;
            }
            List<String> done = new ArrayList<>();
            Map<String, Integer> out = new HashMap<>();
            for (Map.Entry<?, ?> e : stats.entrySet()) {
                StatBase stat = (StatBase) e.getKey();
                int value = (Integer) e.getValue();
                if (stat.isAchievement()) {
                    String id = achievementId(stat);
                    if (value > 0 && id != null && doneAchievements.add(id)) done.add(id);
                } else {
                    String object = stat instanceof StatCrafting ? registryName(((StatCrafting) stat).func_150959_a()) : null;
                    String key = LegacyStats.toKey(stat.statId, object);
                    if (key != null) out.put(key, value);
                }
            }
            boolean initial = !gotFirstStats;
            gotFirstStats = true;
            if (!done.isEmpty() || initial) core.onAdvancements(done, initial, playtime(mc));
            Integer played = out.get(PLAY_TIME);
            if (played != null) {
                playerPlaytime = played;
                ticksSinceStats = 0;
            }
            if (!out.isEmpty()) core.onStats(out, playtime(mc));
        });
    }

    /** Catalogue (#80) : tous les achievements du jeu et des mods (GT: New Horizons en a des centaines). */
    private static void refreshCatalog(Minecraft mc) {
        if (core == null || mc.theWorld == null) return;
        // Onglet de chaque achievement, comme dans l'écran du jeu : la page du mod, sinon « Minecraft ».
        // Les pages d'abord : les achievements des mods sont aussi dans la liste du jeu.
        Map<Achievement, String> tabs = new LinkedHashMap<>();
        for (Object page : AchievementPage.getAchievementPages()) {
            AchievementPage p = (AchievementPage) page;
            String tab = p.getName() == null ? null : clean(StatCollector.translateToLocal(p.getName()));
            for (Object o : p.getAchievements()) addAchievement(tabs, (Achievement) o, tab);
        }
        for (Object o : AchievementList.achievementList) addAchievement(tabs, (Achievement) o, "Minecraft");
        List<XPackCore.AdvancementInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<Achievement, String> e : tabs.entrySet()) {
            Achievement a = e.getKey();
            String id = achievementId(a);
            if (id == null || !seen.add(id)) continue;
            try {
                String icon = a.theItemStack == null || a.theItemStack.getItem() == null ? null : registryName(a.theItemStack.getItem());
                // Le nom affiché par le jeu : la clé de traduction n'est pas toujours l'identifiant (HEE : « achievement.hee.3540 » → « achievement.newAdventures »).
                String title = clean(a.func_150951_e().getUnformattedText());
                if (title.isEmpty()) title = a.statId;
                String parent = a.parentAchievement == null ? null : achievementId(a.parentAchievement);
                out.add(new XPackCore.AdvancementInfo(id, parent, title, clean(a.getDescription()), a.getSpecial() ? "challenge" : "task", icon == null ? "minecraft:air" : icon, false, e.getValue())
                        .withPosition(a.displayColumn, a.displayRow));
            } catch (RuntimeException ex) {
                // Achievement d'un mod mal formé : on se passe de lui, pas du catalogue.
                FMLLog.fine("[XPack] achievement ignoré %s : %s", a.statId, ex);
            }
        }
        core.onAdvancementCatalog(out, mc.gameSettings.language == null ? "en_us" : mc.gameSettings.language);
    }

    private static void addAchievement(Map<Achievement, String> tabs, Achievement a, String tab) {
        if (a != null && a.statId != null && !tabs.containsKey(a)) tabs.put(a, tab);
    }

    /** Texte sans codes de couleur (« §a »), sans espaces autour ; vide si absent. */
    private static String clean(String s) {
        if (s == null) return "";
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(s);
        return plain == null ? "" : plain.trim();
    }

    /** Ramassage d'un objet (#128) : l'entité de l'objet existe encore tant que la tâche passe avant celle du jeu. */
    static void onCollectItemPacket(S0DPacketCollectItem packet) {
        int itemId = packet.func_149354_c();
        int playerId = packet.func_149353_d();
        Minecraft mc = Minecraft.getMinecraft();
        mc.func_152344_a(() -> {
            if (core == null || mc.theWorld == null || mc.thePlayer == null || playerId != mc.thePlayer.getEntityId()) return;
            Entity e = mc.theWorld.getEntityByID(itemId);
            if (e instanceof EntityItem) {
                ItemStack stack = ((EntityItem) e).getEntityItem();
                if (stack != null) core.onItemPickup(itemId(stack), stack.stackSize);
            }
        });
    }

    // ------------------------------------------------------------------ objets reçus (#128)

    /** Inventaire complet (armure) et objet tenu au curseur, par identifiant d'objet. */
    private static void observeInventory(Minecraft mc, long playtime) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) count(counts, s);
        for (ItemStack s : mc.thePlayer.inventory.armorInventory) count(counts, s);
        count(counts, mc.thePlayer.inventory.getItemStack());
        boolean nearPlayer = false;
        for (Object p : mc.theWorld.playerEntities) {
            if (p != mc.thePlayer && ((Entity) p).getDistanceSqToEntity(mc.thePlayer) < 64) nearPlayer = true;
        }
        core.onInventory(counts, new ItemArrivals.Context(screenId(mc.currentScreen), nearPlayer), playtime);
    }

    private static void count(Map<String, Integer> counts, ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return;
        String id = itemId(stack);
        Integer prev = counts.get(id);
        counts.put(id, (prev == null ? 0 : prev) + stack.stackSize);
    }

    private static String itemId(ItemStack stack) {
        String id = registryName(stack.getItem());
        return id == null ? "unknown" : id;
    }

    /** Écran ouvert à l'arrivée d'un objet : inventaire, chat, ou nom de l'écran (coffre, machine GregTech, quêtes d'un mod). */
    private static String screenId(GuiScreen screen) {
        if (screen == null) return null;
        if (screen instanceof GuiChat) return "chat";
        if (screen instanceof GuiInventory) return "inventory";
        return screen.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------ commandes (#44) et /xpack

    /**
     * Avant l'envoi d'une commande (#44), sur le fil du jeu. Faux : retenue, l'alerte demande
     * confirmation puis renvoie la commande telle quelle.
     */
    static boolean beforeCommand(String command) {
        if (core == null) return true;
        Consumer<String> resend = c -> {
            Minecraft mc = Minecraft.getMinecraft();
            PacketSpy.allowOnce("/" + c);
            if (mc.getNetHandler() != null) mc.getNetHandler().addToSendQueue(new C01PacketChatMessage("/" + c));
        };
        return core.onCommand(command, resend);
    }

    /** Commandes du mod, traitées dans le client et jamais envoyées au serveur. Vrai si la commande est à nous. */
    static boolean handleLocalCommand(String command) {
        String c = command.trim();
        if (core == null || !(c.equalsIgnoreCase("xpack") || c.toLowerCase(Locale.ROOT).startsWith("xpack "))) return false;
        String sub = c.length() > 5 ? c.substring(5).trim().toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "diagnostic":
            case "diag":
                runDiagnostic();
                break;
            case "check":
            case "verifier":
            case "vérifier":
                core.checkPresence();
                break;
            case "settings":
            case "reglages":
            case "réglages":
                defer(() -> Minecraft.getMinecraft().displayGuiScreen(new SettingsScreen1710(null)));
                break;
            case "toast":
            case "essai":
            case "test":
                core.previewToast(Lang.tr("xpack.toast.preview"));
                break;
            case "support":
                defer(() -> Minecraft.getMinecraft().displayGuiScreen(new SupportScreen1710(null)));
                break;
            case "team":
            case "equipe":
            case "équipe":
                Ui1710.say("Cet écran n'est pas encore disponible en 1.7.10 / This screen is not available yet on 1.7.10.");
                break;
            default:
                help();
        }
        return true;
    }

    private static void help() {
        Ui1710.say(Lang.tr("xpack.command.help.title"));
        for (String sub : SUBCOMMANDS) {
            Ui1710.line("  " + net.minecraft.util.EnumChatFormatting.AQUA + "/xpack " + sub + net.minecraft.util.EnumChatFormatting.DARK_GRAY + " — " + net.minecraft.util.EnumChatFormatting.GRAY + Lang.tr("xpack.command." + sub));
        }
    }

    /** /xpack diagnostic : fichiers du lanceur (anonymisés) + modpack détecté, dans un dossier à envoyer. */
    private static void runDiagnostic() {
        ModpackDetector.ModpackInfo detected = ModpackDetector.detect(gameDir, modIds);
        String summary = "XPack Companion " + MOD_VERSION + " (forge 1.7.10), Minecraft 1.7.10\nModpack détecté : " + Diagnostics.describe(detected) + "\nMods chargés : " + modIds.size();
        try {
            Diagnostics.Result r = Diagnostics.write(gameDir, gameDir.resolve("xpack-diagnostic"), summary, modIds, Clock.systemUTC());
            Ui1710.say(Lang.tr("xpack.diagnostic.done", Diagnostics.describe(detected), r.found().isEmpty() ? "-" : String.join(", ", r.found())) + " " + r.folder().toAbsolutePath());
        } catch (IOException e) {
            Ui1710.say(Lang.tr("xpack.diagnostic.failed", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ bouton ⚙ et badge du menu pause

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onPauseMenuInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiIngameMenu)) return;
        // Bouton ⚙ en haut à gauche, le badge d'état s'affiche à sa droite (comme dans les autres versions).
        event.buttonList.add(new GuiButton(PAUSE_BUTTON_ID, 4, 4, 20, 20, "⚙"));
    }

    /** Pastille 🟢 comptabilisé / 🟡 hors-ligne ou non lié / 🔴 non comptabilisé (#39), à droite du bouton ⚙. */
    @SubscribeEvent
    public void onPauseMenuDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiIngameMenu) || core == null || !core.config().badge) return;
        XPackCore.Status status = core.status();
        int color;
        switch (status) {
            case COUNTED:
                color = 0x55FF55;
                break;
            case OFFLINE:
            case UNLINKED:
                color = 0xFFD24A;
                break;
            case NOT_COUNTED:
            case STARTED_LATE:
                color = 0xFF5555;
                break;
            default:
                color = 0x888888;
        }
        net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String label = "● ";
        font.drawString(label, 28, 10, 0xFF000000 | color);
        font.drawString(net.minecraft.util.EnumChatFormatting.WHITE + "XPack · " + net.minecraft.util.EnumChatFormatting.GRAY + Lang.tr("xpack.badge." + status.name().toLowerCase(Locale.ROOT)), 28 + font.getStringWidth(label), 10, 0xFFFFFF);
    }

    @SubscribeEvent
    public void onPauseMenuAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.gui instanceof GuiIngameMenu && event.button.id == PAUSE_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new SettingsScreen1710(event.gui));
        }
    }

    private static String sha256(String s) {
        try {
            return J8.hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
