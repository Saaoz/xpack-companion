package net.xpack.companion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/**
 * Toast « Modpack terminé à 100 % » (#60) : doré, plus large et plus long que celui d'un succès.
 * Affiché après le toast des succès du même lot ({@link XPackToasts}).
 */
final class CompletionToast implements Toast {
    private static final long DISPLAY_MS = 8000;
    private static final int WIDTH = 200;
    private static final int GOLD = 0xFFD45A;
    private static final long TICKS_PER_HOUR = 72_000;

    private final Component title = Texts.tr("xpack.toast.completed");
    private final Component detail;

    CompletionToast(String modpackName, int achievements, long playtimeTicks) {
        long hours = Math.max(1, Math.round(playtimeTicks / (double) TICKS_PER_HOUR));
        Component stats = Texts.tr("xpack.toast.completedDetail", achievements, hours);
        detail = modpackName == null ? stats : Component.literal(modpackName + " · ").append(stats);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        var font = toasts.getMinecraft().font;
        Compat.toastBackground(g, width(), height(), false);
        // Trophée : pastille dorée à la place de l'icône d'objet.
        g.fill(8, 8, 24, 24, 0xFF000000 | GOLD);
        g.fill(10, 10, 22, 22, 0xFF3A2C08);
        g.drawString(font, "★", 13, 12, GOLD, false);
        g.drawString(font, title, 30, 7, GOLD, false);
        g.drawString(font, font.substrByWidth(detail, WIDTH - 36).getString() + (font.width(detail) > WIDTH - 36 ? "…" : ""), 30, 18, 0xFFFFFF, false);
        return timeSinceLastVisible >= Compat.toastTime(toasts, DISPLAY_MS) ? Visibility.HIDE : Visibility.SHOW;
    }
}
