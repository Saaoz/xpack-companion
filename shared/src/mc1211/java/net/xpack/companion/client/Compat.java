package net.xpack.companion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceLocation;

/**
 * Appels Minecraft qui changent d'une version à l'autre — ici 1.21.1 (Fabric, NeoForge). La
 * version 1.20.1 (Forge) est dans {@code shared/src/mc1201}. Tout le reste du code est commun.
 */
public final class Compat {
    private static final ResourceLocation TOAST_ADVANCEMENT = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final ResourceLocation TOAST_SYSTEM = ResourceLocation.withDefaultNamespace("toast/system");

    private Compat() {}

    /** Fond des écrans : en 1.21, {@code Screen.render} le dessine déjà. */
    static void background(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    static void openLink(Screen parent, String url) {
        ConfirmLinkScreen.confirmLinkNow(parent, url);
    }

    /** Menu pause complet (pas l'écran réduit de F3+Échap). */
    public static boolean showsPauseMenu(PauseScreen screen) {
        return screen.showsPauseMenu();
    }

    static String userUuid(Minecraft mc) {
        return mc.getUser().getProfileId().toString();
    }

    static boolean allowCommands(IntegratedServer server) {
        return server.getWorldData().isAllowCommands();
    }

    static void toastBackground(GuiGraphics g, int width, int height, boolean system) {
        g.blitSprite(system ? TOAST_SYSTEM : TOAST_ADVANCEMENT, 0, 0, width, height);
    }

    /** Durée d'affichage, allongée par l'option d'accessibilité « durée des notifications ». */
    static double toastTime(ToastComponent toasts, long ms) {
        return ms * toasts.getNotificationDisplayTimeMultiplier();
    }
}
