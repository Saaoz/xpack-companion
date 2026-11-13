package net.xpack.companion.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/**
 * Suggestions de /xpack pendant la frappe, comme dans les versions récentes : le jeu ne propose rien
 * tant qu'on n'appuie pas sur Tab, on dessine donc la liste au-dessus du champ de chat. Tab complète
 * toujours (voir {@code XPackCommand}). Chaque version branche ici son propre évènement de dessin.
 */
public final class ChatSuggestionsLogic {
    private ChatSuggestionsLogic() {}

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
        for (String sub : Platform.SUBCOMMANDS) if (sub.startsWith(rest) && !sub.equals(rest)) out.add("xpack " + sub);
        // Sous-commande déjà complète : on la garde affichée avec sa description.
        if (out.isEmpty() && Platform.SUBCOMMANDS.contains(rest)) out.add("xpack " + rest);
        return out;
    }

    /** Dessine la liste au-dessus du champ de saisie du chat. */
    public static void draw(GuiTextField field, int screenHeight) {
        if (field == null) return;
        List<String> lines = suggestions(field.getText());
        if (lines.isEmpty()) return;
        FontRenderer font = Platform.font();
        int rowHeight = 12;
        List<String> labels = new ArrayList<>();
        int width = 0;
        for (String line : lines) {
            String sub = line.contains(" ") ? line.substring(line.indexOf(' ') + 1) : null;
            String label = "/" + line + (sub == null ? "" : Fmt.DARK_GRAY + " — " + Fmt.GRAY + Lang.tr("xpack.command." + sub));
            labels.add(label);
            width = Math.max(width, font.getStringWidth(label));
        }
        int height = labels.size() * rowHeight;
        int x = 2;
        int bottom = screenHeight - 16;
        Gui.drawRect(x, bottom - height - 2, x + width + 8, bottom, 0xD0000000);
        for (int i = 0; i < labels.size(); i++) {
            int color = i == 0 ? 0xFFFFFF55 : 0xFFFFFFFF;
            font.drawStringWithShadow(labels.get(i), x + 4, bottom - height + i * rowHeight, color);
        }
    }
}
