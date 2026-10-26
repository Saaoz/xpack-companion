package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.XPackCore;

/**
 * Réglages du mod (#40). Ouvert par le bouton ⚙ du menu pause, par Mod Menu (Fabric) ou par la
 * liste des mods (NeoForge). Envoi des données, toasts, badge, liaison du compte Minecraft en
 * cours (aussi depuis le menu principal) et « monde de test » pour le monde ouvert.
 */
public final class SettingsScreen extends Screen {
    private static final int ROW = 24;
    private final Screen parent;
    /** État affiché : les boutons sont reconstruits quand il change (code reçu, liaison terminée…). */
    private String shownState = "";
    private int codeY = -1;

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
        shownState = state(core);
        Config c = core.config();
        User user = minecraft.getUser();
        String uuid = user.getProfileId().toString();
        int x = width / 2 - 155;
        int y = 50;
        addRenderableWidget(CycleButton.onOffBuilder(Boolean.TRUE.equals(c.consented) && c.enabled)
                .create(x, y, 310, 20, Texts.tr("xpack.settings.sending"), (b, v) -> {
                    if (v) core.setConsent(true);
                    c.enabled = v;
                    core.saveConfig();
                }));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(c.toasts).create(x, y, 150, 20, Texts.tr("xpack.settings.toasts"), (b, v) -> {
            c.toasts = v;
            core.saveConfig();
        }));
        addRenderableWidget(CycleButton.onOffBuilder(c.badge).create(x + 160, y, 150, 20, Texts.tr("xpack.settings.badge"), (b, v) -> {
            c.badge = v;
            core.saveConfig();
        }));

        // Compte : lier / délier le compte Minecraft actuellement connecté.
        y += ROW;
        XPackCore.LinkState link = core.linkState();
        codeY = -1;
        if (core.isLinked(uuid)) {
            addRenderableWidget(Button.builder(Texts.tr("xpack.settings.unlink", user.getName()).withStyle(ChatFormatting.RED), b -> {
                core.unlink(uuid);
                rebuildWidgets();
            }).bounds(x, y, 310, 20).build());
        } else if (link != null && link.userCode() != null) {
            addRenderableWidget(Button.builder(Texts.tr("xpack.settings.openSite"), b -> ConfirmLinkScreen.confirmLinkNow(this, link.url()))
                    .bounds(x, y, 150, 20).build());
            addRenderableWidget(Button.builder(Texts.tr("xpack.settings.copyCode"), b -> minecraft.keyboardHandler.setClipboard(link.userCode()))
                    .bounds(x + 160, y, 150, 20).build());
            y += ROW;
            codeY = y + 6;
            y += ROW;
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                core.cancelLinking();
                rebuildWidgets();
            }).bounds(x, y, 310, 20).build());
        } else {
            Button start = Button.builder(Texts.tr(link == null ? "xpack.settings.link" : link.failed() ? "xpack.settings.linkRetry" : "xpack.settings.linkWaiting"), b -> {
                core.startLinking(uuid, user.getName());
                rebuildWidgets();
            }).bounds(x, y, 310, 20).build();
            start.active = link == null || link.failed();
            addRenderableWidget(start);
        }

        // Monde ouvert : le déclarer « monde de test » (rien n'est envoyé, rien n'est signalé).
        if (core.inWorld()) {
            y += ROW;
            Button practice = Button.builder(Texts.tr(core.isPracticeWorld() ? "xpack.settings.practiceOn" : "xpack.settings.practice"), b ->
                    minecraft.setScreen(new ConfirmScreen(ok -> {
                        if (ok) core.markWorldPractice();
                        minecraft.setScreen(this);
                    }, Texts.tr("xpack.settings.practiceConfirmTitle"), Texts.tr("xpack.settings.practiceConfirm")))).bounds(x, y, 310, 20).build();
            practice.active = !core.isPracticeWorld();
            addRenderableWidget(practice);
            y += ROW;
            Button support = Button.builder(Texts.tr("xpack.settings.support"), b -> minecraft.setScreen(new SupportScreen(this))).bounds(x, y, 310, 20).build();
            support.active = core.canContactSupport();
            addRenderableWidget(support);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private String state(XPackCore core) {
        XPackCore.LinkState l = core.linkState();
        return core.isLinked(minecraft.getUser().getProfileId().toString()) + "|" + (l == null ? "-" : l.userCode() + l.failed())
                + "|" + core.inWorld() + core.isPracticeWorld();
    }

    @Override
    public void tick() {
        XPackCore core = XPackClient.core();
        if (core != null && !state(core).equals(shownState)) rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        XPackCore core = XPackClient.core();
        if (core == null) return;
        String name = minecraft.getUser().getName();
        Component linked = core.isLinked(minecraft.getUser().getProfileId().toString())
                ? Texts.tr("xpack.settings.linked", name)
                : Texts.tr("xpack.settings.unlinked", name);
        g.drawCenteredString(font, linked.copy().withStyle(ChatFormatting.GRAY), width / 2, 34, 0xAAAAAA);
        XPackCore.LinkState link = core.linkState();
        if (codeY >= 0 && link != null && link.userCode() != null) {
            Component code = Texts.tr("xpack.settings.code", link.userCode()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            g.drawCenteredString(font, code, width / 2, codeY, 0xFFFFFF);
        }
        g.drawCenteredString(font, Texts.tr("xpack.settings.privacy").withStyle(ChatFormatting.DARK_GRAY), width / 2, height - 44, 0x777777);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
