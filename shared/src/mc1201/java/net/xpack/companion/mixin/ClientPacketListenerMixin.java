package net.xpack.companion.mixin;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.stats.Stat;
import net.xpack.companion.client.XPackClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Commandes tapées (#44, y compris F3+F4 qui passe par sendUnsignedCommand) et statistiques (#36). Version 1.20.1. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void xpack$beforeCommand(String command, CallbackInfo ci) {
        if (XPackClient.handleLocalCommand(command)) {
            ci.cancel(); // /xpack … : traité dans le client, jamais envoyé au serveur
            return;
        }
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        if (!XPackClient.beforeCommand(command, self::sendCommand)) ci.cancel();
    }

    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void xpack$beforeUnsignedCommand(String command, CallbackInfoReturnable<Boolean> cir) {
        // Aussi le chemin des liens cliquables du chat (« Contacter le support » → /xpack support).
        if (XPackClient.handleLocalCommand(command)) {
            cir.setReturnValue(true);
            return;
        }
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        if (!XPackClient.beforeCommand(command, self::sendUnsignedCommand)) cir.setReturnValue(false);
    }

    /** Atteint uniquement sur le thread client (le premier passage relance la méthode sur ce thread). */
    @Inject(method = "handleAwardStats", at = @At("TAIL"))
    private void xpack$afterStats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
        Map<Stat<?>, Integer> stats = new HashMap<>();
        packet.getStats().forEach(stats::put);
        XPackClient.onStats(stats);
    }

    /** Ramassage d'un objet (#128). Au premier passage (thread réseau), la méthode se relance sur le thread client : on attend celui-là. */
    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void xpack$beforeTakeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) XPackClient.onTakeItem(packet.getItemId(), packet.getPlayerId(), packet.getAmount());
    }
}
