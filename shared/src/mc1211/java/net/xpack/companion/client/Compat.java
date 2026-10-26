package net.xpack.companion.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.xpack.companion.core.XPackCore;

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

    /**
     * Catalogue des progrès affichables (#80). Solo : l'arbre complet du serveur intégré, progrès
     * cachés compris. Multijoueur : ce que le serveur a rendu visible au client (il complète au fil du jeu).
     */
    static List<XPackCore.AdvancementInfo> advancementCatalog(Minecraft mc) {
        List<XPackCore.AdvancementInfo> out = new ArrayList<>();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
                h.value().display().ifPresent(d -> out.add(info(h.id().toString(), h.value().parent().map(Object::toString).orElse(null), d)));
            }
        } else if (mc.getConnection() != null) {
            for (AdvancementNode node : mc.getConnection().getAdvancements().getTree().nodes()) {
                AdvancementHolder h = node.holder();
                String parent = node.parent() == null ? null : node.parent().holder().id().toString();
                h.value().display().ifPresent(d -> out.add(info(h.id().toString(), parent, d)));
            }
        }
        return out;
    }

    private static XPackCore.AdvancementInfo info(String id, String parent, DisplayInfo d) {
        return new XPackCore.AdvancementInfo(id, parent, d.getTitle().getString(), d.getDescription().getString(), d.getType().getSerializedName(),
                BuiltInRegistries.ITEM.getKey(d.getIcon().getItem()).toString(), d.isHidden());
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
