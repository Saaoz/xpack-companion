package net.steamcraft.companion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Toast « Succès Steamcraft débloqué » (#39), distinct du toast vanilla d'advancement. */
final class SteamcraftToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_MS = 5000;

    private final Component title;
    private final Component name;
    private final int rarityColor;

    SteamcraftToast(String name, int points, String rarity) {
        this.title = Component.translatable("steamcraft.toast.title", points);
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
        g.blitSprite(BACKGROUND, 0, 0, width(), height());
        // Pastille de rareté à la place de l'icône d'objet.
        g.fill(8, 8, 24, 24, 0xFF000000 | rarityColor);
        g.fill(10, 10, 22, 22, 0xFF141A23);
        g.drawString(toasts.getMinecraft().font, title, 30, 7, rarityColor, false);
        g.drawString(toasts.getMinecraft().font, name, 30, 18, 0xFFFFFF, false);
        return timeSinceLastVisible >= DISPLAY_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
