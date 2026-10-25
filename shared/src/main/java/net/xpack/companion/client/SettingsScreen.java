package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.XPackCore;

/**
 * Réglages du mod (#40) : ouvert depuis Mod Menu (Fabric) ou la liste des mods (NeoForge).
 * Envoi des données, toasts, badge du menu pause et liaison du compte.
 */
public final class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Texts.tr("xpack.settings.title"));
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
        int x = width / 2 - 155;
        int y = 50;
        addRenderableWidget(CycleButton.onOffBuilder(Boolean.TRUE.equals(c.consented) && c.enabled)
                .create(x, y, 310, 20, Texts.tr("xpack.settings.sending"), (b, v) -> {
                    if (v) core.setConsent(true);
                    c.enabled = v;
                    core.saveConfig();
                }));
        y += 24;
        addRenderableWidget(CycleButton.onOffBuilder(c.toasts).create(x, y, 150, 20, Texts.tr("xpack.settings.toasts"), (b, v) -> {
            c.toasts = v;
            core.saveConfig();
        }));
        addRenderableWidget(CycleButton.onOffBuilder(c.badge).create(x + 160, y, 150, 20, Texts.tr("xpack.settings.badge"), (b, v) -> {
            c.badge = v;
            core.saveConfig();
        }));
        y += 24;
        Button unlink = Button.builder(Texts.tr("xpack.settings.unlink").withStyle(ChatFormatting.RED), b -> {
            core.unlink();
            b.active = false;
        }).bounds(x, y, 310, 20).build();
        unlink.active = c.token != null;
        addRenderableWidget(unlink);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        XPackCore core = XPackClient.core();
        if (core != null) {
            Config c = core.config();
            Component linked = c.token != null ? Texts.tr("xpack.settings.linked") : Texts.tr("xpack.settings.unlinked");
            g.drawCenteredString(font, linked.copy().withStyle(ChatFormatting.GRAY), width / 2, 34, 0xAAAAAA);
            g.drawCenteredString(font, Texts.tr("xpack.settings.privacy").withStyle(ChatFormatting.DARK_GRAY), width / 2, 50 + 24 * 3 + 6, 0x777777);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
