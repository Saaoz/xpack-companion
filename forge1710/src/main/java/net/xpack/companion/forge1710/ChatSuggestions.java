package net.xpack.companion.forge1710;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.xpack.companion.legacy.ChatSuggestionsLogic;

/**
 * Branche les suggestions de /xpack (voir {@link ChatSuggestionsLogic}) sur le chat 1.7.10 : le jeu
 * ne propose rien tant qu'on n'appuie pas sur Tab. Tab complète toujours (voir {@link XPackCommand}).
 */
public final class ChatSuggestions {
    private static Field inputField;
    private static boolean lookedUp;

    /** Le champ de saisie du chat, protégé dans le jeu : lu par réflexion, une seule recherche. */
    private static GuiTextField input(GuiChat chat) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                inputField = GuiChat.class.getDeclaredField("inputField");
                inputField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                try {
                    inputField = GuiChat.class.getDeclaredField("field_146415_a");
                    inputField.setAccessible(true);
                } catch (ReflectiveOperationException | RuntimeException e2) {
                    inputField = null;
                }
            }
        }
        try {
            return inputField == null ? null : (GuiTextField) inputField.get(chat);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiChat)) return;
        ChatSuggestionsLogic.draw(input((GuiChat) event.gui), event.gui.height);
    }
}
