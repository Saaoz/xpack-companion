package net.xpack.companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.SpeedrunExport;
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
            if (mc.player != null && mc.level != null
                    && TIMER.watchMovement(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.level.dimension().location().toString())) {
                MinecraftUi.say(Texts.tr("xpack.speedrun.lost"));
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

    /**
     * Retour à l'écran titre : le chrono s'arrête, quel que soit le mode. Le temps reste affiché dans le prochain
     * monde jusqu'à la remise à zéro. Pas de message : le chat est vidé en quittant le monde. La run classée, elle,
     * est déjà abandonnée en quittant le monde ({@code XPackCore.onLeaveWorld}).
     */
    static void onLeaveWorld(XPackCore core) {
        if (!core.config().speedrunEnabled || (!TIMER.isRunning() && TIMER.state() != SpeedrunTimer.State.PAUSED)) return;
        TIMER.stop();
        List<Long> best = TIMER.newBest();
        if (best != null) {
            core.config().speedrunBest = new ArrayList<>(best);
            core.saveConfig();
            TIMER.setBest(best);
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
        String preset = core.config().speedrunPreset;
        String category = SpeedrunPresets.find(preset) == null ? Texts.tr("xpack.speedrun.preset.custom").getString() : Texts.tr("xpack.speedrun.preset." + preset).getString();
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

    /** Run classée du monde (#225) : IGT en direct tant qu'elle est active, figé ensuite. Renvoie le y suivant. */
    private static int renderRanked(GuiGraphics g, XPackCore core, int x, int y) {
        var run = core.rankedRun();
        if (run == null) return y;
        var font = Minecraft.getInstance().font;
        int color = run.isActive() ? 0xFFAA00 : run.state == net.xpack.companion.core.RankedRun.State.FINISHED ? GREEN : RED;
        String time = SpeedrunTimer.format(core.rankedIgtTicks() * 50);
        g.drawString(font, Texts.tr("xpack.speedrun.ranked.hud").getString() + "  IGT " + time, x, y, color);
        return y + LINE + 4;
    }

    /** Chrono, puis étapes avec l'écart au meilleur temps. En haut à gauche, les toasts étant à droite. */
    static void render(GuiGraphics g, XPackCore core) {
        if (core == null || !core.config().speedrunEnabled || !core.config().speedrunHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int x = 6;
        int y = 6;
        var font = mc.font;
        y = renderRanked(g, core, x, y);
        if (!TIMER.isActive()) return;
        int color = TIMER.state() == SpeedrunTimer.State.PAUSED ? GREY : WHITE;
        g.drawString(font, "IGT " + SpeedrunTimer.format(TIMER.igtMs()), x, y, color);
        g.drawString(font, "RTA " + SpeedrunTimer.format(TIMER.rtaMs()), x, y + LINE, GREY);
        y += LINE * 2 + 2;
        if (TIMER.state() == SpeedrunTimer.State.PAUSED) {
            g.drawString(font, Texts.tr("xpack.speedrun.pausedHud").withStyle(ChatFormatting.YELLOW), x, y, WHITE);
            y += LINE;
        }
        if (TIMER.state() == SpeedrunTimer.State.LOST) {
            g.drawString(font, Texts.tr("xpack.speedrun.lostHud").withStyle(ChatFormatting.RED), x, y, WHITE);
            y += LINE;
        }
        List<SpeedrunTimer.Split> splits = TIMER.splits();
        int from = Math.max(0, splits.size() - 6); // les 6 dernières étapes suffisent à l'écran
        for (int i = from; i < splits.size(); i++) {
            SpeedrunTimer.Split s = splits.get(i);
            String line = s.name + "  " + SpeedrunTimer.format(s.igtTicks * 50);
            Long delta = TIMER.deltaTicks(i);
            g.drawString(font, line, x, y, GREY);
            if (delta != null) {
                String d = (delta <= 0 ? "-" : "+") + SpeedrunTimer.format(Math.abs(delta) * 50).replaceFirst("^00:", "");
                g.drawString(font, d, x + font.width(line) + 6, y, delta <= 0 ? GREEN : RED);
            }
            y += LINE;
        }
        String next = TIMER.nextStep();
        if (next != null && TIMER.state() != SpeedrunTimer.State.FINISHED) g.drawString(font, "> " + next, x, y, WHITE);
    }
}
