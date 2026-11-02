package net.xpack.companion.forge1122;

import java.io.IOException;
import java.net.URI;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.XPackCore;

/**
 * Réglages du mod en 1.12.2 : envoi des données, toasts, liaison du compte Minecraft (seul endroit
 * où l'on peut lier) et « monde de test ». Ouvert par le bouton XPack du menu pause ou {@code /xpack settings}.
 */
public final class SettingsScreen1122 extends GuiScreen {
    private static final int ROW = 24;
    private static final int SENDING = 1, TOASTS = 2, LINK = 3, OPEN_SITE = 4, COPY = 5, CANCEL = 6, PRACTICE = 7, DONE = 8;
    private final GuiScreen parent;
    /** État affiché : les boutons sont reconstruits quand il change (code reçu, liaison terminée…). */
    private String shownState = "";
    private int codeY = -1;

    public SettingsScreen1122(GuiScreen parent) {
        this.parent = parent;
    }

    private static String uuid() {
        return net.minecraft.client.Minecraft.getMinecraft().getSession().getProfile().getId().toString();
    }

    private static String name() {
        return net.minecraft.client.Minecraft.getMinecraft().getSession().getUsername();
    }

    private static String onOff(boolean on) {
        return I18n.format(on ? "options.on" : "options.off");
    }

    @Override
    public void initGui() {
        buttonList.clear();
        XPackCore core = Client1122.core();
        if (core == null) {
            buttonList.add(new GuiButton(DONE, width / 2 - 100, height / 2, 200, 20, I18n.format("gui.done")));
            return;
        }
        shownState = state(core);
        Config c = core.config();
        int x = width / 2 - 155;
        int y = 50;
        buttonList.add(new GuiButton(SENDING, x, y, 310, 20, Lang.tr("xpack.settings.sending") + ": " + onOff(Boolean.TRUE.equals(c.consented) && c.enabled)));
        y += ROW;
        buttonList.add(new GuiButton(TOASTS, x, y, 310, 20, Lang.tr("xpack.settings.toasts") + ": " + onOff(c.toasts)));

        y += ROW;
        XPackCore.LinkState link = core.linkState();
        codeY = -1;
        if (core.isLinked(uuid())) {
            buttonList.add(new GuiButton(LINK, x, y, 310, 20, TextFormatting.RED + Lang.tr("xpack.settings.unlink", name())));
        } else if (link != null && link.userCode() != null) {
            buttonList.add(new GuiButton(OPEN_SITE, x, y, 150, 20, Lang.tr("xpack.settings.openSite")));
            buttonList.add(new GuiButton(COPY, x + 160, y, 150, 20, Lang.tr("xpack.settings.copyCode")));
            y += ROW;
            codeY = y + 6;
            y += ROW;
            buttonList.add(new GuiButton(CANCEL, x, y, 310, 20, I18n.format("gui.cancel")));
        } else {
            GuiButton start = new GuiButton(LINK, x, y, 310, 20, Lang.tr(link == null ? "xpack.settings.link" : link.failed() ? "xpack.settings.linkRetry" : "xpack.settings.linkWaiting"));
            start.enabled = link == null || link.failed();
            buttonList.add(start);
        }

        if (core.inWorld()) {
            y += ROW;
            GuiButton practice = new GuiButton(PRACTICE, x, y, 310, 20, Lang.tr(core.isPracticeWorld() ? "xpack.settings.practiceOn" : "xpack.settings.practice"));
            practice.enabled = !core.isPracticeWorld();
            buttonList.add(practice);
        }
        buttonList.add(new GuiButton(DONE, width / 2 - 100, height - 28, 200, 20, I18n.format("gui.done")));
    }

    private String state(XPackCore core) {
        XPackCore.LinkState l = core.linkState();
        return core.isLinked(uuid()) + "|" + (l == null ? "-" : l.userCode() + l.failed()) + "|" + core.inWorld() + core.isPracticeWorld();
    }

    @Override
    public void updateScreen() {
        XPackCore core = Client1122.core();
        if (core != null && !state(core).equals(shownState)) initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        XPackCore core = Client1122.core();
        if (core == null || button.id == DONE) {
            mc.displayGuiScreen(parent);
            return;
        }
        Config c = core.config();
        XPackCore.LinkState link = core.linkState();
        switch (button.id) {
            case SENDING:
                boolean on = !(Boolean.TRUE.equals(c.consented) && c.enabled);
                if (on) core.setConsent(true);
                c.enabled = on;
                core.saveConfig();
                break;
            case TOASTS:
                c.toasts = !c.toasts;
                core.saveConfig();
                break;
            case LINK:
                if (core.isLinked(uuid())) core.unlink(uuid());
                else core.startLinking(uuid(), name());
                break;
            case OPEN_SITE:
                if (link != null && link.url() != null) openLink(link.url());
                break;
            case COPY:
                if (link != null && link.userCode() != null) setClipboardString(link.userCode());
                break;
            case CANCEL:
                core.cancelLinking();
                break;
            case PRACTICE:
                core.markWorldPractice();
                break;
            default:
                break;
        }
        initGui();
    }

    /** Navigateur du système, par réflexion comme le jeu : java.awt peut manquer sur certaines installations. */
    private static void openLink(String url) {
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object d = desktop.getMethod("getDesktop").invoke(null);
            desktop.getMethod("browse", URI.class).invoke(d, new URI(url));
        } catch (Throwable ignored) {
            setClipboardString(url); // à défaut, le lien est copié
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, Lang.tr("xpack.settings.title"), width / 2, 20, 0xFFFFFF);
        XPackCore core = Client1122.core();
        if (core != null) {
            String linked = Lang.tr(core.isLinked(uuid()) ? "xpack.settings.linked" : "xpack.settings.unlinked", name());
            drawCenteredString(fontRenderer, TextFormatting.GRAY + linked, width / 2, 34, 0xAAAAAA);
            XPackCore.LinkState link = core.linkState();
            if (codeY >= 0 && link != null && link.userCode() != null) {
                drawCenteredString(fontRenderer, TextFormatting.GREEN + "" + TextFormatting.BOLD + Lang.tr("xpack.settings.code", link.userCode()), width / 2, codeY, 0xFFFFFF);
            }
            drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY + Lang.tr("xpack.settings.privacy"), width / 2, height - 44, 0x777777);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
