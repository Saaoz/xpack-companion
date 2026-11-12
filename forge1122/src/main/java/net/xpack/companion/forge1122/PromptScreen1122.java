package net.xpack.companion.forge1122;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

/**
 * Écran de question à deux boutons, avec un texte long passé à la ligne (le « oui / non » du jeu
 * ne montre qu'une ligne). Sert au consentement, aux alertes de triche et à la difficulté.
 */
final class PromptScreen1122 extends GuiScreen {
    private static final int YES = 1, NO = 2;

    /** Un paragraphe : titre facultatif en gras, puis le texte. */
    static final class Section {
        final String heading;
        final String body;

        Section(String heading, String body) {
            this.heading = heading;
            this.body = body;
        }
    }

    private final String title;
    private final List<Section> sections;
    private final String yes;
    private final String no;
    /** Échap : vrai = répond « non » ; faux = ferme sans répondre (le consentement sera redemandé). */
    private final boolean escapeAnswersNo;
    private final Consumer<Boolean> answer;
    private boolean answered;

    PromptScreen1122(String title, List<Section> sections, String yes, String no, boolean escapeAnswersNo, Consumer<Boolean> answer) {
        this.title = title;
        this.sections = sections;
        this.yes = yes;
        this.no = no;
        this.escapeAnswersNo = escapeAnswersNo;
        this.answer = answer;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int y = height - 40;
        buttonList.add(new GuiButton(NO, width / 2 - 155, y, 150, 20, no));
        buttonList.add(new GuiButton(YES, width / 2 + 5, y, 150, 20, TextFormatting.GREEN + yes));
    }

    private void choose(boolean value) {
        if (answered) return;
        answered = true;
        mc.displayGuiScreen(null);
        answer.accept(value);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        choose(button.id == YES);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (escapeAnswersNo) choose(false);
            else mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int w = Math.min(width - 40, 380);
        int x = width / 2;
        int y = 24;
        drawCenteredString(fontRenderer, TextFormatting.GREEN + "" + TextFormatting.BOLD + title, x, y, 0xFFFFFF);
        y += 20;
        for (Section s : sections) {
            if (s.heading != null) {
                drawCenteredString(fontRenderer, TextFormatting.WHITE + "" + TextFormatting.BOLD + s.heading, x, y, 0xFFFFFF);
                y += 12;
            }
            for (Object line : fontRenderer.listFormattedStringToWidth(s.body, w)) {
                drawCenteredString(fontRenderer, (String) line, x, y, 0xDDDDDD);
                y += 10;
            }
            y += 8;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Le jeu peut fermer l'écran sans qu'on l'ait voulu : on ne réagit qu'au choix du joueur. */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
