package net.xpack.companion.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Toast d'un palier du modpack (#60) : « Palier 25/50/75 % atteint » (bronze, argent, or) ou « Modpack
 * terminé à 100 % » (doré). Même dessin que le toast d'un succès (#213) : une médaille-objet (brique,
 * lingot de fer, lingot d'or, étoile du Nether à 100 %), le palier, le modpack et ses chiffres, la barre
 * du temps restant. Affiché après le toast des succès du même lot ({@link XPackToasts}).
 */
final class CompletionToast implements Toast {
    private static final long DISPLAY_MS = 8000;
    private static final int WIDTH = 200;
    private static final int HEIGHT = 40;
    private static final int GOLD = 0xFFD45A;
    private static final int SILVER = 0xD0D6DE;
    private static final int BRONZE = 0xD08A4A;
    private static final long TICKS_PER_HOUR = 72_000;

    private final int tier;
    private final int color;
    private final Component title;
    private final String modpackName;
    private final Component stats;

    private CompletionToast(int tier, int color, Component title, String modpackName, Component stats) {
        this.tier = tier;
        this.color = color;
        this.title = title;
        this.modpackName = modpackName;
        this.stats = stats;
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

    /** Médaille du palier, en objet du jeu. */
    private ItemStack medal() {
        return new ItemStack(tier >= 100 ? Items.NETHER_STAR : tier >= 75 ? Items.GOLD_INGOT : tier >= 50 ? Items.IRON_INGOT : Items.BRICK);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        Font font = toasts.getMinecraft().font;
        int rgb = 0xFF000000 | color;
        double shown = Compat.toastTime(toasts, DISPLAY_MS);
        ToastStyle.frame(g, WIDTH, HEIGHT, rgb, tier >= 100);
        ToastStyle.icon(g, 6, 11, rgb, medal());
        int textWidth = WIDTH - 30 - 6;
        g.drawString(font, ToastStyle.fit(font, title.getString(), textWidth), 30, 6, color, false);
        if (modpackName != null) {
            g.drawString(font, ToastStyle.fit(font, modpackName, textWidth), 30, 17, 0xFFFFFF, false);
            g.drawString(font, ToastStyle.fit(font, stats.getString(), textWidth), 30, 28, ToastStyle.GREY, false);
        } else {
            g.drawString(font, ToastStyle.fit(font, stats.getString(), textWidth), 30, 17, 0xFFFFFF, false);
        }
        ToastStyle.timeBar(g, WIDTH, HEIGHT, rgb, 1 - timeSinceLastVisible / shown);
        return timeSinceLastVisible >= shown ? Visibility.HIDE : Visibility.SHOW;
    }
}
