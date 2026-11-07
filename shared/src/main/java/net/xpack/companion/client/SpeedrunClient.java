package net.xpack.companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.xpack.companion.core.Config;
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
    private static final boolean[] HELD = new boolean[4];
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
        if (!inWorld) return;
        if (!mc.isPaused()) {
            TIMER.gameTick();
            // Pause du chrono (touche) : le jeu tourne, donc le joueur ne doit pas bouger (#220). Échap en solo fige
            // la partie : aucun déplacement possible, rien à surveiller.
            if (mc.player != null && mc.level != null
                    && TIMER.watchMovement(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.level.dimension().location().toString())) {
                MinecraftUi.say(Texts.tr("xpack.speedrun.lost"));
            }
        }
        if (mc.screen != null) {
            java.util.Arrays.fill(HELD, true); // une touche tenue en sortant d'un écran ne déclenche rien
            return;
        }
        Config c = core.config();
        if (pressedOnce(mc, 0, key(c.speedrunToggleKey, InputConstants.KEY_NUMPAD1))) toggle(mc);
        if (pressedOnce(mc, 1, key(c.speedrunSplitKey, InputConstants.KEY_NUMPAD2))) split(core);
        if (pressedOnce(mc, 2, key(c.speedrunStopKey, InputConstants.KEY_NUMPAD3))) stop(core);
        if (pressedOnce(mc, 3, key(c.speedrunResetKey, InputConstants.KEY_NUMPAD4))) reset();
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

    /** Nom de la touche réglée pour une action (0 démarrer, 1 étape, 2 arrêter, 3 remise à zéro), lisible. */
    static String keyName(Config c, int slot) {
        return key(keyOf(c, slot), -1).getDisplayName().getString();
    }

    private static String keyOf(Config c, int slot) {
        return slot == 0 ? c.speedrunToggleKey : slot == 1 ? c.speedrunSplitKey : slot == 2 ? c.speedrunStopKey : c.speedrunResetKey;
    }

    static void bindKey(XPackCore core, int slot, String keyName) {
        Config c = core.config();
        if (slot == 0) c.speedrunToggleKey = keyName;
        else if (slot == 1) c.speedrunSplitKey = keyName;
        else if (slot == 2) c.speedrunStopKey = keyName;
        else c.speedrunResetKey = keyName;
        core.saveConfig();
    }

    /** Nouvelle liste d'étapes : le meilleur temps ne se compare plus qu'à la même liste, il repart donc de zéro si elle change. */
    static void applySteps(XPackCore core, List<String> steps, String preset) {
        Config c = core.config();
        if (steps.equals(c.speedrunSteps) && preset.equals(c.speedrunPreset)) return;
        c.speedrunPreset = preset;
        if (steps.equals(c.speedrunSteps)) {
            core.saveConfig();
            return;
        }
        c.speedrunSteps = new ArrayList<>(steps);
        c.speedrunBest = new ArrayList<>();
        TIMER.setSteps(steps);
        TIMER.setBest(c.speedrunBest);
        core.saveConfig();
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

    /** Chrono, puis étapes avec l'écart au meilleur temps. En haut à gauche, les toasts étant à droite. */
    static void render(GuiGraphics g, XPackCore core) {
        if (core == null || !core.config().speedrunHud || !TIMER.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int x = 6;
        int y = 6;
        var font = mc.font;
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
