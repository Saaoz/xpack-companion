package net.xpack.companion.legacy;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

/**
 * Base des écrans du mod (1.7.10) : cache les différences de nom et de signature de {@code GuiScreen}
 * pour que les écrans communs n'aient qu'une version. Même nom dans chaque port, un seul est compilé.
 */
public abstract class LegacyGuiScreen extends GuiScreen {
    protected final FontRenderer font() {
        return fontRendererObj;
    }

    protected final void keyTypedSuper(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
    }

    protected final void mouseClickedSuper(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
