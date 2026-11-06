package net.xpack.companion.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.xpack.companion.client.XPackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Après les toasts du jeu : les nôtres, quand le joueur les a placés dans un autre coin.
 *
 * <p>On se greffe sur l'appel du jeu ({@code GameRenderer.render} → {@code ToastComponent.render}),
 * pas sur {@code ToastComponent.render} lui-même : Toast Control (ATM10…) remplace le gestionnaire de
 * toasts par une sous-classe qui réécrit {@code render} sans appeler l'original, et nos toasts
 * n'étaient alors jamais dessinés (le son, lui, partait).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererToastsMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void xpack$renderToasts(ToastComponent toasts, GuiGraphics graphics) {
        toasts.render(graphics);
        XPackClient.renderPlacedToasts(graphics, toasts);
    }
}
