package net.xpack.companion.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.xpack.companion.core.XPackCore;
import org.joml.Matrix4f;

/**
 * Icônes d'objets (#189). Le serveur lit les textures dans les fichiers du modpack ; pour les objets
 * qu'il n'a pas pu lire (rendu par le code d'un mod), il demande au jeu de les dessiner. Chaque objet
 * demandé est rendu hors écran, comme dans un inventaire, puis envoyé en PNG. Le serveur refuse tout
 * objet qu'il n'a pas demandé : ce code ne décide de rien.
 */
final class ItemIcons {
    /** Taille de l'image envoyée (le serveur accepte de 16 à 128 px). */
    private static final int SIZE = 64;
    /** Objets dessinés par tick : assez peu pour ne jamais se voir. */
    private static final int PER_TICK = 2;

    private ItemIcons() {}

    static void tick(Minecraft mc, XPackCore core) {
        if (mc.level == null || mc.player == null) return;
        for (int i = 0; i < PER_TICK; i++) {
            String item = core.nextIconToDraw();
            if (item == null) return;
            try {
                byte[] png = draw(mc, item);
                if (png != null) core.submitIcon(item, png);
            } catch (Throwable t) {
                // Un objet qui ne se dessine pas est simplement ignoré : il garde le losange sur le site.
                SmokeTest.log("icon failed " + item + ": " + t);
            }
        }
    }

    /** Rend l'objet dans une image transparente de {@link #SIZE} px ; null si l'objet n'existe pas. */
    private static byte[] draw(Minecraft mc, String itemId) throws java.io.IOException {
        ItemStack stack = Compat.stackOf(itemId);
        if (stack == null || stack.isEmpty()) return null;

        RenderTarget target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        try {
            target.setClearColor(0f, 0f, 0f, 0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            // Même repère que l'interface du jeu, sur 16 × 16 unités : l'objet remplit l'image.
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, 16f, 16f, 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
            Compat.pushIconView();

            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.renderItem(stack, 0, 0);
            graphics.flush();

            // Lecture sans forcer l'opacité : le fond reste transparent.
            NativeImage image = new NativeImage(SIZE, SIZE, false);
            try {
                RenderSystem.bindTexture(target.getColorTextureId());
                image.downloadTexture(0, false);
                image.flipY();
                return image.asByteArray();
            } finally {
                image.close();
            }
        } finally {
            Compat.popIconView();
            RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
