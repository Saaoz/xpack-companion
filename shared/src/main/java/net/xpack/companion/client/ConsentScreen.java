package net.xpack.companion.client;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Premier lancement (#40) : ce qui est envoyé, ce qui ne l'est jamais, et un choix explicite.
 * Fermer l'écran (Échap) ne vaut pas accord : la question reviendra au prochain lancement.
 */
public final class ConsentScreen extends Screen {
    private final Consumer<Boolean> answer;
    private MultiLineLabel sent = MultiLineLabel.EMPTY;
    private MultiLineLabel never = MultiLineLabel.EMPTY;
    private MultiLineLabel footer = MultiLineLabel.EMPTY;

    public ConsentScreen(Consumer<Boolean> answer) {
        super(Texts.tr("xpack.consent.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        this.answer = answer;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 40, 380);
        sent = MultiLineLabel.create(font, Texts.tr("xpack.consent.sent"), w);
        never = MultiLineLabel.create(font, Texts.tr("xpack.consent.never"), w);
        footer = MultiLineLabel.create(font, Texts.tr("xpack.consent.footer").withStyle(ChatFormatting.GRAY), w);
        int y = height - 40;
        addRenderableWidget(Button.builder(Texts.tr("xpack.consent.refuse"), b -> choose(false)).bounds(width / 2 - 155, y, 150, 20).build());
        addRenderableWidget(Button.builder(Texts.tr("xpack.consent.accept").withStyle(ChatFormatting.GREEN), b -> choose(true))
                .bounds(width / 2 + 5, y, 150, 20).build());
    }

    private void choose(boolean accepted) {
        onClose();
        answer.accept(accepted);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Compat.background(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        int x = width / 2;
        int y = 24;
        g.drawCenteredString(font, title, x, y, 0xFFFFFF);
        y += 20;
        g.drawCenteredString(font, Texts.tr("xpack.consent.sentTitle").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), x, y, 0xFFFFFF);
        sent.renderCentered(g, x, y + 12);
        y += 16 + sent.getLineCount() * 9 + 10;
        g.drawCenteredString(font, Texts.tr("xpack.consent.neverTitle").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), x, y, 0xFFFFFF);
        never.renderCentered(g, x, y + 12);
        y += 16 + never.getLineCount() * 9 + 10;
        footer.renderCentered(g, x, y);
    }

    /** Accord simulé (scénario de test {@link SmokeTest}). */
    void acceptForTest() {
        choose(true);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
