package net.xpack.companion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.SpeedrunHud;
import net.xpack.companion.core.XPackCore;

/**
 * Place et taille de l'affichage du chrono (#221), ouvert depuis {@code /xpack speedrun}. Un exemple de
 * chrono est dessiné à l'endroit choisi, avec le même code que le vrai : ce qu'on voit ici est ce qu'on aura en jeu.
 */
public final class SpeedrunHudScreen extends Screen {
    private final Screen parent;
    private Button anchorButton;
    private Button scaleButton;
    private Button offsetXButton;
    private Button offsetYButton;

    public SpeedrunHudScreen(Screen parent) {
        super(Texts.tr("xpack.speedrun.hud.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XPackCore core = XPackClient.core();
        if (core == null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height / 2, 200, 20).build());
            return;
        }
        Config c = core.config();
        int x = width / 2 - 100;
        int y = height / 2 - 44;
        anchorButton = addRenderableWidget(Button.builder(anchorLabel(c), b -> {
            c.speedrunHudAnchor = SpeedrunHud.anchor(c).next().id;
            save(core);
        }).bounds(x, y, 200, 20).build());
        scaleButton = addRenderableWidget(Button.builder(scaleLabel(c), b -> {
            c.speedrunHudScale = SpeedrunHud.nextScale(SpeedrunHud.scale(c));
            save(core);
        }).bounds(x, y + 24, 200, 20).build());
        offsetXButton = addRenderableWidget(Button.builder(offsetXLabel(c), b -> {
            c.speedrunHudOffsetX = SpeedrunHud.nextOffset(SpeedrunHud.offsetX(c));
            save(core);
        }).bounds(x, y + 48, 200, 20).build());
        offsetYButton = addRenderableWidget(Button.builder(offsetYLabel(c), b -> {
            c.speedrunHudOffsetY = SpeedrunHud.nextOffset(SpeedrunHud.offsetY(c));
            save(core);
        }).bounds(x, y + 72, 200, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.speedrun.hud.reset"), b -> {
            SpeedrunHud.reset(c);
            save(core);
        }).bounds(x, height - 52, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 102, height - 52, 98, 20).build());
    }

    private void save(XPackCore core) {
        core.saveConfig();
        Config c = core.config();
        anchorButton.setMessage(anchorLabel(c));
        scaleButton.setMessage(scaleLabel(c));
        offsetXButton.setMessage(offsetXLabel(c));
        offsetYButton.setMessage(offsetYLabel(c));
    }

    private static Component anchorLabel(Config c) {
        return Texts.tr("xpack.speedrun.hud.anchor", Texts.tr("xpack.speedrun.hud.anchor." + SpeedrunHud.anchor(c).id));
    }

    private static Component scaleLabel(Config c) {
        return Texts.tr("xpack.speedrun.hud.scale", SpeedrunHud.scale(c) + " %");
    }

    private static Component offsetXLabel(Config c) {
        return Texts.tr("xpack.speedrun.hud.offsetX", SpeedrunHud.offsetX(c));
    }

    private static Component offsetYLabel(Config c) {
        return Texts.tr("xpack.speedrun.hud.offsetY", SpeedrunHud.offsetY(c));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Compat.background(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        XPackCore core = XPackClient.core();
        if (core == null) return;
        g.drawCenteredString(font, Texts.tr("xpack.speedrun.hud.hint"), width / 2, height / 2 - 62, 0xAAAAAA);
        SpeedrunClient.renderPreview(g, core);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
