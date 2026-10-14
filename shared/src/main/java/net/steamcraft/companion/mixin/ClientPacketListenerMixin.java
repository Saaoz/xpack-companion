package net.steamcraft.companion.mixin;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.stats.Stat;
import net.steamcraft.companion.client.SteamcraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Commandes tapées (#44, y compris F3+F4 qui passe par sendUnsignedCommand) et statistiques (#36). */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void steamcraft$beforeCommand(String command, CallbackInfo ci) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        if (!SteamcraftClient.beforeCommand(command, self::sendCommand)) ci.cancel();
    }

    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void steamcraft$beforeUnsignedCommand(String command, CallbackInfoReturnable<Boolean> cir) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        if (!SteamcraftClient.beforeCommand(command, self::sendUnsignedCommand)) cir.setReturnValue(false);
    }

    /** Atteint uniquement sur le thread client (le premier passage relance la méthode sur ce thread). */
    @Inject(method = "handleAwardStats", at = @At("TAIL"))
    private void steamcraft$afterStats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
        Map<Stat<?>, Integer> stats = new HashMap<>();
        packet.stats().forEach(stats::put);
        SteamcraftClient.onStats(stats);
    }
}
