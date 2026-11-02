package net.xpack.companion.forge1710;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.xpack.companion.core.J8;
import net.xpack.companion.core.Presence;
import net.xpack.companion.core.Ui;

/** Affichage 1.7.10 : alertes en écrans de question, toasts dessinés par le mod et messages de chat. */
final class Ui1710 implements Ui {
    private static final String PREFIX = EnumChatFormatting.GREEN + "[XPack] " + EnumChatFormatting.RESET;

    /** Question à deux boutons, montrée dès qu'aucun autre écran n'est ouvert (le chat, le chargement du monde). */
    private static void ask(String title, List<PromptScreen1710.Section> sections, String yes, String no, boolean escapeAnswersNo, Consumer<Boolean> answer) {
        Minecraft mc = Minecraft.getMinecraft();
        Client1710.defer(() -> mc.displayGuiScreen(new PromptScreen1710(title, sections, yes, no, escapeAnswersNo, answer)));
    }

    private static List<PromptScreen1710.Section> text(String body) {
        return J8.listOf(new PromptScreen1710.Section(null, body));
    }

    @Override
    public void showCheatWarning(String command, Runnable onContinue) {
        ask(Lang.tr("xpack.warning.title"), J8.listOf(new PromptScreen1710.Section(null, Lang.tr("xpack.warning.body")), new PromptScreen1710.Section(null, EnumChatFormatting.AQUA + command)),
                Lang.tr("xpack.warning.continue"), Lang.tr("xpack.warning.cancel"), true, ok -> {
                    if (ok) onContinue.run();
                });
    }

    @Override
    public void showCheatModeWarning(String mode, Runnable onDisable, Runnable onContinue) {
        String body = Lang.tr(mode.equals("ftbquests") ? "xpack.cheatmode.body.quests" : "xpack.cheatmode.body.items");
        ask(Lang.tr("xpack.cheatmode.title", Lang.tr("xpack.cheatmode.name." + mode)), text(body), Lang.tr("xpack.cheatmode.keep"), Lang.tr("xpack.cheatmode.disable"), false,
                ok -> (ok ? onContinue : onDisable).run());
    }

    @Override
    public void difficultyWarning(String from, String to, boolean canRevert) {
        say(EnumChatFormatting.YELLOW + Lang.tr("xpack.difficulty_warning.title") + ": " + Lang.tr("xpack.difficulty_warning.body", Lang.tr("xpack.difficulty." + from), Lang.tr("xpack.difficulty." + to)));
    }

    @Override
    public void askConsent(Consumer<Boolean> answer) {
        ask(Lang.tr("xpack.consent.title"),
                J8.listOf(new PromptScreen1710.Section(Lang.tr("xpack.consent.sentTitle"), Lang.tr("xpack.consent.sent")),
                        new PromptScreen1710.Section(Lang.tr("xpack.consent.neverTitle"), Lang.tr("xpack.consent.never")),
                        new PromptScreen1710.Section(null, EnumChatFormatting.GRAY + Lang.tr("xpack.consent.footer"))),
                Lang.tr("xpack.consent.accept"), Lang.tr("xpack.consent.refuse"), false, answer);
    }

    @Override
    public void toastUnlocked(String name, int points, String rarity) {
        Toasts1710.unlocked(name, points, rarity);
    }

    @Override
    public void toastCompleted(String modpackName, int achievements, long playtimeTicks) {
        Toasts1710.completed(modpackName, achievements, playtimeTicks);
    }

    @Override
    public void toastMilestone(String modpackName, int tier, int achievements, int total) {
        Toasts1710.milestone(modpackName, tier, achievements, total);
    }

    @Override
    public void notify(String translationKey, Object... args) {
        say(Lang.tr(translationKey, args));
    }

    @Override
    public void integrityAlert(String translationKey, Object... args) {
        String reason = Lang.tr(translationKey, args);
        Toasts1710.alert(reason);
        // Même message dans le chat : il reste lisible après le toast.
        say(EnumChatFormatting.RED + reason + EnumChatFormatting.RESET + " " + EnumChatFormatting.AQUA + "/xpack support" + EnumChatFormatting.RESET);
    }

    @Override
    public void presenceReport(boolean selfLinked, List<Presence.Player> players) {
        say(Lang.tr(selfLinked ? "xpack.check.title" : "xpack.check.title_unlinked"));
        for (Presence.Player p : players) {
            String key = p.status() == Presence.Status.LINKED ? "xpack.check.linked" : p.status() == Presence.Status.INSTALLED ? "xpack.check.installed" : "xpack.check.unknown";
            EnumChatFormatting color = p.status() == Presence.Status.LINKED ? EnumChatFormatting.GREEN : p.status() == Presence.Status.INSTALLED ? EnumChatFormatting.YELLOW : EnumChatFormatting.RED;
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
        ChatComponentText text = new ChatComponentText(message);
        mc.func_152344_a(() -> mc.ingameGUI.getChatGUI().printChatMessage(text));
    }
}
