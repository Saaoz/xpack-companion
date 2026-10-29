package net.xpack.companion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/**
 * Toast d'un palier du modpack (#60) : « Palier 25/50/75 % atteint » (bronze, argent, or) ou « Modpack
 * terminé à 100 % » (doré). Plus large et plus long que celui d'un succès, affiché après le toast des
 * succès du même lot ({@link XPackToasts}).
 */
final class CompletionToast implements Toast {
    private static final long DISPLAY_MS = 8000;
    private static final int WIDTH = 200;
    private static final int GOLD = 0xFFD45A;
    private static final int SILVER = 0xD0D6DE;
    private static final int BRONZE = 0xD08A4A;
    private static final long TICKS_PER_HOUR = 72_000;

    private final int tier;
    private final int color;
    private final Component title;
    private final Component detail;

    private CompletionToast(int tier, int color, Component title, String modpackName, Component stats) {
        this.tier = tier;
        this.color = color;
        this.title = title;
        this.detail = modpackName == null ? stats : Component.literal(modpackName + " · ").append(stats);
    }

    static CompletionToast completed(String modpackName, int achievements, long playtimeTicks) {
        long hours = Math.max(1, Math.round(playtimeTicks / (double) TICKS_PER_HOUR));
        return new CompletionToast(100, GOLD, Texts.tr("xpack.toast.completed"), modpackName, Texts.tr("xpack.toast.completedDetail", achievements, hours));
    }

    static CompletionToast milestone(String modpackName, int tier, int achievements, int total) {
        int color = tier >= 75 ? GOLD : tier >= 50 ? SILVER : BRONZE;
        return new CompletionToast(tier, color, Texts.tr("xpack.toast.milestone", tier), modpackName, Texts.tr("xpack.toast.milestoneDetail", achievements, total));
    }

    int tier() {
        return tier;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        var font = toasts.getMinecraft().font;
        Compat.toastBackground(g, width(), height(), false);
        // Médaille : pastille à la couleur du palier à la place de l'icône d'objet.
        g.fill(8, 8, 24, 24, 0xFF000000 | color);
        g.fill(10, 10, 22, 22, 0xFF2A2418);
        g.drawString(font, "★", 13, 12, color, false);
        g.drawString(font, title, 30, 7, color, false);
        g.drawString(font, font.substrByWidth(detail, WIDTH - 36).getString() + (font.width(detail) > WIDTH - 36 ? "…" : ""), 30, 18, 0xFFFFFF, false);
        return timeSinceLastVisible >= Compat.toastTime(toasts, DISPLAY_MS) ? Visibility.HIDE : Visibility.SHOW;
    }
}
