package net.xpack.companion.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Alerte « ce monde n'est plus comptabilisé » : raison de la détection et touche pour contacter
 * le support, au dessin des autres toasts XPack (#213) : une barrière dans un cadre orange, barre du
 * temps restant. Reste {@link #DISPLAY_MS} ms (plus longtemps qu'un toast normal) pour laisser le
 * temps de lire et de réagir. Les toasts ne sont pas cliquables, d'où la touche.
 */
final class IntegrityToast implements Toast {
    static final long DISPLAY_MS = 20_000;
    private static final int WIDTH = 220;
    private static final int ORANGE = 0xFFB347;
    /** Début du texte, à droite de l'icône. */
    private static final int TEXT_X = 30;

    private final Component title;
    private final List<FormattedCharSequence> reason;
    private final Component hint;

    IntegrityToast(Component reason, String keyName) {
        Font font = Minecraft.getInstance().font;
        this.title = Texts.tr("xpack.alert.title");
        this.reason = font.split(reason, WIDTH - TEXT_X - 8);
        this.hint = Texts.tr("xpack.alert.support", keyName);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return Math.max(40, 20 + reason.size() * 10 + 15);
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        Font font = toasts.getMinecraft().font;
        int rgb = 0xFF000000 | ORANGE;
        double shown = Compat.toastTime(toasts, DISPLAY_MS);
        ToastStyle.frame(g, WIDTH, height(), rgb, false);
        ToastStyle.icon(g, 6, 8, rgb, new ItemStack(Items.BARRIER));
        g.drawString(font, title, TEXT_X, 7, ORANGE, false);
        int y = 19;
        for (FormattedCharSequence line : reason) {
            g.drawString(font, line, TEXT_X, y, 0xFFFFFF, false);
            y += 10;
        }
        g.drawString(font, hint, TEXT_X, y + 2, ToastStyle.GREY, false);
        ToastStyle.timeBar(g, WIDTH, height(), rgb, 1 - timeSinceLastVisible / shown);
        return timeSinceLastVisible >= shown ? Visibility.HIDE : Visibility.SHOW;
    }
}
