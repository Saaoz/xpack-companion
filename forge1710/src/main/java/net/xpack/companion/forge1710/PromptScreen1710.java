package net.xpack.companion.forge1710;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

/**
 * Écran de question à deux boutons, avec un texte long passé à la ligne. Sert au consentement, aux
 * alertes de triche et aux modes triche des mods.
 */
final class PromptScreen1710 extends GuiScreen {
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

    PromptScreen1710(String title, List<Section> sections, String yes, String no, boolean escapeAnswersNo, Consumer<Boolean> answer) {
        this.title = title;
        this.sections = sections;
        this.yes = yes;
        this.no = no;
        this.escapeAnswersNo = escapeAnswersNo;
        this.answer = answer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        int y = height - 40;
        buttonList.add(new GuiButton(NO, width / 2 - 155, y, 150, 20, no));
        buttonList.add(new GuiButton(YES, width / 2 + 5, y, 150, 20, EnumChatFormatting.GREEN + yes));
    }

    private void choose(boolean value) {
        if (answered) return;
        answered = true;
        mc.displayGuiScreen(null);
        answer.accept(value);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        choose(button.id == YES);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
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
        drawCenteredString(fontRendererObj, EnumChatFormatting.GREEN + "" + EnumChatFormatting.BOLD + title, x, y, 0xFFFFFF);
        y += 20;
        for (Section s : sections) {
            if (s.heading != null) {
                drawCenteredString(fontRendererObj, EnumChatFormatting.WHITE + "" + EnumChatFormatting.BOLD + s.heading, x, y, 0xFFFFFF);
                y += 12;
            }
            for (Object line : fontRendererObj.listFormattedStringToWidth(s.body, w)) {
                drawCenteredString(fontRendererObj, (String) line, x, y, 0xDDDDDD);
                y += 10;
            }
            y += 8;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
