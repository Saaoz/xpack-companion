package net.xpack.companion.forge1122;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CPacketChatMessage;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraft.network.play.server.SPacketAdvancementInfo;
import net.minecraft.network.play.server.SPacketCollectItem;
import net.minecraft.network.play.server.SPacketStatistics;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.stats.StatBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.GameType;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
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
 * Pont entre Minecraft 1.12.2 (noms MCP) et le cœur du mod. Pas de Mixin en 1.12.2 : les progrès,
 * statistiques et ramassages sont lus dans les paquets du serveur par {@link PacketSpy}, les
 * commandes par l'événement de chat de Forge, le reste à chaque tick du client.
 */
public final class Client1122 {
    private static final String MOD_VERSION = "0.4.0";
    /** Fréquence de demande des statistiques au serveur (#36). */
    private static final int STATS_INTERVAL_TICKS = 20 * 60;
    /** Vérification du mode de jeu et des capacités (#47, #48). */
    private static final int STATE_INTERVAL_TICKS = 10;
    /** Relevé de l'inventaire pour le journal des objets reçus (#128). */
    private static final int ITEMS_INTERVAL_TICKS = 20;
    private static final int PAUSE_BUTTON_ID = 7_300;
    private static final String PLAY_TIME = "minecraft:custom/minecraft:play_time";

    private static XPackCore core;
    private static Path configDir;
    private static Path gameDir;
    private static List<String> modIds = J8.listOf();
    private static boolean inWorld;
    private static int ticks;
    /** Quêtes BetterQuesting du monde solo en cours (RLCraft) ; null sans BetterQuesting ou en multijoueur. */
    private static QuestWatcher quests;
    private static long ticksSinceStats;
    /** Multijoueur : temps joué reçu avec les dernières statistiques (-1 : pas encore reçu). */
    private static long playerPlaytime = -1;
    private static Object lastPlayer;
    private static Set<String> questRewards;

    /** Actions à jouer au prochain tick : le chat se ferme juste après l'envoi d'une commande et refermerait un écran ouvert tout de suite. */
    private static final java.util.Queue<Runnable> DEFERRED = new java.util.concurrent.ConcurrentLinkedQueue<>();

    static void defer(Runnable action) {
        DEFERRED.add(action);
    }

    private Client1122() {}

    public static XPackCore core() {
        return core;
    }

    // ------------------------------------------------------------------ démarrage

    static void init() {
        Minecraft mc = Minecraft.getMinecraft();
        gameDir = mc.gameDir.toPath();
        configDir = new File(mc.gameDir, "config").toPath();
        List<String> ids = new ArrayList<>();
        for (ModContainer m : Loader.instance().getModList()) ids.add(m.getModId());
        modIds = J8.copyOf(ids);

        Path configFile = configDir.resolve("xpack.json");
        Config config = Config.load(configFile);
        config.save(configFile); // écrit les valeurs par défaut au premier lancement
        ApiClient api = new ApiClient(ApiClient.http(config.apiUrl, "XPackCompanion/" + MOD_VERSION + " (forge 1.12.2)"));
        core = new XPackCore(config, configFile, new Credentials(Credentials.defaultLocation()), api, new Ui1122(), mc::addScheduledTask, Clock.systemUTC(), IntegrityRules.bundled());
        core.setLoadedMods(modIds);
        // Fichier du monde écrit hors du thread de jeu (#74), et une dernière fois à la fermeture.
        ExecutorService disk = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "XPack disk");
            t.setDaemon(true);
            return t;
        });
        core.writeWorldFilesWith(disk);
        Runtime.getRuntime().addShutdownHook(new Thread(core::flushWorldFile, "XPack flush"));
        MinecraftForge.EVENT_BUS.register(new Client1122());
        MinecraftForge.EVENT_BUS.register(new ChatSuggestions());
        MinecraftForge.EVENT_BUS.register(new Toasts1122());
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new XPackCommand());
    }

    // ------------------------------------------------------------------ connexion : espion de paquets

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        NetworkManager manager = event.getManager();
        if (manager.channel().pipeline().get(PacketSpy.NAME) == null) manager.channel().pipeline().addBefore("packet_handler", PacketSpy.NAME, new PacketSpy());
    }

    // ------------------------------------------------------------------ tick client

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || core == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        // Seulement en jeu, sans écran : pas pendant le chargement du monde (qui fermerait l'écran) ni avec le chat ouvert.
        if (mc.currentScreen == null && mc.world != null) for (Runnable r; (r = DEFERRED.poll()) != null; ) r.run();
        boolean nowInWorld = mc.world != null && mc.player != null && mc.getConnection() != null;
        if (nowInWorld && !inWorld) join(mc);
        if (!nowInWorld && inWorld) {
            ticks = 0;
            stopQuests();
            core.onLeaveWorld();
        }
        inWorld = nowInWorld;
        Toasts1122.tick(mc);
        Sounds1122.tick(mc);
        if (inWorld) ItemIcons1122.tick(mc, core);

        if (inWorld) {
            ticksSinceStats++;
            long playtime = playtime(mc);
            core.setPlaytime(playtime);
            ticks++;
            if (quests != null) quests.tick();
            if (ticks % STATE_INTERVAL_TICKS == 0 && mc.playerController != null) {
                core.onPlayerState(new XPackCore.PlayerState(mc.playerController.getCurrentGameType().getName(), mc.player.capabilities.disableDamage), playtime);
                core.onCheatsAllowed(cheatsAllowed(mc));
                Map<String, String> others = new HashMap<>();
                for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
                    if (!info.getGameProfile().getId().equals(mc.player.getUniqueID())) others.put(info.getGameProfile().getId().toString(), info.getGameProfile().getName());
                }
                core.setOtherPlayers(others);
                // Badge de difficulté (#116).
                boolean hardcore = mc.world.getWorldInfo().isHardcoreModeEnabled();
                String difficulty = hardcore ? "hardcore" : mc.world.getDifficulty().name().toLowerCase(Locale.ROOT);
                core.onDifficulty(difficulty, mc.getIntegratedServer() != null && !mc.world.getWorldInfo().isDifficultyLocked() && !hardcore, playtime);
                core.onResourcePacks(resourcePacks(mc), playtime);
            }
            if (mc.player != lastPlayer) {
                // Réapparition ou changement de dimension : nouveau joueur, inventaire renvoyé par le serveur.
                lastPlayer = mc.player;
                core.resetInventory();
            }
            if (ticks % ITEMS_INTERVAL_TICKS == 0) observeInventory(mc, playtime);
            // Première demande 5 s après l'entrée dans le monde, puis toutes les 60 s.
            if ((ticks == 100 || ticks % STATS_INTERVAL_TICKS == 0) && !core.trackedStats().isEmpty()) {
                mc.getConnection().sendPacket(new CPacketClientStatus(CPacketClientStatus.State.REQUEST_STATS));
            }
        }
        core.tick();
    }

    private static void join(Minecraft mc) {
        playerPlaytime = -1;
        ticksSinceStats = 0;
        lastPlayer = mc.player;
        if (questRewards == null) questRewards = QuestRewards.scan(gameDir);
        core.setQuestRewards(questRewards);
        core.onJoinWorld(worldContext(mc));
        refreshCatalog(mc);
        startQuests(mc);
    }

    /**
     * Livre BetterQuesting (RLCraft) : chaque quête est un succès. Solo seulement : en multijoueur
     * les fichiers de quêtes restent sur le serveur.
     */
    private static void startQuests(Minecraft mc) {
        stopQuests();
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null || !modIds.contains("betterquesting")) return;
        Path worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory().toPath();
        quests = new QuestWatcher(core, worldDir, mc.player.getUniqueID().toString(), Client1122::translate,
                () -> mc.gameSettings.language == null ? "en_us" : mc.gameSettings.language, () -> playtime(mc));
    }

    private static void stopQuests() {
        if (quests != null) quests.close();
        quests = null;
    }

    /** Texte du jeu pour une clé de traduction ; un texte déjà rédigé (GT: New Horizons) reste tel quel. */
    private static String translate(String key) {
        if (key == null || key.isEmpty() || key.indexOf(' ') >= 0 || !I18n.hasKey(key)) return key;
        return I18n.format(key);
    }

    private static XPackCore.WorldContext worldContext(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        Path storeFile;
        String kind;
        boolean cheatsAllowed;
        GameType mode = mc.playerController != null ? mc.playerController.getCurrentGameType() : GameType.SURVIVAL;
        boolean creative = mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
        if (server != null) {
            storeFile = server.getWorld(0).getSaveHandler().getWorldDirectory().toPath().resolve("xpack.json");
            kind = "singleplayer";
            cheatsAllowed = server.getWorld(0).getWorldInfo().areCommandsAllowed();
            // Monde créé en créatif : un monde de test, même si le joueur est repassé en survie.
            creative |= server.getWorld(0).getWorldInfo().getGameType() == GameType.CREATIVE;
        } else {
            net.minecraft.client.multiplayer.ServerData data = mc.getCurrentServerData();
            String address = data != null ? data.serverIP : "inconnu";
            storeFile = configDir.resolve("xpack").resolve("servers").resolve(sha256(address.toLowerCase(Locale.ROOT)).substring(0, 24) + ".json");
            kind = "multiplayer";
            cheatsAllowed = mc.player.canUseCommand(2, "");
        }
        JsonObject fingerprint = new JsonObject();
        fingerprint.addProperty("minecraft", "1.12.2");
        fingerprint.addProperty("loader", "forge");
        fingerprint.addProperty("modsHash", ModpackDetector.modsHash(modIds));
        fingerprint.addProperty("modCount", modIds.size());
        fingerprint.addProperty("modVersion", MOD_VERSION);
        JsonArray helpers = new JsonArray();
        for (String h : core.miningHelpers()) helpers.add(new com.google.gson.JsonPrimitive(h));
        fingerprint.add("miningMods", helpers);
        // Horloge de playtimeTicks (#74) : le serveur ne prend pas le changement d'horloge pour un recul.
        fingerprint.addProperty("playtimeClock", server != null ? "world" : "player");
        return new XPackCore.WorldContext(storeFile, kind, Instant.now().toString(), cheatsAllowed, mc.player.getUniqueID().toString(), mc.player.getName(),
                ModpackDetector.detect(gameDir, modIds), fingerprint, creative);
    }

    /**
     * Temps de jeu des événements (#74). Solo : l'âge du monde. Multijoueur : le temps joué par le
     * joueur, car l'âge du monde est celui du serveur. Les statistiques n'arrivent que toutes les
     * 60 s : on ajoute les ticks écoulés depuis.
     */
    private static long playtime(Minecraft mc) {
        if (mc.getIntegratedServer() != null) return mc.world.getTotalWorldTime();
        long played = playerPlaytime < 0 ? 0 : playerPlaytime + ticksSinceStats;
        return Math.max(played, core.playtimeFloor());
    }

    /** Commandes de triche possibles : monde ouvert avec les commandes, ou joueur opérateur. */
    private static boolean cheatsAllowed(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        return server != null ? server.getWorld(0).getWorldInfo().areCommandsAllowed() : mc.player.canUseCommand(2, "");
    }

    private static List<String> resourcePacks(Minecraft mc) {
        List<String> out = new ArrayList<>();
        for (ResourcePackRepository.Entry e : mc.getResourcePackRepository().getRepositoryEntries()) out.add(e.getResourcePackName());
        return out;
    }

    // ------------------------------------------------------------------ progrès, statistiques, ramassages (paquets)

    /**
     * Progrès reçus (#35). On lit après le traitement du paquet par le jeu : notre tâche en planifie
     * une seconde, qui passe donc après celle que le jeu planifie pour ce même paquet.
     */
    static void onAdvancementPacket(SPacketAdvancementInfo packet) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean reset = packet.isFirstSync();
        boolean added = !packet.getAdvancementsToAdd().isEmpty();
        Map<ResourceLocation, AdvancementProgress> progress = packet.getProgressUpdates();
        mc.addScheduledTask(() -> mc.addScheduledTask(() -> {
            if (core == null || mc.world == null || mc.player == null) return;
            if (!inWorld) {
                // Le paquet initial arrive avant le premier tick « en jeu » : on ouvre la partie tout de suite.
                join(mc);
                inWorld = true;
            }
            List<String> done = new ArrayList<>();
            for (Map.Entry<ResourceLocation, AdvancementProgress> e : progress.entrySet()) {
                if (e.getValue().isDone()) done.add(e.getKey().toString());
            }
            if (!done.isEmpty()) core.onAdvancements(done, reset, playtime(mc));
            // Progrès nouvellement visibles (multijoueur) : le catalogue envoyé à XPack s'enrichit (#80).
            if (reset || added) refreshCatalog(mc);
        }));
    }

    /** Catalogue des progrès du modpack (#80) : solo = l'arbre complet du serveur intégré, multijoueur = ce que le serveur montre. */
    private static void refreshCatalog(Minecraft mc) {
        if (core == null || mc.world == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        Iterable<Advancement> all = server != null
                ? server.getAdvancementManager().getAdvancements()
                : mc.getConnection() != null ? mc.getConnection().getAdvancementManager().getAdvancementList().getAdvancements() : new ArrayList<Advancement>();
        List<XPackCore.AdvancementInfo> out = new ArrayList<>();
        for (Advancement a : all) {
            DisplayInfo d = a.getDisplay();
            if (d == null) continue;
            ResourceLocation icon = Item.REGISTRY.getNameForObject(d.getIcon().getItem());
            out.add(new XPackCore.AdvancementInfo(a.getId().toString(), a.getParent() == null ? null : a.getParent().getId().toString(), d.getTitle().getUnformattedText(),
                    d.getDescription().getUnformattedText(), d.getFrame().getName(), icon == null ? "minecraft:air" : icon.toString(), d.isHidden()));
        }
        core.onAdvancementCatalog(out, mc.gameSettings.language == null ? "en_us" : mc.gameSettings.language);
    }

    /** Statistiques reçues après notre demande (#36), au format commun (voir {@link LegacyStats}). */
    static void onStatsPacket(SPacketStatistics packet) {
        Map<StatBase, Integer> stats = packet.getStatisticMap();
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (core == null || mc.world == null) return;
            Map<String, Integer> out = new HashMap<>();
            for (Map.Entry<StatBase, Integer> e : stats.entrySet()) {
                String key = LegacyStats.toKey(e.getKey().statId);
                if (key != null) out.put(key, e.getValue());
            }
            Integer played = out.get(PLAY_TIME);
            if (played != null) {
                playerPlaytime = played;
                ticksSinceStats = 0;
            }
            core.onStats(out, playtime(mc));
        });
    }

    /** Ramassage d'un objet (#128) : l'entité de l'objet existe encore tant que la tâche passe avant celle du jeu. */
    static void onCollectItemPacket(SPacketCollectItem packet) {
        int itemId = packet.getCollectedItemEntityID();
        int playerId = packet.getEntityID();
        int amount = packet.getAmount();
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (core == null || mc.world == null || mc.player == null || playerId != mc.player.getEntityId()) return;
            Entity e = mc.world.getEntityByID(itemId);
            if (e instanceof EntityItem) core.onItemPickup(itemId(((EntityItem) e).getItem()), amount);
        });
    }

    // ------------------------------------------------------------------ objets reçus (#128)

    /** Inventaire complet (armure, seconde main) et objet tenu au curseur, par identifiant d'objet. */
    private static void observeInventory(Minecraft mc, long playtime) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack s : mc.player.inventory.mainInventory) count(counts, s);
        for (ItemStack s : mc.player.inventory.armorInventory) count(counts, s);
        for (ItemStack s : mc.player.inventory.offHandInventory) count(counts, s);
        count(counts, mc.player.inventory.getItemStack());
        boolean nearPlayer = false;
        for (Object p : mc.world.playerEntities) {
            if (p != mc.player && ((Entity) p).getDistanceSq(mc.player) < 64) nearPlayer = true;
        }
        core.onInventory(counts, new ItemArrivals.Context(screenId(mc.currentScreen), nearPlayer), playtime);
    }

    private static void count(Map<String, Integer> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String id = itemId(stack);
        Integer prev = counts.get(id);
        counts.put(id, (prev == null ? 0 : prev) + stack.getCount());
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }

    /** Écran ouvert à l'arrivée d'un objet : inventaire, chat, ou nom de l'écran (coffre, four, quêtes d'un mod). */
    private static String screenId(GuiScreen screen) {
        if (screen == null) return null;
        if (screen instanceof GuiChat) return "chat";
        if (screen instanceof GuiInventory) return "inventory";
        if (screen instanceof GuiContainer) return screen.getClass().getSimpleName();
        return screen.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------ commandes (#44) et /xpack

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (core == null || message == null || !message.startsWith("/")) return;
        String command = message.substring(1);
        if (handleLocalCommand(command)) {
            event.setCanceled(true);
            return;
        }
        // Retenue : l'écran d'alerte demande confirmation, puis renvoie la commande tel quel (sans repasser par ici).
        Consumer<String> resend = c -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getConnection() != null) mc.getConnection().sendPacket(new CPacketChatMessage("/" + c));
        };
        if (!core.onCommand(command, resend)) event.setCanceled(true);
    }

    /** Commandes du mod, traitées dans le client et jamais envoyées au serveur. Vrai si la commande est à nous. */
    static boolean handleLocalCommand(String command) {
        String c = command.trim();
        if (core == null || !(c.equalsIgnoreCase("xpack") || c.toLowerCase(Locale.ROOT).startsWith("xpack "))) return false;
        String sub = c.length() > 5 ? c.substring(5).trim().toLowerCase(Locale.ROOT) : "";
        Minecraft mc = Minecraft.getMinecraft();
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
                defer(() -> mc.displayGuiScreen(new SettingsScreen1122(null)));
                break;
            case "toast":
            case "essai":
            case "test":
                core.previewToast(Lang.tr("xpack.toast.preview"));
                break;
            case "support":
                defer(() -> mc.displayGuiScreen(new SupportScreen1122(null)));
                break;
            case "team":
            case "equipe":
            case "équipe":
                defer(() -> Minecraft.getMinecraft().displayGuiScreen(new TeamScreen1122(null)));
                break;
            default:
                help();
        }
        return true;
    }

    /** Sous-commandes, dans l'ordre de l'aide. Leur description est la clé {@code xpack.command.<nom>}. */
    static final List<String> SUBCOMMANDS = J8.listOf("settings", "toast", "team", "check", "diagnostic", "support", "help");

    private static void help() {
        Ui1122.say(Lang.tr("xpack.command.help.title"));
        for (String sub : SUBCOMMANDS) Ui1122.line("  " + net.minecraft.util.text.TextFormatting.AQUA + "/xpack " + sub + net.minecraft.util.text.TextFormatting.DARK_GRAY + " — " + net.minecraft.util.text.TextFormatting.GRAY + Lang.tr("xpack.command." + sub));
    }

    /** /xpack diagnostic : fichiers du lanceur (anonymisés) + modpack détecté, dans un dossier à envoyer. */
    private static void runDiagnostic() {
        ModpackDetector.ModpackInfo detected = ModpackDetector.detect(gameDir, modIds);
        String summary = "XPack Companion " + MOD_VERSION + " (forge 1.12.2), Minecraft 1.12.2\nModpack détecté : " + Diagnostics.describe(detected) + "\nMods chargés : " + modIds.size();
        try {
            Diagnostics.Result r = Diagnostics.write(gameDir, gameDir.resolve("xpack-diagnostic"), summary, modIds, Clock.systemUTC());
            Ui1122.say(Lang.tr("xpack.diagnostic.done", Diagnostics.describe(detected), r.found().isEmpty() ? "-" : String.join(", ", r.found())) + " " + r.folder().toAbsolutePath());
        } catch (IOException e) {
            Ui1122.say(Lang.tr("xpack.diagnostic.failed", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ bouton XPack du menu pause

    @SubscribeEvent
    public void onPauseMenuInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiIngameMenu)) return;
        // Bouton ⚙ en haut à gauche, le badge d'état s'affiche à sa droite (comme dans les autres versions).
        event.getButtonList().add(new GuiButton(PAUSE_BUTTON_ID, 4, 4, 20, 20, "⚙"));
    }

    /** Pastille 🟢 comptabilisé / 🟡 hors-ligne ou non lié / 🔴 non comptabilisé (#39), à droite du bouton ⚙. */
    @SubscribeEvent
    public void onPauseMenuDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiIngameMenu) || core == null || !core.config().badge) return;
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
        font.drawString(net.minecraft.util.text.TextFormatting.WHITE + "XPack · " + net.minecraft.util.text.TextFormatting.GRAY + Lang.tr("xpack.badge." + status.name().toLowerCase(Locale.ROOT)), 28 + font.getStringWidth(label), 10, 0xFFFFFF);
    }

    @SubscribeEvent
    public void onPauseMenuAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getGui() instanceof GuiIngameMenu && event.getButton().id == PAUSE_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new SettingsScreen1122(event.getGui()));
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
