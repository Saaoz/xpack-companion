package net.xpack.companion.forge1710;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.xpack.companion.core.XPackCore;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Icônes d'objets (#189), comme dans les versions récentes : le serveur lit les textures dans les
 * fichiers du modpack ; pour les objets qu'il n'a pas pu lire (rendu par le code d'un mod), il demande
 * au jeu de les dessiner. Chaque objet demandé est rendu hors écran, comme dans un inventaire, puis
 * envoyé en PNG. Le serveur refuse tout objet qu'il n'a pas demandé : ce code ne décide de rien.
 */
final class ItemIcons1710 {
    /** Taille de l'image envoyée (le serveur accepte de 16 à 128 px). */
    private static final int SIZE = 64;
    /** Objets dessinés par tick : assez peu pour ne jamais se voir. */
    private static final int PER_TICK = 2;
    private static final RenderItem ITEMS = new RenderItem();

    private ItemIcons1710() {}

    static void tick(Minecraft mc, XPackCore core) {
        // Sans cible de rendu hors écran (option « Fast Render » / carte ancienne), on ne dessine rien.
        if (mc.theWorld == null || mc.thePlayer == null || !OpenGlHelper.isFramebufferEnabled()) return;
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
        Object found = Item.itemRegistry.getObject(itemId);
        if (!(found instanceof Item)) return null;
        ItemStack stack = new ItemStack((Item) found);

        Framebuffer target = new Framebuffer(SIZE, SIZE, true);
        try {
            target.setFramebufferColor(0f, 0f, 0f, 0f);
            target.framebufferClear();
            target.bindFramebuffer(true);
            // Même repère que l'interface du jeu, sur 16 × 16 unités : l'objet remplit l'image.
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, 16.0, 16.0, 0.0, 1000.0, 3000.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(0f, 0f, -2000f);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            try {
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
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
