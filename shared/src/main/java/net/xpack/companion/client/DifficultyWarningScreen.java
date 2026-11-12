package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.world.Difficulty;

/**
 * La difficulté vient de baisser (#116) : prévenir plutôt que punir. Quelques minutes plus bas ne
 * changent rien ; au-delà, le badge de difficulté du monde baisse, et ses succès rapportent moins.
 * En solo, « Remettre » rétablit la difficulté d'avant ; sinon le joueur accepte, ou est seulement
 * informé (multijoueur : la difficulté vient du serveur). Montré une seule fois par monde.
 */
public final class DifficultyWarningScreen extends Screen {
    private final String from;
    private final String to;
    private final boolean canRevert;
    private MultiLineLabel body = MultiLineLabel.EMPTY;

    public DifficultyWarningScreen(String from, String to, boolean canRevert) {
        super(Texts.tr("xpack.difficulty_warning.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        this.from = from;
        this.to = to;
        this.canRevert = canRevert;
    }

    @Override
    protected void init() {
        String key = canRevert ? "xpack.difficulty_warning.body" : "xpack.difficulty_warning.server";
        body = MultiLineLabel.create(font, Texts.tr(key, name(from), name(to)), Math.min(width - 50, 360));
        int y = height / 2 + 30;
        if (canRevert) {
            addRenderableWidget(Button.builder(Texts.tr("xpack.difficulty_warning.revert", name(from)), b -> {
                revert();
                onClose();
            }).bounds(width / 2 - 155, y, 150, 20).build());
            addRenderableWidget(Button.builder(Texts.tr("xpack.difficulty_warning.accept"), b -> onClose()).bounds(width / 2 + 5, y, 150, 20).build());
        } else {
            addRenderableWidget(Button.builder(Texts.tr("xpack.difficulty_warning.ok"), b -> onClose()).bounds(width / 2 - 75, y, 150, 20).build());
        }
    }

    /** Comme le menu Options : le serveur intégré accepte la demande du propriétaire du monde. */
    private void revert() {
        Difficulty d = Difficulty.byName(from);
        Minecraft mc = Minecraft.getInstance();
        if (d != null && mc.getConnection() != null) mc.getConnection().send(new ServerboundChangeDifficultyPacket(d));
    }

    private static net.minecraft.network.chat.MutableComponent name(String difficulty) {
        return Texts.tr("xpack.difficulty." + difficulty);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Compat.background(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        int top = height / 2 - 60;
        g.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        body.renderCentered(g, width / 2, top + 20);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
