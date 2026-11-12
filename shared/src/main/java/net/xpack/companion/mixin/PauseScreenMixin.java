package net.xpack.companion.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.xpack.companion.client.Compat;
import net.xpack.companion.client.SettingsScreen;
import net.xpack.companion.client.TeamScreen;
import net.xpack.companion.client.Texts;
import net.xpack.companion.client.XPackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bouton ⚙ en haut à gauche du menu pause : réglages XPack sans passer par la liste des mods. En
 * multijoueur (serveur ou LAN), bouton Équipe XPack à côté (#145).
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void xpack$settingsButton(CallbackInfo ci) {
        PauseScreen self = (PauseScreen) (Object) this;
        if (!Compat.showsPauseMenu(self) || XPackClient.core() == null) return;
        addRenderableWidget(Button.builder(Component.literal("⚙"), b -> minecraft.setScreen(new SettingsScreen(self)))
                .bounds(4, 4, 20, 20)
                .tooltip(Tooltip.create(Texts.tr("xpack.settings.open")))
                .build());
        if (minecraft.isSingleplayer() && (minecraft.getSingleplayerServer() == null || !minecraft.getSingleplayerServer().isPublished())) return;
        addRenderableWidget(Button.builder(Texts.tr("xpack.team.button"), b -> minecraft.setScreen(new TeamScreen(self)))
                .bounds(28, 4, 70, 20)
                .tooltip(Tooltip.create(Texts.tr("xpack.team.title")))
                .build());
    }
}
