package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;

/**
 * Mode triche d'un mod activé (#49) : JEI/REI/EMI (donner des objets) ou mode édition de FTB
 * Quests. Prévenir avant : « Le désactiver » l'éteint, « Garder » signale le monde. Fermer
 * l'écran (Échap) revient à le désactiver : on ne garde jamais un mode triche par erreur.
 */
public final class CheatModeWarningScreen extends Screen {
    private final String mode;
    private final Runnable onDisable;
    private final Runnable onContinue;
    private boolean decided;
    private MultiLineLabel body = MultiLineLabel.EMPTY;

    public CheatModeWarningScreen(String mode, Runnable onDisable, Runnable onContinue) {
        super(Texts.tr("xpack.cheatmode.title", Texts.tr("xpack.cheatmode.name." + mode)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        this.mode = mode;
        this.onDisable = onDisable;
        this.onContinue = onContinue;
    }

    @Override
    protected void init() {
        String key = mode.equals("ftbquests") ? "xpack.cheatmode.body.quests" : "xpack.cheatmode.body.items";
        body = MultiLineLabel.create(font, Texts.tr(key), Math.min(width - 50, 360));
        int y = height / 2 + 30;
        addRenderableWidget(Button.builder(Texts.tr("xpack.cheatmode.disable").withStyle(ChatFormatting.GREEN), b -> decide(onDisable))
                .bounds(width / 2 - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.cheatmode.keep").withStyle(ChatFormatting.RED), b -> decide(onContinue))
                .bounds(width / 2 + 5, y, 150, 20).build());
    }

    private void decide(Runnable choice) {
        decided = true;
        super.onClose();
        choice.run();
    }

    @Override
    public void onClose() {
        if (!decided) decide(onDisable);
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
