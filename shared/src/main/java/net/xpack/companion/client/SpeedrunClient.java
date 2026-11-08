package net.xpack.companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.SpeedrunCategories;
import net.xpack.companion.core.SpeedrunExport;
import net.xpack.companion.core.SpeedrunHud;
import net.xpack.companion.core.SpeedrunMode;
import net.xpack.companion.core.SpeedrunPresets;
import net.xpack.companion.core.SpeedrunTimer;
import net.xpack.companion.core.XPackCore;

/**
 * Speedrun libre (#219) : touches, chrono des ticks de jeu et affichage à l'écran. La logique est dans
 * {@link SpeedrunTimer} (testée sans Minecraft) ; ici, uniquement ce qui touche au jeu. Un chrono libre n'est
 * jamais vérifié et ne part pas au classement.
 */
final class SpeedrunClient {
    private static final int LINE = 10;
    private static final int GREY = 0xAAAAAA;
    private static final int WHITE = 0xFFFFFF;
    private static final int GREEN = 0x55FF55;
    private static final int RED = 0xFF5555;

    private static final SpeedrunTimer TIMER = new SpeedrunTimer(System::currentTimeMillis);
    /** Touches maintenues au tick précédent : une pression ne compte qu'une fois. */
    private static final boolean[] HELD = new boolean[5];
    private static boolean loaded;

    private SpeedrunClient() {}

    static void load(XPackCore core) {
        if (loaded) return;
        loaded = true;
        TIMER.setSteps(core.config().speedrunSteps == null ? new ArrayList<String>() : core.config().speedrunSteps);
        TIMER.setBest(core.config().speedrunBest);
    }

    private static InputConstants.Key key(String name, int fallback) {
        try {
            return InputConstants.getKey(name);
        } catch (RuntimeException e) {
            return InputConstants.Type.KEYSYM.getOrCreate(fallback);
        }
    }

    private static boolean pressedOnce(Minecraft mc, int slot, InputConstants.Key key) {
        boolean down = key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        boolean fresh = down && !HELD[slot];
        HELD[slot] = down;
        return fresh;
    }

    /** Une fois par tick client. Les touches ne comptent qu'en jeu, sans écran ouvert (ni chat, ni menu). */
    static void tick(Minecraft mc, XPackCore core, boolean inWorld) {
        load(core);
        if (!inWorld || !core.config().speedrunEnabled) return; // désactivé par défaut : aucune touche, aucun chrono
        if (!mc.isPaused()) {
            TIMER.gameTick();
            // Pause du chrono (touche) : le jeu tourne, donc le joueur ne doit pas bouger (#220). Échap en solo fige
            // la partie : aucun déplacement possible, rien à surveiller.
            // Pause due à la sortie du monde : le chrono repart tout seul au premier déplacement (il n'est pas perdu).
            boolean wasExitPaused = TIMER.isExitPaused();
            if (mc.player != null && mc.level != null
                    && TIMER.watchMovement(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.level.dimension().location().toString())) {
                MinecraftUi.say(Texts.tr("xpack.speedrun.lost"));
            } else if (wasExitPaused && TIMER.isRunning()) {
                MinecraftUi.say(Texts.tr("xpack.speedrun.resumed"));
            }
        }
        Config c = core.config();
        SpeedrunMode mode = SpeedrunMode.parse(c.speedrunMode);
        // Le moment du départ automatique : l'apparition dans un monde neuf (en solo), même si un écran est ouvert.
        // Consommé même si le départ est manuel.
        if (core.takeFreshSpawn() && mode.startAuto() && !TIMER.isRunning() && TIMER.state() != SpeedrunTimer.State.PAUSED) start(mc);
        if (mc.screen != null) {
            java.util.Arrays.fill(HELD, true); // une touche tenue en sortant d'un écran ne déclenche rien
            return;
        }
        if (pressedOnce(mc, 0, key(c.speedrunToggleKey, InputConstants.KEY_NUMPAD1))) pauseResume(mc);
        if (pressedOnce(mc, 1, key(c.speedrunSplitKey, InputConstants.KEY_NUMPAD2))) split(core);
        if (pressedOnce(mc, 2, key(c.speedrunStopKey, InputConstants.KEY_NUMPAD3)) && !mode.endAuto()) stop(core);
        if (pressedOnce(mc, 3, key(c.speedrunResetKey, InputConstants.KEY_NUMPAD4))) reset();
        if (pressedOnce(mc, 4, key(c.speedrunStartKey, InputConstants.KEY_NUMPAD0)) && !mode.startAuto()) start(mc);
    }

    // ------------------------------------------------------------------ actions (touches et écran /xpack speedrun)

    static SpeedrunTimer timer() {
        return TIMER;
    }

    static void toggle(Minecraft mc) {
        boolean wasIdle = !TIMER.isRunning() && TIMER.state() != SpeedrunTimer.State.PAUSED;
        TIMER.toggle();
        MinecraftUi.say(Texts.tr(wasIdle ? "xpack.speedrun.started" : TIMER.isRunning() ? "xpack.speedrun.resumed" : "xpack.speedrun.paused"));
        if (mc.player != null && mc.level != null) TIMER.watchMovement(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.level.dimension().location().toString()); // point de départ de la pause
    }

    /** Départ du chrono (touche de départ, ou apparition dans un monde neuf en départ automatique) : sans effet s'il tourne déjà. */
    static void start(Minecraft mc) {
        if (TIMER.isRunning() || TIMER.state() == SpeedrunTimer.State.PAUSED) return;
        TIMER.start();
        MinecraftUi.say(Texts.tr("xpack.speedrun.started"));
    }

    /** Pause puis reprise. Sans effet si le chrono n'est pas lancé. */
    static void pauseResume(Minecraft mc) {
        if (!TIMER.isRunning() && TIMER.state() != SpeedrunTimer.State.PAUSED) return;
        toggle(mc);
    }

    /** Début des crédits : fin du chrono libre, si la fin est automatique. */
    static void onCredits(XPackCore core) {
        if (!core.config().speedrunEnabled) return;
        if (SpeedrunMode.parse(core.config().speedrunMode).endAuto()) stop(core);
    }

    // ------------------------------------------------------------------ un chrono par monde

    private static java.nio.file.Path worldFile(String worldId) {
        return XPackClient.gameDirectory().resolve("xpack-speedrun").resolve("worlds").resolve(worldId.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    /**
     * Quitter le monde : le chrono en cours passe en pause et reste lié à ce monde (fichier du dossier xpack-speedrun),
     * y compris d'un lancement du jeu à l'autre : les gros modpacks se jouent en plusieurs fois. À appeler avant que
     * le monde ne soit oublié. La run classée, elle, est abandonnée en quittant le monde ({@code XPackCore.onLeaveWorld}).
     */
    static void onLeaveWorld(XPackCore core) {
        String id = core.worldId();
        if (!core.config().speedrunEnabled || id == null) return;
        java.nio.file.Path file = worldFile(id);
        try {
            if (!TIMER.isActive()) {
                java.nio.file.Files.deleteIfExists(file); // chrono remis à zéro : pas de vieux chrono à retrouver
                return;
            }
            TIMER.pauseForExit();
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, TIMER.toJson().toString());
        } catch (java.io.IOException e) {
            // Chrono non gardé : il sera simplement remis à zéro au retour.
        }
    }

    /**
     * Ouvrir un monde : son chrono, s'il en avait un, est retrouvé en pause. Il reprend tout seul si le départ est
     * automatique, sinon à la touche de pause / reprise. Un monde sans chrono repart d'un chrono neuf, qui ne garde
     * rien du monde précédent.
     */
    static void onJoinWorld(XPackCore core) {
        if (!core.config().speedrunEnabled) return;
        load(core);
        TIMER.reset();
        String id = core.worldId();
        if (id == null) return;
        try {
            java.nio.file.Path file = worldFile(id);
            if (!java.nio.file.Files.exists(file) || !TIMER.restore(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject())) return;
        } catch (java.io.IOException | RuntimeException e) {
            TIMER.reset();
            return;
        }
        if (TIMER.state() != SpeedrunTimer.State.PAUSED) return; // terminé ou perdu : affiché tel quel
        if (SpeedrunMode.parse(core.config().speedrunMode).startAuto()) {
            TIMER.resume();
            MinecraftUi.say(Texts.tr("xpack.speedrun.resumed"));
        } else {
            MinecraftUi.say(Texts.tr("xpack.speedrun.rejoined"));
        }
    }

    static void split(XPackCore core) {
        SpeedrunTimer.Split s = TIMER.split();
        if (s == null) return;
        MinecraftUi.say(Texts.tr("xpack.speedrun.split", s.name, SpeedrunTimer.format(s.igtTicks * 50)));
        if (TIMER.state() == SpeedrunTimer.State.FINISHED) finished(core);
    }

    static void stop(XPackCore core) {
        if (!TIMER.isRunning() && TIMER.state() != SpeedrunTimer.State.PAUSED) return;
        TIMER.stop();
        finished(core);
    }

    static void reset() {
        if (!TIMER.isActive()) return;
        TIMER.reset();
        MinecraftUi.say(Texts.tr("xpack.speedrun.reset"));
    }

    /** Nom de la touche réglée pour une action (0 pause, 1 étape, 2 fin, 3 remise à zéro, 4 départ), lisible. */
    static String keyName(Config c, int slot) {
        return key(keyOf(c, slot), -1).getDisplayName().getString();
    }

    private static String keyOf(Config c, int slot) {
        return slot == 0 ? c.speedrunToggleKey : slot == 1 ? c.speedrunSplitKey : slot == 2 ? c.speedrunStopKey : slot == 3 ? c.speedrunResetKey : c.speedrunStartKey;
    }

    static void bindKey(XPackCore core, int slot, String keyName) {
        Config c = core.config();
        if (slot == 0) c.speedrunToggleKey = keyName;
        else if (slot == 1) c.speedrunSplitKey = keyName;
        else if (slot == 2) c.speedrunStopKey = keyName;
        else if (slot == 3) c.speedrunResetKey = keyName;
        else c.speedrunStartKey = keyName;
        core.saveConfig();
    }

    /**
     * Nouvelle liste d'étapes : le meilleur temps ne se compare plus qu'à la même liste, il repart donc de zéro si
     * elle change. {@code importedBest} (fichier LiveSplit), s'il a une valeur par étape, le remplace.
     */
    static void applySteps(XPackCore core, List<String> steps, String preset, List<Long> importedBest) {
        Config c = core.config();
        boolean best = importedBest != null && !importedBest.isEmpty() && importedBest.size() == steps.size();
        if (steps.equals(c.speedrunSteps) && preset.equals(c.speedrunPreset) && !best) return;
        c.speedrunPreset = preset;
        if (!steps.equals(c.speedrunSteps)) {
            c.speedrunSteps = new ArrayList<>(steps);
            c.speedrunBest = new ArrayList<>();
            TIMER.setSteps(steps);
        }
        if (best) c.speedrunBest = new ArrayList<>(importedBest);
        TIMER.setBest(c.speedrunBest);
        core.saveConfig();
    }

    /** Exporte le chrono terminé (JSON, CSV, LiveSplit, texte) dans le dossier xpack-speedrun et copie le texte pour speedrun.com (#223). */
    static void export(Minecraft mc, XPackCore core) {
        String category = presetName(core.config().speedrunPreset);
        SpeedrunExport.Run run = SpeedrunExport.from(TIMER, category, XPackClient.minecraftVersion(), XPackClient.loaderName(), XPackClient.modpackName(), System.currentTimeMillis());
        if (run == null) {
            MinecraftUi.say(Texts.tr("xpack.speedrun.export.notFinished"));
            return;
        }
        try {
            java.nio.file.Path dir = XPackClient.gameDirectory().resolve("xpack-speedrun");
            SpeedrunExport.write(dir, run);
            mc.keyboardHandler.setClipboard(SpeedrunExport.text(run));
            net.minecraft.network.chat.MutableComponent open = Texts.tr("xpack.speedrun.export.open").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE, dir.toAbsolutePath().toString())));
            MinecraftUi.say(Texts.tr("xpack.speedrun.export.done").append(" ").append(open));
        } catch (java.io.IOException e) {
            MinecraftUi.say(Texts.tr("xpack.speedrun.export.failed", e.getMessage()));
        }
    }

    private static void finished(XPackCore core) {
        MinecraftUi.say(Texts.tr("xpack.speedrun.finished", SpeedrunTimer.format(TIMER.igtMs()), SpeedrunTimer.format(TIMER.rtaMs())));
        List<Long> best = TIMER.newBest();
        if (best != null) {
            core.config().speedrunBest = new ArrayList<>(best);
            core.saveConfig();
            TIMER.setBest(best);
            MinecraftUi.say(Texts.tr("xpack.speedrun.newBest"));
        }
    }

    /** Une ligne de l'affichage : texte, position dans le cadre (à l'échelle 100 %) et couleur. */
    private static final class HudLine {
        final Component text;
        final int x;
        final int y;
        final int color;

        HudLine(Component text, int x, int y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    /** Les lignes de l'affichage et la taille de leur cadre. */
    private static final class HudLines {
        final List<HudLine> lines = new ArrayList<>();
        int width;
        int height;

        void add(Component text, int x, int y, int color) {
            lines.add(new HudLine(text, x, y, color));
            width = Math.max(width, x + Minecraft.getInstance().font.width(text));
            height = Math.max(height, y + LINE);
        }
    }

    /** Run classée du monde (#225) : IGT en direct tant qu'elle est active, figé ensuite. Renvoie le y suivant. */
    private static int rankedLines(HudLines out, XPackCore core, int y) {
        var run = core.rankedRun();
        if (run == null) return y;
        int color = run.isActive() ? 0xFFAA00 : run.state == net.xpack.companion.core.RankedRun.State.FINISHED ? GREEN : RED;
        String time = SpeedrunTimer.format(core.rankedIgtTicks() * 50);
        out.add(Component.literal(Texts.tr("xpack.speedrun.ranked.hud").getString() + "  IGT " + time), 0, y, color);
        return y + LINE + 4;
    }

    /** Chrono, puis étapes avec l'écart au meilleur temps. */
    private static HudLines liveLines(XPackCore core) {
        HudLines out = new HudLines();
        var font = Minecraft.getInstance().font;
        int y = rankedLines(out, core, 0);
        if (!TIMER.isActive()) return out;
        int color = TIMER.state() == SpeedrunTimer.State.PAUSED ? GREY : WHITE;
        out.add(Component.literal("IGT " + SpeedrunTimer.format(TIMER.igtMs())), 0, y, color);
        out.add(Component.literal("RTA " + SpeedrunTimer.format(TIMER.rtaMs())), 0, y + LINE, GREY);
        y += LINE * 2 + 2;
        if (TIMER.state() == SpeedrunTimer.State.PAUSED) {
            out.add(Texts.tr("xpack.speedrun.pausedHud").withStyle(ChatFormatting.YELLOW), 0, y, WHITE);
            y += LINE;
        }
        if (TIMER.state() == SpeedrunTimer.State.LOST) {
            out.add(Texts.tr("xpack.speedrun.lostHud").withStyle(ChatFormatting.RED), 0, y, WHITE);
            y += LINE;
        }
        List<SpeedrunTimer.Split> splits = TIMER.splits();
        int from = Math.max(0, splits.size() - 6); // les 6 dernières étapes suffisent à l'écran
        for (int i = from; i < splits.size(); i++) {
            SpeedrunTimer.Split s = splits.get(i);
            String line = s.name + "  " + SpeedrunTimer.format(s.igtTicks * 50);
            Long delta = TIMER.deltaTicks(i);
            out.add(Component.literal(line), 0, y, GREY);
            if (delta != null) {
                String d = (delta <= 0 ? "-" : "+") + SpeedrunTimer.format(Math.abs(delta) * 50).replaceFirst("^00:", "");
                out.add(Component.literal(d), font.width(line) + 6, y, delta <= 0 ? GREEN : RED);
            }
            y += LINE;
        }
        String next = TIMER.nextStep();
        if (next != null && TIMER.state() != SpeedrunTimer.State.FINISHED) out.add(Component.literal("> " + next), 0, y, WHITE);
        return out;
    }

    /** Exemple fixe pour l'écran de placement (#221) : le même dessin que le vrai chrono. */
    private static HudLines sampleLines() {
        HudLines out = new HudLines();
        out.add(Component.literal("IGT 00:12:34.500"), 0, 0, WHITE);
        out.add(Component.literal("RTA 00:12:40.100"), 0, LINE, GREY);
        int y = LINE * 2 + 2;
        out.add(Component.literal("Nether  00:05:10.000"), 0, y, GREY);
        out.add(Component.literal("-0:03"), Minecraft.getInstance().font.width("Nether  00:05:10.000") + 6, y, GREEN);
        out.add(Component.literal("> " + Texts.tr("xpack.speedrun.step.end").getString()), 0, y + LINE, WHITE);
        return out;
    }

    /** Dessine les lignes dans leur cadre, selon le coin, la taille et le décalage de la configuration. */
    private static void draw(GuiGraphics g, Config config, HudLines hud) {
        if (hud.lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int scale = SpeedrunHud.scale(config);
        int[] at = SpeedrunHud.origin(SpeedrunHud.anchor(config), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                hud.width, hud.height, scale, SpeedrunHud.offsetX(config), SpeedrunHud.offsetY(config));
        float s = scale / 100f;
        g.pose().pushPose();
        g.pose().translate(at[0], at[1], 0);
        g.pose().scale(s, s, 1f);
        for (HudLine l : hud.lines) g.drawString(mc.font, l.text, l.x, l.y, l.color);
        g.pose().popPose();
    }

    static void render(GuiGraphics g, XPackCore core) {
        if (core == null || !core.config().speedrunEnabled || !core.config().speedrunHud) return;
        if (Minecraft.getInstance().options.hideGui) return;
        draw(g, core.config(), liveLines(core));
    }

    /** Aperçu de l'affichage pour l'écran de placement : visible même chronomètre arrêté ou HUD coupé. */
    static void renderPreview(GuiGraphics g, XPackCore core) {
        draw(g, core.config(), sampleLines());
    }

    // ---- Préréglages propres au modpack (#222)

    private static volatile List<SpeedrunCategories.Category> packCategories = new ArrayList<>();
    private static boolean packFetching;
    private static boolean packFetched;

    /** Catégories du modpack détecté, vides tant qu'elles ne sont pas lues ou si le pack n'en a pas. */
    static List<SpeedrunCategories.Category> packCategories() {
        return packCategories;
    }

    /**
     * Lit les catégories une fois par partie du jeu, à l'ouverture de l'écran du speedrun (et seulement s'il est
     * activé). Une simple lecture : {@code GET /v1/speedrun/categories}, sans jeton ni donnée du joueur. Un échec
     * (hors ligne) n'est pas définitif : l'ouverture suivante réessaie.
     */
    static void fetchPackCategories(XPackCore core, Runnable whenLoaded) {
        if (packFetched || packFetching) return;
        // Pas de requête avant l'acceptation de l'écran de consentement, ni si le joueur a coupé l'envoi.
        if (!core.config().enabled || !Boolean.TRUE.equals(core.config().consented)) return;
        String id = SpeedrunCategories.modpackId(XPackClient.modpackInfo());
        if (id == null) {
            packFetched = true; // pack non identifié : rien à demander
            return;
        }
        packFetching = true;
        core.api().speedrunCategories().whenComplete((res, err) -> Minecraft.getInstance().execute(() -> {
            packFetching = false;
            if (err != null || res == null || !res.ok()) return;
            packFetched = true;
            packCategories = SpeedrunCategories.parse(res.body(), id);
            if (whenLoaded != null) whenLoaded.run();
        }));
    }

    /** Nom d'un préréglage tel que l'écran et l'export l'affichent. */
    static String presetName(String preset) {
        if (preset == null || SpeedrunPresets.CUSTOM.equals(preset)) return Texts.tr("xpack.speedrun.preset.custom").getString();
        if (SpeedrunPresets.find(preset) != null) return Texts.tr("xpack.speedrun.preset." + preset).getString();
        SpeedrunCategories.Category c = SpeedrunCategories.find(packCategories, preset);
        if (c != null) return c.name;
        return SpeedrunCategories.isPackPreset(preset) ? Texts.tr("xpack.speedrun.preset.pack").getString() : Texts.tr("xpack.speedrun.preset.custom").getString();
    }
}
