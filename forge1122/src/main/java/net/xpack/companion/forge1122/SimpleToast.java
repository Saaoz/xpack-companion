package net.xpack.companion.forge1122;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;

/** Toast XPack : deux lignes sur fond sombre, affiché 5 s dans la pile de toasts du jeu. */
final class SimpleToast implements IToast {
    private static final long DISPLAY_MS = 5_000;
    private final String title;
    private final String subtitle;
    private final int color;

    SimpleToast(String title, String subtitle, int color) {
        this.title = title;
        this.subtitle = subtitle;
        this.color = color;
    }

    @Override
    public Visibility draw(GuiToast toastGui, long delta) {
        Minecraft mc = toastGui.getMinecraft();
        Gui.drawRect(0, 0, 160, 32, 0xFF5B5B5B);
        Gui.drawRect(1, 1, 159, 31, 0xF0141A23);
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(title, 150), 5, 6, color);
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(subtitle, 150), 5, 18, 0xFFFFFF);
        return delta >= DISPLAY_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
