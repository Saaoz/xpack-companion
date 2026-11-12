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
import net.xpack.companion.core.LssImport;
import net.xpack.companion.core.SpeedrunCategories;
import net.xpack.companion.core.SpeedrunMode;
import net.xpack.companion.core.SpeedrunPresets;
import net.xpack.companion.core.SpeedrunTimer;
import net.xpack.companion.core.XPackCore;

/**
 * Écran du speedrun libre (#221), ouvert par {@code /xpack speedrun} : le chrono et ses boutons, les touches
 * (un clic puis la touche voulue), l'affichage à l'écran et le nom des étapes. Les noms s'enregistrent à la
 * fermeture ; un chrono libre n'est jamais vérifié.
 */
public final class SpeedrunScreen extends Screen {
    private static final int MAX_STEPS = 7;
    private static final int GAP = 22;
    private static final int STEP_GAP = 20;
    private static final String[] KEY_LABELS = {"xpack.speedrun.key.toggle", "xpack.speedrun.key.split", "xpack.speedrun.key.stop", "xpack.speedrun.key.reset", "xpack.speedrun.key.start"};

    private final Screen parent;
    private final List<EditBox> stepBoxes = new ArrayList<>();
    private final Button[] keyButtons = new Button[5];
    private Button toggleButton;
    private Button enableButton;
    private Button doneButton;
    private Button presetButton;
    private Button exportButton;
    /** Meilleur temps lu dans un fichier LiveSplit déposé, appliqué à la fermeture avec les étapes. */
    private List<Long> pendingBest;
    private Component importMessage;
    private String preset = SpeedrunPresets.CUSTOM;
    /** Vrai pendant le remplissage des cases par un préréglage : ce n'est pas une saisie du joueur. */
    private boolean filling;
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
        boolean enabled = core.config().speedrunEnabled;
        enableButton = addRenderableWidget(Button.builder(enableLabel(enabled), b -> {
            collectSteps(core);
            core.config().speedrunEnabled = !core.config().speedrunEnabled;
            core.saveConfig();
            rebuildWidgets();
        }).bounds(left, 24, 150, 16).tooltip(net.minecraft.client.gui.components.Tooltip.create(Texts.tr("xpack.speedrun.enable.hint"))).build());
        // Départ et fin : le menu décide quelles touches sont utiles. La run n'est classée qu'en tout automatique.
        SpeedrunMode mode = SpeedrunMode.parse(core.config().speedrunMode);
        addRenderableWidget(Button.builder(modeLabel(mode), b -> {
            collectSteps(core); // les cases saisies sont gardées avant de reconstruire l'écran
            core.config().speedrunMode = mode.next().id;
            core.saveConfig();
            rebuildWidgets();
        }).bounds(left, 72, 100, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Texts.tr("xpack.speedrun.mode.hint." + mode.id))).build());
        boolean hud = core.config().speedrunHud;
        addRenderableWidget(Button.builder(Component.literal("HUD").withStyle(hud ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED), b -> {
            core.config().speedrunHud = !core.config().speedrunHud;
            core.saveConfig();
            b.setMessage(Component.literal("HUD").withStyle(core.config().speedrunHud ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
        }).bounds(left + 104, 72, 46, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Texts.tr("xpack.speedrun.hud"))).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(timer), b -> {
            SpeedrunClient.toggle(minecraft);
            b.setMessage(toggleLabel(timer));
        }).bounds(left, 96, 37, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.split.button"), b -> SpeedrunClient.split(core)).bounds(left + 38, 96, 37, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.stop.button"), b -> SpeedrunClient.stop(core)).bounds(left + 76, 96, 37, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.reset.button"), b -> SpeedrunClient.reset()).bounds(left + 114, 96, 36, 20).build());

        // Touches : départ seulement s'il est manuel, fin seulement si elle est manuelle ; pause, étape et remise à zéro toujours.
        java.util.Arrays.fill(keyButtons, null);
        int y = 134;
        for (int slot : new int[] {4, 0, 1, 2, 3}) {
            if (slot == 4 && mode.startAuto()) continue;
            if (slot == 2 && mode.endAuto()) continue;
            final int s = slot;
            keyButtons[slot] = addRenderableWidget(Button.builder(keyLabel(core, slot), b -> {
                capturing = s;
                refreshKeyLabels(core);
            }).bounds(left, y, 150, 18).build());
            y += 20;
        }

        // Préréglage, puis étapes : les cases vides sont ignorées, l'ordre est celui de la liste.
        // Un préréglage de modpack reste choisi même si ses catégories ne sont pas (encore) lues : hors ligne, on ne l'efface pas.
        String saved = core.config().speedrunPreset;
        preset = SpeedrunPresets.ids().contains(saved) || SpeedrunCategories.isPackPreset(saved) ? saved : SpeedrunPresets.CUSTOM;
        presetButton = addRenderableWidget(Button.builder(presetLabel(), b -> cyclePreset()).bounds(right, 36, 150, 20).build());
        List<String> steps = core.config().speedrunSteps == null ? new ArrayList<String>() : core.config().speedrunSteps;
        stepBoxes.clear();
        int sy = 80;
        for (int i = 0; i < MAX_STEPS; i++) {
            EditBox box = new EditBox(font, right, sy, 150, 18, Texts.tr("xpack.speedrun.step", i + 1));
            box.setMaxLength(32);
            box.setHint(Texts.tr("xpack.speedrun.step", i + 1));
            if (i < steps.size()) box.setValue(steps.get(i));
            box.setResponder(v -> {
                if (!filling && !SpeedrunPresets.CUSTOM.equals(preset)) {
                    preset = SpeedrunPresets.CUSTOM; // une case modifiée à la main : plus un préréglage
                    presetButton.setMessage(presetLabel());
                }
            });
            stepBoxes.add(addRenderableWidget(box));
            sy += STEP_GAP;
        }
        exportButton = addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.export.button"), b -> SpeedrunClient.export(minecraft, core)).bounds(right, 221, 150, 18).build());
        exportButton.active = timer.state() == SpeedrunTimer.State.FINISHED;
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.hud.open"), b -> {
            collectSteps(core); // les étapes saisies sont gardées avant de changer d'écran
            minecraft.setScreen(new SpeedrunHudScreen(this));
        }).bounds(width / 2 - 100, height - 28, 98, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 + 2, height - 28, 98, 20).build());
        if (enabled) SpeedrunClient.fetchPackCategories(core, null); // catégories du modpack (#222), lecture seule
        // Désactivé : tout est visible mais grisé, sauf le bouton d'activation et « Terminé ».
        if (!enabled) {
            for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w != enableButton && w != doneButton) w.active = false;
            }
        }
    }

    private static Component enableLabel(boolean enabled) {
        return Texts.tr(enabled ? "xpack.speedrun.enable.on" : "xpack.speedrun.enable.off");
    }

    private Component presetLabel() {
        return Texts.tr("xpack.speedrun.preset", SpeedrunClient.presetName(preset));
    }

    /**
     * Préréglage suivant : ceux du mod, puis les catégories du modpack détecté (#222). Ses étapes remplissent les
     * cases (le nom reste modifiable ensuite).
     */
    private void cyclePreset() {
        List<SpeedrunCategories.Category> cats = SpeedrunClient.packCategories();
        List<String> ids = SpeedrunCategories.presetIds(cats);
        preset = ids.get((ids.indexOf(preset) + 1) % ids.size());
        SpeedrunPresets.Preset p = SpeedrunPresets.find(preset);
        SpeedrunCategories.Category cat = SpeedrunCategories.find(cats, preset);
        List<String> names = new ArrayList<>();
        if (p != null) for (SpeedrunPresets.Step s : p.steps) names.add(Texts.tr("xpack.speedrun.step." + s.id).getString());
        else if (cat != null) names.addAll(cat.steps());
        if (p != null || cat != null) {
            filling = true;
            for (int i = 0; i < stepBoxes.size(); i++) stepBoxes.get(i).setValue(i < names.size() ? names.get(i) : "");
            filling = false;
        }
        presetButton.setMessage(presetLabel());
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

    /** Un fichier LiveSplit (.lss) déposé sur l'écran : ses segments deviennent les étapes (#224). */
    @Override
    public void onFilesDrop(List<java.nio.file.Path> files) {
        for (java.nio.file.Path file : files) {
            if (!file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".lss")) continue;
            try {
                LssImport.Result r = LssImport.read(file);
                List<String> names = r.steps.size() > MAX_STEPS ? r.steps.subList(0, MAX_STEPS) : r.steps;
                filling = true;
                for (int i = 0; i < stepBoxes.size(); i++) stepBoxes.get(i).setValue(i < names.size() ? names.get(i) : "");
                filling = false;
                preset = SpeedrunPresets.CUSTOM;
                if (presetButton != null) presetButton.setMessage(presetLabel());
                pendingBest = r.steps.size() <= MAX_STEPS ? r.bestTicks : null; // un meilleur temps tronqué ne se compare à rien
                importMessage = r.steps.size() > MAX_STEPS
                        ? Texts.tr("xpack.speedrun.import.truncated", r.steps.size(), MAX_STEPS)
                        : Texts.tr(pendingBest != null && !pendingBest.isEmpty() ? "xpack.speedrun.import.doneBest" : "xpack.speedrun.import.done", names.size());
            } catch (LssImport.LssException e) {
                importMessage = Texts.tr("xpack.speedrun.import.error." + e.getMessage());
            } catch (java.io.IOException e) {
                importMessage = Texts.tr("xpack.speedrun.import.error.io");
            }
            return;
        }
    }

    @Override
    public void tick() {
        if (toggleButton != null) toggleButton.setMessage(toggleLabel(SpeedrunClient.timer()));
        if (exportButton != null) exportButton.active = SpeedrunClient.timer().state() == SpeedrunTimer.State.FINISHED;
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
        g.drawString(font, Texts.tr("xpack.speedrun.mode"), left, 62, 0xAAAAAA);
        g.drawString(font, "IGT " + SpeedrunTimer.format(timer.igtMs()), left, 45, 0xFFFFFF);
        g.drawString(font, "RTA " + SpeedrunTimer.format(timer.rtaMs()), left + 80, 45, 0xAAAAAA);
        g.drawString(font, Texts.tr("xpack.speedrun.keys"), left, 122, 0xAAAAAA);
        if (!core.config().speedrunEnabled) g.drawCenteredString(font, Texts.tr("xpack.speedrun.disabledHint"), width / 2, height - 42, 0xFFAA00);
        else         if (importMessage != null) g.drawCenteredString(font, importMessage, width / 2, height - 42, 0xFFFFFF);
        g.drawString(font, Texts.tr("xpack.speedrun.steps"), right, 66, 0xAAAAAA);
        if (timer.state() == SpeedrunTimer.State.LOST) g.drawString(font, Texts.tr("xpack.speedrun.lostHud").withStyle(net.minecraft.ChatFormatting.RED), left, 54, 0xFF5555);
    }

    @Override
    public void removed() {
        XPackCore core = XPackClient.core();
        if (core == null) return;
        collectSteps(core);
    }

    /** Enregistre les noms d'étapes saisis (cases vides ignorées). */
    private void collectSteps(XPackCore core) {
        List<String> steps = new ArrayList<>();
        for (EditBox box : stepBoxes) {
            String v = box.getValue().strip();
            if (!v.isEmpty()) steps.add(v);
        }
        SpeedrunClient.applySteps(core, steps, preset, pendingBest);
    }

    private static Component modeLabel(SpeedrunMode mode) {
        return Texts.tr("xpack.speedrun.mode." + mode.id);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
