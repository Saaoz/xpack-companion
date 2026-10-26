package net.xpack.companion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/** Toast « Succès XPack débloqué » (#39), distinct du toast vanilla d'advancement. */
final class XPackToast implements Toast {
    private static final long DISPLAY_MS = 5000;

    private final Component title;
    private final Component name;
    private final int rarityColor;

    XPackToast(String name, int points, String rarity) {
        this.title = Texts.tr("xpack.toast.title", points);
        this.name = Component.literal(name);
        this.rarityColor = switch (rarity) {
            case "peu_commun" -> 0x4CC38A;
            case "rare" -> 0x4FA3FF;
            case "epique" -> 0xB47CFF;
            case "legendaire" -> 0xFFB347;
            default -> 0xA9B6C4;
        };
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        Compat.toastBackground(g, width(), height(), false);
        // Pastille de rareté à la place de l'icône d'objet.
        g.fill(8, 8, 24, 24, 0xFF000000 | rarityColor);
        g.fill(10, 10, 22, 22, 0xFF141A23);
        g.drawString(toasts.getMinecraft().font, title, 30, 7, rarityColor, false);
        g.drawString(toasts.getMinecraft().font, name, 30, 18, 0xFFFFFF, false);
        return timeSinceLastVisible >= Compat.toastTime(toasts, DISPLAY_MS) ? Visibility.HIDE : Visibility.SHOW;
    }
}
