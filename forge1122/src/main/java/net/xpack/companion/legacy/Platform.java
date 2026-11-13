package net.xpack.companion.legacy;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.Ui;
import net.xpack.companion.core.XPackCore;
import net.xpack.companion.forge1122.Client1122;
import net.xpack.companion.forge1122.Sounds1122;
import net.xpack.companion.forge1122.Toasts1122;

/**
 * Pont entre le code commun 1.12.2 / 1.7.10 (dossier {@code legacy/}) et ce port : tout ce qui
 * dépend de l'API du jeu ou des classes propres au port. Même nom dans chaque port, un seul est compilé.
 */
public final class Platform {
    /** Coin des toasts tant que le joueur n'a pas choisi. */
    public static final String DEFAULT_TOAST_POSITION = "top_right";
    public static final List<String> SUBCOMMANDS = Client1122.SUBCOMMANDS;
    public static final List<String> PRESETS = Sounds1122.PRESETS;
    public static final List<String> RARITIES = Sounds1122.RARITIES;

    private Platform() {}

    public static XPackCore core() {
        return Client1122.core();
    }

    public static void defer(Runnable action) {
        Client1122.defer(action);
    }

    public static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    public static String profileId() {
        return Minecraft.getMinecraft().getSession().getProfile().getId().toString();
    }

    public static GuiTextField newTextField(FontRenderer font, int x, int y, int width, int height) {
        return new GuiTextField(0, font, x, y, width, height);
    }

    public static int textX(GuiTextField field) {
        return field.x;
    }

    public static int textY(GuiTextField field) {
        return field.y;
    }

    public static void resetCombo() {
        Sounds1122.resetCombo();
    }

    public static void playSound(Config config, String rarity) {
        Sounds1122.play(config, rarity);
    }

    public static void toastUnlocked(Ui.Unlock unlock) {
        Toasts1122.unlocked(unlock);
    }

    public static void toastCompleted(String modpackName, int achievements, long playtimeTicks) {
        Toasts1122.completed(modpackName, achievements, playtimeTicks);
    }

    public static void toastMilestone(String modpackName, int tier, int achievements, int total) {
        Toasts1122.milestone(modpackName, tier, achievements, total);
    }

    public static void toastAlert(String reason) {
        Toasts1122.alert(reason);
    }

    /** Ajoute une ligne au chat, depuis le fil du jeu. */
    public static void printChatLine(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        ITextComponent text = new TextComponentString(message);
        mc.addScheduledTask(() -> mc.ingameGUI.getChatGUI().printChatMessage(text));
    }
}
