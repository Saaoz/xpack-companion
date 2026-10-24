package net.xpack.companion.mixin;

import net.minecraft.client.Minecraft;
import net.xpack.companion.client.XPackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tick client : entrée/sortie de monde, mode de jeu, envoi périodique. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void xpack$tick(CallbackInfo ci) {
        XPackClient.onClientTick((Minecraft) (Object) this);
    }
}
