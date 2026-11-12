package net.xpack.companion.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Dessin commun des toasts XPack (#183, #213) : cadre fin (doré pour un moment fort), filet de couleur
 * en haut, icône d'objet dans un cadre de 20 px, barre du temps d'affichage restant en bas. Le toast
 * d'un succès ({@link XPackToast}) a fixé ce dessin ; paliers et alertes le reprennent.
 */
final class ToastStyle {
    static final int BORDER = 0xFF3D4B60;
    static final int FILL = 0xF0141A23;
    static final int WELL = 0xFF0B0F15;
    static final int GREY = 0xA3B1C2;

    private ToastStyle() {}

    /** Fond, cadre ({@code strong} : cadre de la couleur) et filet du haut. */
    static void frame(GuiGraphics g, int width, int height, int rgb, boolean strong) {
        g.fill(0, 0, width, height, strong ? rgb : BORDER);
        g.fill(1, 1, width - 1, height - 1, FILL);
        g.fill(1, 1, width - 1, 3, rgb);
    }

    /** Objet dans un cadre de 20 px à la couleur donnée, coin haut gauche en (x, y). */
    static void icon(GuiGraphics g, int x, int y, int rgb, ItemStack stack) {
        g.fill(x, y, x + 20, y + 20, rgb);
        g.fill(x + 2, y + 2, x + 18, y + 18, WELL);
        g.renderItem(stack, x + 2, y + 2);
    }

    /** Barre du temps restant ({@code left} de 1 à 0). */
    static void timeBar(GuiGraphics g, int width, int height, int rgb, double left) {
        int bar = (int) Math.round((width - 2) * Math.max(0, Math.min(1, left)));
        g.fill(1, height - 3, width - 1, height - 1, WELL);
        g.fill(1, height - 3, 1 + bar, height - 1, rgb);
    }

    static String fit(Font font, String text, int width) {
        return font.width(text) > width ? font.plainSubstrByWidth(text, width - font.width("…")) + "…" : text;
    }
}
