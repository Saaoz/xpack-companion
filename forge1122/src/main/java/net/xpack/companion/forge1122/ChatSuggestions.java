package net.xpack.companion.forge1122;

import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.xpack.companion.legacy.ChatSuggestionsLogic;

/**
 * Branche les suggestions de /xpack (voir {@link ChatSuggestionsLogic}) sur le chat 1.12.2 : le jeu
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
                inputField = ReflectionHelper.findField(GuiChat.class, "inputField", "field_146415_a");
            } catch (RuntimeException e) {
                inputField = null;
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
        if (!(event.getGui() instanceof GuiChat)) return;
        ChatSuggestionsLogic.draw(input((GuiChat) event.getGui()), event.getGui().height);
    }
}
