package net.xpack.companion.legacy;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatComponentText;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.Ui;
import net.xpack.companion.core.XPackCore;
import net.xpack.companion.forge1710.Client1710;
import net.xpack.companion.forge1710.Sounds1710;
import net.xpack.companion.forge1710.Toasts1710;

/**
 * Pont entre le code commun 1.12.2 / 1.7.10 (dossier {@code legacy/}) et ce port : tout ce qui
 * dépend de l'API du jeu ou des classes propres au port. Même nom dans chaque port, un seul est compilé.
 */
public final class Platform {
    /** Coin des toasts tant que le joueur n'a pas choisi. */
    public static final String DEFAULT_TOAST_POSITION = Toasts1710.DEFAULT_POSITION;
    public static final List<String> SUBCOMMANDS = Client1710.SUBCOMMANDS;
    public static final List<String> PRESETS = Sounds1710.PRESETS;
    public static final List<String> RARITIES = Sounds1710.RARITIES;

    private Platform() {}

    public static XPackCore core() {
        return Client1710.core();
    }

    public static void defer(Runnable action) {
        Client1710.defer(action);
    }

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    public static String profileId() {
        return Minecraft.getMinecraft().getSession().func_148256_e().getId().toString();
    }

    public static GuiTextField newTextField(FontRenderer font, int x, int y, int width, int height) {
        return new GuiTextField(font, x, y, width, height);
    }

    public static int textX(GuiTextField field) {
        return field.xPosition;
    }

    public static int textY(GuiTextField field) {
        return field.yPosition;
    }

    public static void resetCombo() {
        Sounds1710.resetCombo();
    }

    public static void playSound(Config config, String rarity) {
        Sounds1710.play(config, rarity);
    }

    public static void toastUnlocked(Ui.Unlock unlock) {
        Toasts1710.unlocked(unlock);
    }

    public static void toastCompleted(String modpackName, int achievements, long playtimeTicks) {
        Toasts1710.completed(modpackName, achievements, playtimeTicks);
    }

    public static void toastMilestone(String modpackName, int tier, int achievements, int total) {
        Toasts1710.milestone(modpackName, tier, achievements, total);
    }

    public static void toastAlert(String reason) {
        Toasts1710.alert(reason);
    }

    /** Ajoute une ligne au chat, depuis le fil du jeu. */
    public static void printChatLine(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        ChatComponentText text = new ChatComponentText(message);
        mc.func_152344_a(() -> mc.ingameGUI.getChatGUI().printChatMessage(text));
    }
}
