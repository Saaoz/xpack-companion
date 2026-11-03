package net.xpack.companion.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.xpack.companion.client.XPackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Après les toasts du jeu : les nôtres, quand le joueur les a placés dans un autre coin. */
@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void xpack$renderOwnCorner(GuiGraphics graphics, CallbackInfo ci) {
        XPackClient.renderPlacedToasts(graphics, (ToastComponent) (Object) this);
    }
}
