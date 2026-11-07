package net.xpack.companion.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.SpeedrunTimer;
import net.xpack.companion.core.XPackCore;

/**
 * Écran du speedrun libre (#221), ouvert par {@code /xpack speedrun} : le chrono et ses boutons, les touches
 * (un clic puis la touche voulue), l'affichage à l'écran et le nom des étapes. Les noms s'enregistrent à la
 * fermeture ; un chrono libre n'est jamais vérifié.
 */
public final class SpeedrunScreen extends Screen {
    private static final int MAX_STEPS = 8;
    private static final int GAP = 22;
    private static final String[] KEY_LABELS = {"xpack.speedrun.key.toggle", "xpack.speedrun.key.split", "xpack.speedrun.key.stop", "xpack.speedrun.key.reset"};

    private final Screen parent;
    private final List<EditBox> stepBoxes = new ArrayList<>();
    private final Button[] keyButtons = new Button[4];
    private Button toggleButton;
    /** Touche en cours de réglage (0 à 3), ou -1. */
    private int capturing = -1;

    public SpeedrunScreen(Screen parent) {
        super(Texts.tr("xpack.speedrun.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XPackCore core = XPackClient.core();
        if (core == null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height / 2, 200, 20).build());
            return;
        }
        SpeedrunClient.load(core);
        SpeedrunTimer timer = SpeedrunClient.timer();
        int left = width / 2 - 160;
        int right = width / 2 + 10;
        int y = 62;

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(timer), b -> {
            SpeedrunClient.toggle(minecraft);
            b.setMessage(toggleLabel(timer));
        }).bounds(left, y, 73, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.split.button"), b -> SpeedrunClient.split(core)).bounds(left + 77, y, 73, 20).build());
        y += GAP;
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.stop.button"), b -> SpeedrunClient.stop(core)).bounds(left, y, 73, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.reset.button"), b -> SpeedrunClient.reset()).bounds(left + 77, y, 73, 20).build());

        y += GAP + 14; // place pour le titre « Touches »
        for (int slot = 0; slot < 4; slot++) {
            final int s = slot;
            keyButtons[slot] = addRenderableWidget(Button.builder(keyLabel(core, slot), b -> {
                capturing = s;
                refreshKeyLabels(core);
            }).bounds(left, y, 150, 20).build());
            y += GAP;
        }
        addRenderableWidget(CycleButton.onOffBuilder(core.config().speedrunHud).create(left, y, 150, 20, Texts.tr("xpack.speedrun.hud"), (b, v) -> {
            core.config().speedrunHud = v;
            core.saveConfig();
        }));

        // Étapes : les cases vides sont ignorées, l'ordre est celui de la liste.
        List<String> steps = core.config().speedrunSteps == null ? new ArrayList<String>() : core.config().speedrunSteps;
        stepBoxes.clear();
        int sy = 62;
        for (int i = 0; i < MAX_STEPS; i++) {
            EditBox box = new EditBox(font, right, sy, 150, 20, Texts.tr("xpack.speedrun.step", i + 1));
            box.setMaxLength(32);
            box.setHint(Texts.tr("xpack.speedrun.step", i + 1));
            if (i < steps.size()) box.setValue(steps.get(i));
            stepBoxes.add(addRenderableWidget(box));
            sy += GAP;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private static Component toggleLabel(SpeedrunTimer timer) {
        return Texts.tr(timer.state() == SpeedrunTimer.State.RUNNING ? "xpack.speedrun.pause.button" : timer.state() == SpeedrunTimer.State.PAUSED ? "xpack.speedrun.resume.button" : "xpack.speedrun.start.button");
    }

    private Component keyLabel(XPackCore core, int slot) {
        Component key = capturing == slot ? Texts.tr("xpack.speedrun.key.press") : Component.literal(SpeedrunClient.keyName(core.config(), slot));
        return Texts.tr(KEY_LABELS[slot], key);
    }

    private void refreshKeyLabels(XPackCore core) {
        for (int i = 0; i < keyButtons.length; i++) if (keyButtons[i] != null) keyButtons[i].setMessage(keyLabel(core, i));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        XPackCore core = XPackClient.core();
        if (capturing >= 0 && core != null) {
            if (keyCode != InputConstants.KEY_ESCAPE) SpeedrunClient.bindKey(core, capturing, InputConstants.getKey(keyCode, scanCode).getName());
            capturing = -1;
            refreshKeyLabels(core);
            return true; // Échap annule le réglage sans fermer l'écran
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (toggleButton != null) toggleButton.setMessage(toggleLabel(SpeedrunClient.timer()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Compat.background(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        XPackCore core = XPackClient.core();
        if (core == null) return;
        SpeedrunTimer timer = SpeedrunClient.timer();
        int left = width / 2 - 160;
        int right = width / 2 + 10;
        g.drawCenteredString(font, Texts.tr("xpack.speedrun.free").withStyle(net.minecraft.ChatFormatting.GRAY), width / 2, 28, 0xAAAAAA);
        g.drawString(font, "IGT " + SpeedrunTimer.format(timer.igtMs()), left, 40, 0xFFFFFF);
        g.drawString(font, "RTA " + SpeedrunTimer.format(timer.rtaMs()), left + 80, 40, 0xAAAAAA);
        g.drawString(font, Texts.tr("xpack.speedrun.keys"), left, 62 + GAP * 2 + 2, 0xAAAAAA);
        g.drawString(font, Texts.tr("xpack.speedrun.steps"), right, 50, 0xAAAAAA);
        if (timer.state() == SpeedrunTimer.State.LOST) g.drawString(font, Texts.tr("xpack.speedrun.lostHud").withStyle(net.minecraft.ChatFormatting.RED), left + 160, 40, 0xFF5555);
    }

    @Override
    public void removed() {
        XPackCore core = XPackClient.core();
        if (core == null) return;
        List<String> steps = new ArrayList<>();
        for (EditBox box : stepBoxes) {
            String v = box.getValue().strip();
            if (!v.isEmpty()) steps.add(v);
        }
        SpeedrunClient.applySteps(core, steps);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
