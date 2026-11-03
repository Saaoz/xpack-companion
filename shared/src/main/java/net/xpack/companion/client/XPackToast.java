package net.xpack.companion.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.xpack.companion.core.Ui;

/**
 * Toast « Succès XPack » (#39), distinct du toast vanilla d'advancement et affiché après lui
 * ({@link XPackToasts}). Plusieurs succès obtenus ensemble = un seul toast récapitulatif.
 * Dessin : icône du progrès dans un cadre de la rareté, rareté et part des joueurs, points à
 * droite, barre du temps d'affichage restant en bas (même dessin en 1.12.2 et 1.7.10).
 */
final class XPackToast implements Toast {
    private static final long DISPLAY_MS = 5000;
    private static final int WIDTH = 200;
    private static final int HEIGHT = 40;
    /** Icônes affichées dans un récapitulatif, puis « +N ». */
    private static final int MAX_ICONS = 3;
    private static final int GOLD = 0xFFD166;
    private static final int GREY = 0xA3B1C2;
    private static final Ui.Unlock.Tr TR = (key, args) -> Texts.tr(key, args).getString();

    private final List<Ui.Unlock> unlocks;
    private final Ui.Unlock rarest;
    private final int color;

    private XPackToast(List<Ui.Unlock> unlocks) {
        this.unlocks = unlocks;
        this.rarest = Ui.Unlock.rarest(unlocks);
        this.color = Ui.Unlock.color(rarest.rarity);
    }

    static XPackToast of(List<Ui.Unlock> unlocks) {
        return new XPackToast(unlocks);
    }

    /** Objet icône du progrès ; une fiole d'expérience pour un succès sans progrès du jeu. */
    private static ItemStack icon(String id) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return new ItemStack(item == Items.AIR ? Items.EXPERIENCE_BOTTLE : item);
    }

    private static String fit(Font font, String text, int width) {
        return font.width(text) > width ? font.plainSubstrByWidth(text, width - font.width("…")) + "…" : text;
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
        // Cadre (doré pour un légendaire), filet de la rareté en haut.
        g.fill(0, 0, WIDTH, HEIGHT, "legendaire".equals(rarest.rarity) ? rgb : 0xFF3D4B60);
        g.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xF0141A23);
        g.fill(1, 1, WIDTH - 1, 3, rgb);

        // Icônes : une par succès (3 au plus, puis « +N »), dans un cadre de leur rareté.
        int x = 6;
        int shownIcons = Math.min(unlocks.size(), unlocks.size() > MAX_ICONS ? MAX_ICONS - 1 : MAX_ICONS);
        for (int i = 0; i < shownIcons; i++) {
            Ui.Unlock u = unlocks.get(i);
            g.fill(x, 11, x + 20, 31, 0xFF000000 | Ui.Unlock.color(u.rarity));
            g.fill(x + 2, 13, x + 18, 29, 0xFF0B0F15);
            g.renderItem(icon(u.item), x + 2, 13);
            x += 22;
        }
        if (unlocks.size() > shownIcons) {
            g.fill(x, 11, x + 20, 31, 0xFFA9B6C4);
            g.fill(x + 2, 13, x + 18, 29, 0xFF0B0F15);
            String more = "+" + (unlocks.size() - shownIcons);
            g.drawString(font, more, x + 10 - font.width(more) / 2, 17, 0xD6DEE8, false);
            x += 22;
        }

        // Points à droite.
        String pts = "+" + Ui.Unlock.totalPoints(unlocks);
        g.drawString(font, pts, WIDTH - 6 - font.width(pts), 10, GOLD, false);
        String unit = Texts.tr("xpack.toast.points").getString();
        g.drawString(font, unit, WIDTH - 6 - font.width(unit), 21, GREY, false);

        // Trois lignes : rareté, nom(s), part des joueurs et difficulté.
        int textX = x + 4;
        int textWidth = WIDTH - textX - 10 - Math.max(font.width(pts), font.width(unit));
        g.drawString(font, fit(font, Ui.Unlock.header(unlocks, TR), textWidth), textX, 6, color, false);
        g.drawString(font, fit(font, Ui.Unlock.names(unlocks), textWidth), textX, 17, 0xFFFFFF, false);
        g.drawString(font, fit(font, Ui.Unlock.detail(unlocks, TR), textWidth), textX, 28, GREY, false);

        // Temps d'affichage restant.
        int bar = (int) Math.round((WIDTH - 2) * Math.max(0, 1 - timeSinceLastVisible / shown));
        g.fill(1, HEIGHT - 3, WIDTH - 1, HEIGHT - 1, 0xFF0B0F15);
        g.fill(1, HEIGHT - 3, 1 + bar, HEIGHT - 1, rgb);
        return timeSinceLastVisible >= shown ? Visibility.HIDE : Visibility.SHOW;
    }
}
