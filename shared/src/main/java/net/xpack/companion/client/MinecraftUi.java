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
    public void askConsent(java.util.function.Consumer<Boolean> answer) {
        Minecraft mc = Minecraft.getInstance();
        // Différé : l'écran de chargement du monde se ferme au premier tick en jeu.
        mc.tell(() -> mc.setScreen(new ConsentScreen(answer)));
    }

    @Override
    public void toastUnlocked(String name, int points, String rarity) {
        XPackToasts.unlocked(name, points, rarity); // affiché après les progrès du jeu
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
    static void say(Component message) {
        line(Component.empty().append(PREFIX).append(message));
    }

    /** Ligne de chat sans préfixe (suite d'un message, ex. une commande de l'aide). */
    static void line(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.gui.getChat().addMessage(message));
    }
}
