package net.xpack.companion.client;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/**
 * Toast « Succès XPack » (#39), distinct du toast vanilla d'advancement et affiché après lui
 * ({@link XPackToasts}). Plusieurs succès obtenus ensemble = un seul toast récapitulatif.
 */
final class XPackToast implements Toast {
    private static final long DISPLAY_MS = 5000;
    private static final int WIDTH = 160;

    private final Component title;
    private final Component detail;
    private final int rarityColor;

    private XPackToast(Component title, Component detail, int rarityColor) {
        this.title = title;
        this.detail = detail;
        this.rarityColor = rarityColor;
    }

    static XPackToast of(List<XPackToasts.Unlock> unlocks) {
        // La couleur du plus rare des succès obtenus.
        XPackToasts.Unlock rarest = unlocks.stream().max((a, b) -> Integer.compare(rank(a.rarity()), rank(b.rarity()))).orElseThrow();
        int color = color(rarest.rarity());
        if (unlocks.size() == 1) return new XPackToast(Texts.tr("xpack.toast.title", rarest.points()), Component.literal(rarest.name()), color);
        int points = unlocks.stream().mapToInt(XPackToasts.Unlock::points).sum();
        String names = unlocks.stream().map(XPackToasts.Unlock::name).collect(Collectors.joining(", "));
        return new XPackToast(Texts.tr("xpack.toast.summary", unlocks.size(), points), Component.literal(names), color);
    }

    static int rank(String rarity) {
        return switch (rarity) {
            case "peu_commun" -> 1;
            case "rare" -> 2;
            case "epique" -> 3;
            case "legendaire" -> 4;
            default -> 0;
        };
    }

    private static int color(String rarity) {
        return switch (rarity) {
            case "peu_commun" -> 0x4CC38A;
            case "rare" -> 0x4FA3FF;
            case "epique" -> 0xB47CFF;
            case "legendaire" -> 0xFFB347;
            default -> 0xA9B6C4;
        };
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        var font = toasts.getMinecraft().font;
        Compat.toastBackground(g, width(), height(), false);
        // Pastille de rareté à la place de l'icône d'objet.
        g.fill(8, 8, 24, 24, 0xFF000000 | rarityColor);
        g.fill(10, 10, 22, 22, 0xFF141A23);
        g.drawString(font, title, 30, 7, rarityColor, false);
        // Liste des noms coupée à la largeur du toast.
        g.drawString(font, font.substrByWidth(detail, WIDTH - 36).getString() + (font.width(detail) > WIDTH - 36 ? "…" : ""), 30, 18, 0xFFFFFF, false);
        return timeSinceLastVisible >= Compat.toastTime(toasts, DISPLAY_MS) ? Visibility.HIDE : Visibility.SHOW;
    }
}
