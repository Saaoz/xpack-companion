package net.xpack.companion.forge1710;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;

/**
 * Suggestions de /xpack pendant la frappe, comme dans les versions récentes : le jeu ne propose rien
 * tant qu'on n'appuie pas sur Tab, on dessine donc la liste au-dessus du champ de chat. Tab complète
 * toujours (voir {@link XPackCommand}).
 */
public final class ChatSuggestions {
    private static Field inputField;
    private static boolean lookedUp;

    /** Le champ de saisie du chat, protégé dans le jeu : lu par réflexion, une seule recherche. */
    private static GuiTextField input(GuiChat chat) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                inputField = GuiChat.class.getDeclaredField("inputField");
                inputField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                try {
                    inputField = GuiChat.class.getDeclaredField("field_146415_a");
                    inputField.setAccessible(true);
                } catch (ReflectiveOperationException | RuntimeException e2) {
                    inputField = null;
                }
            }
        }
        try {
            return inputField == null ? null : (GuiTextField) inputField.get(chat);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** Lignes à proposer pour ce texte de chat : « /xpack » tapé en partie, puis les sous-commandes. */
    static List<String> suggestions(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || !text.startsWith("/")) return out;
        String lower = text.substring(1).toLowerCase(Locale.ROOT);
        int space = lower.indexOf(' ');
        if (space < 0) {
            if (!lower.isEmpty() && "xpack".startsWith(lower)) out.add("xpack");
            return out;
        }
        if (!lower.substring(0, space).equals("xpack")) return out;
        String rest = lower.substring(space + 1);
        if (rest.indexOf(' ') >= 0) return out;
        for (String sub : Client1710.SUBCOMMANDS) if (sub.startsWith(rest) && !sub.equals(rest)) out.add("xpack " + sub);
        // Sous-commande déjà complète : on la garde affichée avec sa description.
        if (out.isEmpty() && Client1710.SUBCOMMANDS.contains(rest)) out.add("xpack " + rest);
        return out;
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) return;
        GuiTextField field = input((GuiChat) event.gui);
        if (field == null) return;
        List<String> lines = suggestions(field.getText());
        if (lines.isEmpty()) return;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int rowHeight = 12;
        List<String> labels = new ArrayList<>();
        int width = 0;
        for (String line : lines) {
            String sub = line.contains(" ") ? line.substring(line.indexOf(' ') + 1) : null;
            String label = "/" + line + (sub == null ? "" : EnumChatFormatting.DARK_GRAY + " — " + EnumChatFormatting.GRAY + Lang.tr("xpack.command." + sub));
            labels.add(label);
            width = Math.max(width, font.getStringWidth(label));
        }
        int height = labels.size() * rowHeight;
        int x = 2;
        int bottom = event.gui.height - 16;
        Gui.drawRect(x, bottom - height - 2, x + width + 8, bottom, 0xD0000000);
        for (int i = 0; i < labels.size(); i++) {
            font.drawStringWithShadow(labels.get(i), x + 4, bottom - height + i * rowHeight, i == 0 ? 0xFFFFFF55 : 0xFFFFFFFF);
        }
    }
}
