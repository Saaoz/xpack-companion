package net.xpack.companion.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.Advancement;
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
import net.minecraft.world.item.ItemStack;
import net.xpack.companion.core.XPackCore;
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

    /**
     * Catalogue des progrès affichables (#80). Solo : l'arbre complet du serveur intégré, progrès
     * cachés compris. Multijoueur : ce que le serveur a rendu visible au client (il complète au fil du jeu).
     */
    static List<XPackCore.AdvancementInfo> advancementCatalog(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        Iterable<Advancement> all = server != null
                ? server.getAdvancements().getAllAdvancements()
                : mc.getConnection() != null ? mc.getConnection().getAdvancements().getAdvancements().getAllAdvancements() : List.of();
        List<XPackCore.AdvancementInfo> out = new ArrayList<>();
        for (Advancement a : all) {
            DisplayInfo d = a.getDisplay();
            if (d == null) continue;
            out.add(new XPackCore.AdvancementInfo(a.getId().toString(), a.getParent() == null ? null : a.getParent().getId().toString(), d.getTitle().getString(),
                    d.getDescription().getString(), d.getFrame().getName(), BuiltInRegistries.ITEM.getKey(d.getIcon().getItem()).toString(), d.isHidden()));
        }
        return out;
    }

    /** Pile d'un objet par son identifiant (#189) ; null si inconnu. */
    static ItemStack stackOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(null);
    }

    /** Repère de l'interface (profondeur -11000) pour dessiner un objet hors écran (#189). En 1.20.1 : PoseStack. */
    static void pushIconView() {
        com.mojang.blaze3d.vertex.PoseStack stack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        stack.translate(0f, 0f, -11000f);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
    }

    static void popIconView() {
        com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().popPose();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
    }

    static boolean allowCommands(IntegratedServer server) {
        return server.getWorldData().getAllowCommands();
    }

    /** Durée d'affichage (pas d'option d'accessibilité de durée en 1.20.1). */
    static double toastTime(ToastComponent toasts, long ms) {
        return ms;
    }
}
