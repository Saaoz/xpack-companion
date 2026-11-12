package net.xpack.companion.forge1122;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.xpack.companion.core.XPackCore;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Icônes d'objets (#189), comme dans les versions récentes : le serveur lit les textures dans les
 * fichiers du modpack ; pour les objets qu'il n'a pas pu lire (rendu par le code d'un mod), il demande
 * au jeu de les dessiner. Chaque objet demandé est rendu hors écran, comme dans un inventaire, puis
 * envoyé en PNG. Le serveur refuse tout objet qu'il n'a pas demandé : ce code ne décide de rien.
 */
final class ItemIcons1122 {
    /** Taille de l'image envoyée (le serveur accepte de 16 à 128 px). */
    private static final int SIZE = 64;
    /** Objets dessinés par tick : assez peu pour ne jamais se voir. */
    private static final int PER_TICK = 2;

    private ItemIcons1122() {}

    static void tick(Minecraft mc, XPackCore core) {
        if (mc.world == null || mc.player == null) return;
        for (int i = 0; i < PER_TICK; i++) {
            String item = core.nextIconToDraw();
            if (item == null) return;
            try {
                byte[] png = draw(mc, item);
                if (png != null) core.submitIcon(item, png);
            } catch (Throwable t) {
                // Un objet qui ne se dessine pas est simplement ignoré : il garde le losange sur le site.
            }
        }
    }

    /** Rend l'objet dans une image transparente de {@link #SIZE} px ; null si l'objet n'existe pas. */
    private static byte[] draw(Minecraft mc, String itemId) throws IOException {
        Item item = Item.getByNameOrId(itemId);
        if (item == null || item == Items.AIR) return null;
        ItemStack stack = new ItemStack(item);

        Framebuffer target = new Framebuffer(SIZE, SIZE, true);
        try {
            target.setFramebufferColor(0f, 0f, 0f, 0f);
            target.framebufferClear();
            target.bindFramebuffer(true);
            // Même repère que l'interface du jeu, sur 16 × 16 unités : l'objet remplit l'image.
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0, 16.0, 16.0, 0.0, 1000.0, 3000.0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0f, 0f, -2000f);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
            try {
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GlStateManager.popMatrix();
            }

            ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int i = ((SIZE - 1 - y) * SIZE + x) * 4; // OpenGL lit de bas en haut
                    int r = pixels.get(i) & 0xFF, g = pixels.get(i + 1) & 0xFF, b = pixels.get(i + 2) & 0xFF, a = pixels.get(i + 3) & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } finally {
            target.deleteFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
        }
    }
}
