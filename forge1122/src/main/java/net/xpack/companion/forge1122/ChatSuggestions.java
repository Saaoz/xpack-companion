package net.xpack.companion.forge1122;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * Suggestions de /xpack pendant la frappe, comme dans les versions récentes : le jeu 1.12.2 ne
 * propose rien tant qu'on n'appuie pas sur Tab, on dessine donc la liste au-dessus du champ de chat.
 * Tab complète toujours (voir {@link XPackCommand}).
 */
public final class ChatSuggestions {
    private static Field inputField;
    private static boolean lookedUp;

    /** Le champ de saisie du chat, protégé dans le jeu : lu par réflexion, une seule recherche. */
    private static GuiTextField input(GuiChat chat) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                inputField = ReflectionHelper.findField(GuiChat.class, "inputField", "field_146415_a");
            } catch (RuntimeException e) {
                inputField = null;
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
        for (String sub : Client1122.SUBCOMMANDS) if (sub.startsWith(rest) && !sub.equals(rest)) out.add("xpack " + sub);
        // Sous-commande déjà complète : on la garde affichée avec sa description.
        if (out.isEmpty() && Client1122.SUBCOMMANDS.contains(rest)) out.add("xpack " + rest);
        return out;
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiChat)) return;
        GuiTextField field = input((GuiChat) event.getGui());
        if (field == null) return;
        List<String> lines = suggestions(field.getText());
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int rowHeight = 12;
        List<String> labels = new ArrayList<>();
        int width = 0;
        for (String line : lines) {
            String sub = line.contains(" ") ? line.substring(line.indexOf(' ') + 1) : null;
            String label = "/" + line + (sub == null ? "" : TextFormatting.DARK_GRAY + " — " + TextFormatting.GRAY + Lang.tr("xpack.command." + sub));
            labels.add(label);
            width = Math.max(width, font.getStringWidth(label));
        }
        int height = labels.size() * rowHeight;
        int x = 2;
        int bottom = event.getGui().height - 16;
        Gui.drawRect(x, bottom - height - 2, x + width + 8, bottom, 0xD0000000);
        for (int i = 0; i < labels.size(); i++) {
            int color = i == 0 ? 0xFFFFFF55 : 0xFFFFFFFF;
            font.drawStringWithShadow(labels.get(i), x + 4, bottom - height + i * rowHeight, color);
        }
    }
}
