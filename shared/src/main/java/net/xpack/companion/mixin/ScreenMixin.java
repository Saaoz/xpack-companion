package net.xpack.companion.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.xpack.companion.client.Compat;
import net.xpack.companion.client.StatusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Badge d'état XPack dans le menu pause (#39) : rien n'est affiché pendant le jeu. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void xpack$badge(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof PauseScreen pause && Compat.showsPauseMenu(pause)) StatusBadge.render(g);
    }
}
