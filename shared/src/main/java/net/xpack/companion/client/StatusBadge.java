package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.XPackCore;

/** Pastille 🟢 comptabilisé / 🟡 hors-ligne ou non lié / 🔴 non comptabilisé (#39). */
public final class StatusBadge {
    private static boolean logged;

    private StatusBadge() {}

    public static void render(GuiGraphics g) {
        XPackCore core = XPackClient.core();
        if (core == null || !core.config().badge) return;
        XPackCore.Status status = core.status();
        int color = switch (status) {
            case COUNTED -> 0x55FF55;
            case OFFLINE, UNLINKED -> 0xFFD24A;
            case NOT_COUNTED -> 0xFF5555;
            case DISABLED -> 0x888888;
        };
        Minecraft mc = Minecraft.getInstance();
        Component label = Component.literal("● ").withColor(color)
                .append(Component.literal("XPack · ").withStyle(ChatFormatting.WHITE))
                .append(Texts.tr("xpack.badge." + status.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        g.drawString(mc.font, label, 6, 6, 0xFFFFFF);
        if (!logged) {
            logged = true;
            SmokeTest.log("badge rendered status=" + status);
        }
    }
}
