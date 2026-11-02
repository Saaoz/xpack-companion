package net.xpack.companion.forge1122;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.xpack.companion.core.Presence;
import net.xpack.companion.core.Ui;

/** Affichage 1.12.2 : alertes en écrans « oui / non » du jeu, toasts du jeu et messages de chat. */
final class Ui1122 implements Ui {
    private static final String PREFIX = TextFormatting.GREEN + "[XPack] " + TextFormatting.RESET;

    /** Question à deux boutons ; l'écran précédent est rouvert ensuite. */
    private static void ask(String title, String body, String yes, String no, Consumer<Boolean> answer) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            GuiScreen previous = mc.currentScreen;
            GuiYesNoCallback callback = (result, id) -> {
                mc.displayGuiScreen(previous);
                answer.accept(result);
            };
            mc.displayGuiScreen(new GuiYesNo(callback, title, body, yes, no, 0));
        });
    }

    @Override
    public void showCheatWarning(String command, Runnable onContinue) {
        ask(Lang.tr("xpack.warning.title"), Lang.tr("xpack.warning.body") + "  " + command, Lang.tr("xpack.warning.continue"), Lang.tr("xpack.warning.cancel"), ok -> {
            if (ok) onContinue.run();
        });
    }

    @Override
    public void showCheatModeWarning(String mode, Runnable onDisable, Runnable onContinue) {
        ask(Lang.tr("xpack.cheatmode.title", Lang.tr("xpack.cheatmode.name." + mode)), Lang.tr("xpack.cheatmode.body"), Lang.tr("xpack.cheatmode.keep"),
                Lang.tr("xpack.cheatmode.disable"), ok -> (ok ? onContinue : onDisable).run());
    }

    @Override
    public void difficultyWarning(String from, String to, boolean canRevert) {
        say(Lang.tr("xpack.difficulty.warning", from, to));
    }

    @Override
    public void askConsent(Consumer<Boolean> answer) {
        ask(Lang.tr("xpack.consent.title"), Lang.tr("xpack.consent.sent") + "  " + Lang.tr("xpack.consent.never"), Lang.tr("xpack.consent.accept"),
                Lang.tr("xpack.consent.refuse"), answer);
    }

    private static void toast(String title, String subtitle, int color) {
        if (!Client1122.core().config().toasts) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.getToastGui().add(new SimpleToast(title, subtitle, color)));
    }

    @Override
    public void toastUnlocked(String name, int points, String rarity) {
        toast(Lang.tr("xpack.toast.unlocked"), name + " · " + points + " pts", 0xFFFFAA00);
    }

    @Override
    public void toastCompleted(String modpackName, int achievements, long playtimeTicks) {
        toast(Lang.tr("xpack.toast.completed"), modpackName == null ? "" : modpackName, 0xFFFFD257);
    }

    @Override
    public void toastMilestone(String modpackName, int tier, int achievements, int total) {
        toast(tier + " %", (modpackName == null ? "" : modpackName + " · ") + achievements + "/" + total, 0xFF66C0F4);
    }

    @Override
    public void notify(String translationKey, Object... args) {
        say(Lang.tr(translationKey, args));
    }

    @Override
    public void integrityAlert(String translationKey, Object... args) {
        say(TextFormatting.RED + Lang.tr(translationKey, args) + TextFormatting.RESET + " " + TextFormatting.AQUA + "/xpack support" + TextFormatting.RESET);
    }

    @Override
    public void presenceReport(boolean selfLinked, List<Presence.Player> players) {
        say(Lang.tr(selfLinked ? "xpack.check.title" : "xpack.check.title_unlinked"));
        for (Presence.Player p : players) {
            String key = p.status() == Presence.Status.LINKED ? "xpack.check.linked" : p.status() == Presence.Status.INSTALLED ? "xpack.check.installed" : "xpack.check.unknown";
            TextFormatting color = p.status() == Presence.Status.LINKED ? TextFormatting.GREEN : p.status() == Presence.Status.INSTALLED ? TextFormatting.YELLOW : TextFormatting.RED;
            line("  " + color + p.name() + " — " + Lang.tr(key));
        }
    }

    @Override
    public void teamNotice(String translationKey, Object... args) {
        say(Lang.tr(translationKey, args));
    }

    static void say(String message) {
        line(PREFIX + message);
    }

    static void line(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        ITextComponent text = new TextComponentString(message);
        mc.addScheduledTask(() -> mc.ingameGUI.getChatGUI().printChatMessage(text));
    }
}
