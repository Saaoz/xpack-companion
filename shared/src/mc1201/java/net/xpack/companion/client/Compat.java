package net.xpack.companion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.xpack.companion.mixin.PauseScreenAccessor;

/**
 * Appels Minecraft qui changent d'une version à l'autre — ici 1.20.1 (Forge). La version 1.21.1
 * est dans {@code shared/src/mc1211}. Tout le reste du code est commun.
 */
public final class Compat {
    private Compat() {}

    /** Fond des écrans : en 1.20.1, {@code Screen.render} ne le dessine pas. */
    static void background(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        screen.renderBackground(g);
    }

    static void openLink(Screen parent, String url) {
        ConfirmLinkScreen.confirmLinkNow(url, parent, true);
    }

    /** Menu pause complet (pas l'écran réduit de F3+Échap) : pas de méthode publique en 1.20.1. */
    public static boolean showsPauseMenu(PauseScreen screen) {
        return ((PauseScreenAccessor) screen).xpack$showPauseMenu();
    }

    static String userUuid(Minecraft mc) {
        return mc.getUser().getGameProfile().getId().toString();
    }

    static boolean allowCommands(IntegratedServer server) {
        return server.getWorldData().getAllowCommands();
    }

    /** Pas de sprites de toast étirables en 1.20.1 : cadre dessiné à la main, aux couleurs des toasts. */
    static void toastBackground(GuiGraphics g, int width, int height, boolean system) {
        g.fill(0, 0, width, height, 0xFF5B5B5B);
        g.fill(1, 1, width - 1, height - 1, system ? 0xF0101418 : 0xF0141A23);
    }

    static double toastTime(ToastComponent toasts, long ms) {
        return ms;
    }
}
