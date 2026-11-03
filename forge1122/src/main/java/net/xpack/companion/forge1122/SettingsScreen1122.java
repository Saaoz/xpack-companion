package net.xpack.companion.forge1122;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.J8;
import net.xpack.companion.core.XPackCore;

/**
 * Réglages du mod (#40), même disposition que les autres versions : envoi des données, toasts et
 * badge, son des succès (choix, volume, écoute), liaison du compte Minecraft en cours, « monde de
 * test » et contact du support pour le monde ouvert. Ouvert par le bouton ⚙ du menu pause ou {@code /xpack settings}.
 */
public final class SettingsScreen1122 extends GuiScreen {
    private static final int ROW = 22;
    private static final List<Integer> VOLUMES = J8.listOf(0, 20, 40, 60, 80, 100);
    private static final int SENDING = 1, TOASTS = 2, BADGE = 3, SOUND = 4, VOLUME = 5, LISTEN = 6, LINK = 7, OPEN_SITE = 8, COPY = 9, CANCEL = 10, PRACTICE = 11, SUPPORT = 12, DONE = 13, PREVIEW = 14, POSITION = 15, DELAY = 16;
    /** Prochaine rareté jouée par « Écouter » : on passe de commun à légendaire, clic après clic. */
    private static int listened;
    private final GuiScreen parent;
    /** État affiché : les boutons sont reconstruits quand il change (code reçu, liaison terminée…). */
    private String shownState = "";
    private int codeY = -1;

    public SettingsScreen1122(GuiScreen parent) {
        this.parent = parent;
    }

    private static String uuid() {
        return Minecraft.getMinecraft().getSession().getProfile().getId().toString();
    }

    private static String name() {
        return Minecraft.getMinecraft().getSession().getUsername();
    }

    private static String onOff(boolean on) {
        return I18n.format(on ? "options.on" : "options.off");
    }

    private static String listenLabel() {
        return Lang.tr("xpack.settings.listen", Lang.tr("xpack.rarity." + Sounds1122.RARITIES.get(listened)));
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
        buttonList.add(new GuiButton(TOASTS, x, y, 100, 20, Lang.tr("xpack.settings.toasts") + ": " + onOff(c.toasts)));
        // Toast d'essai : voir le dessin, les textes et le son d'un succès.
        buttonList.add(new GuiButton(PREVIEW, x + 105, y, 100, 20, Lang.tr("xpack.settings.preview")));
        buttonList.add(new GuiButton(BADGE, x + 210, y, 100, 20, Lang.tr("xpack.settings.badge") + ": " + onOff(c.badge)));

        // Son des succès (#59) : choix du son, volume, et écoute d'une rareté à chaque clic.
        y += ROW;
        String sound = Sounds1122.PRESETS.contains(c.sound) ? c.sound : "carillon";
        int volume = VOLUMES.contains(c.soundVolume) ? c.soundVolume : 80;
        buttonList.add(new GuiButton(SOUND, x, y, 120, 20, Lang.tr("xpack.settings.sound") + ": " + Lang.tr("xpack.settings.sound." + sound)));
        buttonList.add(new GuiButton(VOLUME, x + 130, y, 85, 20, Lang.tr("xpack.settings.volume") + ": " + volume + " %"));
        buttonList.add(new GuiButton(LISTEN, x + 225, y, 85, 20, listenLabel()));

        // Toasts de succès : coin de l'écran et délai avant l'apparition (le bouton « Essai » les montre).
        y += ROW;
        buttonList.add(new GuiButton(POSITION, x, y, 150, 20, Lang.tr("xpack.settings.position") + ": " + Lang.tr("xpack.settings.position." + c.toastPosition("top_right"))));
        buttonList.add(new GuiButton(DELAY, x + 160, y, 150, 20, Lang.tr("xpack.settings.delay") + ": " + Lang.tr("xpack.settings.delay.value", c.toastDelay)));

        // Compte : lier / délier le compte Minecraft actuellement connecté.
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

        // Monde ouvert : le déclarer « monde de test » (rien n'est envoyé, rien n'est signalé), ou écrire au support.
        if (core.inWorld()) {
            y += ROW;
            GuiButton practice = new GuiButton(PRACTICE, x, y, 310, 20, Lang.tr(core.isPracticeWorld() ? "xpack.settings.practiceOn" : "xpack.settings.practice"));
            practice.enabled = !core.isPracticeWorld();
            buttonList.add(practice);
            y += ROW;
            GuiButton support = new GuiButton(SUPPORT, x, y, 310, 20, Lang.tr("xpack.settings.support"));
            support.enabled = core.canContactSupport();
            buttonList.add(support);
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
            case PREVIEW:
                core.previewToast(Lang.tr("xpack.toast.preview"));
                break;
            case TOASTS:
                c.toasts = !c.toasts;
                core.saveConfig();
                break;
            case BADGE:
                c.badge = !c.badge;
                core.saveConfig();
                break;
            case SOUND:
                c.sound = Sounds1122.PRESETS.get((Math.max(0, Sounds1122.PRESETS.indexOf(c.sound)) + 1) % Sounds1122.PRESETS.size());
                core.saveConfig();
                break;
            case VOLUME:
                c.soundVolume = VOLUMES.get((Math.max(0, VOLUMES.indexOf(c.soundVolume)) + 1) % VOLUMES.size());
                core.saveConfig();
                break;
            case POSITION:
                c.toastPosition = Config.TOAST_POSITIONS.get((Config.TOAST_POSITIONS.indexOf(c.toastPosition("top_right")) + 1) % Config.TOAST_POSITIONS.size());
                core.saveConfig();
                break;
            case DELAY:
                c.toastDelay = Config.TOAST_DELAYS.get((Math.max(0, Config.TOAST_DELAYS.indexOf(c.toastDelay)) + 1) % Config.TOAST_DELAYS.size());
                core.saveConfig();
                break;
            case LISTEN:
                Sounds1122.play(c, Sounds1122.RARITIES.get(listened));
                listened = (listened + 1) % Sounds1122.RARITIES.size();
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
            case SUPPORT:
                mc.displayGuiScreen(new SupportScreen1122(this));
                return;
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
            drawCenteredString(fontRenderer, TextFormatting.GRAY + Lang.tr("xpack.badge." + core.status().name().toLowerCase(java.util.Locale.ROOT)), width / 2, height - 58, 0xAAAAAA);
            drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY + Lang.tr("xpack.settings.privacy"), width / 2, height - 44, 0x777777);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
