package net.xpack.companion.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.xpack.companion.client.XPackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bloc cassé par le joueur lui-même (#129) : lu avant qu'il disparaisse, avec l'outil tenu. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void xpack$beforeDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        XPackClient.onBlockBroken(pos);
    }
}
