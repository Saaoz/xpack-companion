package net.xpack.companion.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Alerte « ce monde n'est plus comptabilisé » : raison de la détection et touche pour contacter
 * le support. Reste {@link #DISPLAY_MS} ms (plus longtemps qu'un toast normal) pour laisser le
 * temps de lire et de réagir. Les toasts ne sont pas cliquables, d'où la touche.
 */
final class IntegrityToast implements Toast {
    static final long DISPLAY_MS = 20_000;
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/system");
    private static final int WIDTH = 220;

    private final Component title;
    private final List<FormattedCharSequence> reason;
    private final Component hint;

    IntegrityToast(Component reason, String keyName) {
        Font font = Minecraft.getInstance().font;
        this.title = Texts.tr("xpack.alert.title");
        this.reason = font.split(reason, WIDTH - 16);
        this.hint = Texts.tr("xpack.alert.support", keyName);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return 20 + reason.size() * 10 + 12;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        Font font = toasts.getMinecraft().font;
        g.blitSprite(BACKGROUND, 0, 0, width(), height());
        g.drawString(font, title, 8, 7, 0xFFB347, false);
        int y = 19;
        for (FormattedCharSequence line : reason) {
            g.drawString(font, line, 8, y, 0xFFFFFF, false);
            y += 10;
        }
        g.drawString(font, hint, 8, y + 2, 0xA9B6C4, false);
        return timeSinceLastVisible >= DISPLAY_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
