package net.xpack.companion.legacy;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.xpack.companion.core.J8;
import net.xpack.companion.core.Presence;
import net.xpack.companion.core.Ui;

/** Affichage 1.12.2 et 1.7.10 : écrans de question, toasts propres à chaque version et messages de chat. */
public final class LegacyUi implements Ui {
    private static final String PREFIX = Fmt.GREEN + "[XPack] " + Fmt.RESET;

    /** Question à deux boutons, montrée dès qu'aucun autre écran n'est ouvert (le chat, le chargement du monde). */
    private static void ask(String title, List<PromptScreen.Section> sections, String yes, String no, boolean escapeAnswersNo, Consumer<Boolean> answer) {
        Minecraft mc = Minecraft.getMinecraft();
        Platform.defer(() -> mc.displayGuiScreen(new PromptScreen(title, sections, yes, no, escapeAnswersNo, answer)));
    }

    private static List<PromptScreen.Section> text(String body) {
        return J8.listOf(new PromptScreen.Section(null, body));
    }

    @Override
    public void showCheatWarning(String command, Runnable onContinue) {
        ask(Lang.tr("xpack.warning.title"), J8.listOf(new PromptScreen.Section(null, Lang.tr("xpack.warning.body")), new PromptScreen.Section(null, Fmt.AQUA + Fmt.plain(command))),
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
        say(Fmt.YELLOW + Lang.tr("xpack.difficulty_warning.title") + ": " + Lang.tr("xpack.difficulty_warning.body", Lang.tr("xpack.difficulty." + from), Lang.tr("xpack.difficulty." + to)));
    }

    @Override
    public void askConsent(Consumer<Boolean> answer) {
        ask(Lang.tr("xpack.consent.title"),
                J8.listOf(new PromptScreen.Section(Lang.tr("xpack.consent.sentTitle"), Lang.tr("xpack.consent.sent")),
                        new PromptScreen.Section(Lang.tr("xpack.consent.neverTitle"), Lang.tr("xpack.consent.never")),
                        new PromptScreen.Section(null, Fmt.GRAY + Lang.tr("xpack.consent.footer"))),
                Lang.tr("xpack.consent.accept"), Lang.tr("xpack.consent.refuse"), false, answer);
    }

    @Override
    public void toastUnlocked(Ui.Unlock unlock) {
        Platform.toastUnlocked(new Ui.Unlock(Fmt.plain(unlock.name), unlock.points, unlock.rarity, unlock.item, unlock.percent, unlock.difficulty, unlock.quest));
    }

    @Override
    public void toastCompleted(String modpackName, int achievements, long playtimeTicks) {
        Platform.toastCompleted(Fmt.plain(modpackName), achievements, playtimeTicks);
    }

    @Override
    public void toastMilestone(String modpackName, int tier, int achievements, int total) {
        Platform.toastMilestone(Fmt.plain(modpackName), tier, achievements, total);
    }

    @Override
    public void notify(String translationKey, Object... args) {
        say(Lang.tr(translationKey, args));
    }

    @Override
    public void integrityAlert(String translationKey, Object... args) {
        String reason = Lang.tr(translationKey, args);
        Platform.toastAlert(reason);
        // Même message dans le chat : il reste lisible après le toast.
        say(Fmt.RED + reason + Fmt.RESET + " " + Fmt.AQUA + "/xpack support" + Fmt.RESET);
    }

    @Override
    public void presenceReport(boolean selfLinked, List<Presence.Player> players) {
        say(Lang.tr(selfLinked ? "xpack.check.title" : "xpack.check.title_unlinked"));
        for (Presence.Player p : players) {
            String key = p.status() == Presence.Status.LINKED ? "xpack.check.linked" : p.status() == Presence.Status.INSTALLED ? "xpack.check.installed" : "xpack.check.unknown";
            String color = p.status() == Presence.Status.LINKED ? Fmt.GREEN : p.status() == Presence.Status.INSTALLED ? Fmt.YELLOW : Fmt.RED;
            line("  " + color + Fmt.plain(p.name()) + " — " + Lang.tr(key));
        }
    }

    @Override
    public void teamNotice(String translationKey, Object... args) {
        say(Lang.tr(translationKey, args));
    }

    public static void say(String message) {
        line(PREFIX + message);
    }

    public static void line(String message) {
        Platform.printChatLine(message);
    }
}
