package net.xpack.companion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
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
    public void toastUnlocked(String name, int points, String rarity) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().addToast(new XPackToast(name, points, rarity));
    }

    @Override
    public void notify(String translationKey, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        MutableComponent message = Component.translatable(translationKey, args);
        // Le lien de liaison est cliquable.
        if ("xpack.link.code".equals(translationKey) && args.length > 0) {
            message = message.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, String.valueOf(args[0]))).withUnderlined(true));
        }
        Component line = Component.empty().append(PREFIX).append(message);
        mc.tell(() -> mc.gui.getChat().addMessage(line));
    }
}
