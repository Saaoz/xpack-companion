package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.xpack.companion.core.Ui;

/** Affichage en jeu : écran d'alerte, toasts et messages système. */
final class MinecraftUi implements Ui {
    private static final Component PREFIX = Component.literal("[XPack] ").withStyle(ChatFormatting.GREEN);

    @Override
    public void showCheatWarning(String command, Runnable onContinue) {
        Minecraft mc = Minecraft.getInstance();
        // Différé : l'écran de chat se ferme juste après l'envoi de la commande.
        mc.tell(() -> mc.setScreen(new CheatWarningScreen(command, onContinue)));
    }

    @Override
    public void showCheatModeWarning(String mode, Runnable onDisable, Runnable onContinue) {
        SmokeTest.log("cheat mode warning " + mode);
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new CheatModeWarningScreen(mode, onDisable, onContinue)));
    }

    @Override
    public void difficultyWarning(String from, String to, boolean canRevert) {
        SmokeTest.log("difficulty warning " + from + " > " + to);
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new DifficultyWarningScreen(from, to, canRevert)));
    }

    @Override
    public void askConsent(java.util.function.Consumer<Boolean> answer) {
        Minecraft mc = Minecraft.getInstance();
        // Différé : l'écran de chargement du monde se ferme au premier tick en jeu.
        mc.tell(() -> mc.setScreen(new ConsentScreen(answer)));
    }

    @Override
    public void toastUnlocked(Ui.Unlock unlock) {
        XPackToasts.unlocked(unlock); // affiché après les progrès du jeu
    }

    @Override
    public void toastCompleted(String modpackName, int achievements, long playtimeTicks) {
        XPackToasts.completed(CompletionToast.completed(modpackName, achievements, playtimeTicks));
    }

    @Override
    public void toastMilestone(String modpackName, int tier, int achievements, int total) {
        XPackToasts.completed(CompletionToast.milestone(modpackName, tier, achievements, total));
    }

    @Override
    public void notify(String translationKey, Object... args) {
        SmokeTest.log("notice " + translationKey);
        say(Texts.tr(translationKey, args));
    }

    @Override
    public void integrityAlert(String translationKey, Object... args) {
        SmokeTest.log("alert " + translationKey);
        MutableComponent reason = Texts.tr(translationKey, args);
        XPackToasts.alert(new IntegrityToast(reason, XPackClient.supportKeyName())); // après les toasts du jeu
        // Même message dans le chat, avec un lien cliquable : il reste lisible après le toast.
        MutableComponent link = Texts.tr("xpack.alert.supportLink").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xpack support"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Texts.tr("xpack.alert.supportHover"))));
        say(reason.copy().append(" ").append(link));
    }

    /** Ligne « [XPack] … » dans le chat. */
    @Override
    public void presenceReport(boolean selfLinked, java.util.List<net.xpack.companion.core.Presence.Player> players) {
        say(Texts.tr(selfLinked ? "xpack.check.title" : "xpack.check.title_unlinked"));
        for (net.xpack.companion.core.Presence.Player p : players) {
            String key = switch (p.status()) {
                case LINKED -> "xpack.check.linked";
                case INSTALLED -> "xpack.check.installed";
                case UNKNOWN -> "xpack.check.unknown";
            };
            net.minecraft.ChatFormatting color = switch (p.status()) {
                case LINKED -> net.minecraft.ChatFormatting.GREEN;
                case INSTALLED -> net.minecraft.ChatFormatting.YELLOW;
                case UNKNOWN -> net.minecraft.ChatFormatting.RED;
            };
            line(Component.literal("  " + p.name() + " — ").append(Texts.tr(key)).withStyle(color));
        }
        if (players.stream().anyMatch(p -> p.status() != net.xpack.companion.core.Presence.Status.LINKED)) say(Texts.tr("xpack.check.hint"));
    }

    /** Équipe XPack (#145) : message suivi d'un lien qui ouvre l'écran Équipe. */
    @Override
    public void teamNotice(String translationKey, Object... args) {
        SmokeTest.log("team " + translationKey);
        MutableComponent link = Texts.tr("xpack.team.open").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xpack team"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Texts.tr("xpack.team.openHover"))));
        say(Texts.tr(translationKey, args).append(" ").append(link));
    }

    static void say(Component message) {
        line(Component.empty().append(PREFIX).append(message));
    }

    /** Ligne de chat sans préfixe (suite d'un message, ex. une commande de l'aide). */
    static void line(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.gui.getChat().addMessage(message));
    }
}
