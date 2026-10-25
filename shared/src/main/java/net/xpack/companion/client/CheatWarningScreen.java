package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Alerte avant une commande de triche (#44) : même texte et même logique que la maquette de
 * l'Espace créateurs du site. « Annuler » ne fait rien ; « Continuer » signale le monde puis
 * envoie la commande.
 */
public final class CheatWarningScreen extends Screen {
    private final String command;
    private final Runnable onContinue;
    private MultiLineLabel body = MultiLineLabel.EMPTY;

    public CheatWarningScreen(String command, Runnable onContinue) {
        super(Texts.tr("xpack.warning.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        this.command = command.startsWith("/") ? command : "/" + command;
        this.onContinue = onContinue;
    }

    @Override
    protected void init() {
        body = MultiLineLabel.create(font, Texts.tr("xpack.warning.body"), Math.min(width - 50, 360));
        int y = height / 2 + 30;
        addRenderableWidget(Button.builder(Texts.tr("xpack.warning.cancel"), b -> onClose()).bounds(width / 2 - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.warning.continue").withStyle(ChatFormatting.RED), b -> {
            onClose();
            onContinue.run();
        }).bounds(width / 2 + 5, y, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int top = height / 2 - 60;
        g.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        body.renderCentered(g, width / 2, top + 20);
        g.drawCenteredString(font, Component.literal(command).withStyle(ChatFormatting.GRAY), width / 2, height / 2 + 12, 0xAAAAAA);
    }

    /** Clic simulé sur « Continuer » (scénario de test {@link SmokeTest}). */
    void confirmForTest() {
        onClose();
        onContinue.run();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
