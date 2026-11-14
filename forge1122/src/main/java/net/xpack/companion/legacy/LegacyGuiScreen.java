package net.xpack.companion.legacy;

import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

/**
 * Base des écrans du mod (1.12.2) : cache les différences de nom et de signature de {@code GuiScreen}
 * pour que les écrans communs n'aient qu'une version. Même nom dans chaque port, un seul est compilé.
 */
public abstract class LegacyGuiScreen extends GuiScreen {
    protected final FontRenderer font() {
        return fontRenderer;
    }

    protected final void keyTypedSuper(char typedChar, int keyCode) {
        try {
            super.keyTyped(typedChar, keyCode);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected final void mouseClickedSuper(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
