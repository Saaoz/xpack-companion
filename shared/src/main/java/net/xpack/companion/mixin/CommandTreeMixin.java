package net.xpack.companion.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.xpack.companion.client.XPackCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suggestions du chat : le client ne connaît que l'arbre de commandes envoyé par le serveur. On y
 * ajoute /xpack à chaque réception (connexion, rechargement), sans rien envoyer au serveur.
 */
@Mixin(ClientPacketListener.class)
public abstract class CommandTreeMixin {
    @Shadow
    public abstract CommandDispatcher<SharedSuggestionProvider> getCommands();

    /** Atteint uniquement sur le thread client (le premier passage relance la méthode sur ce thread). */
    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void xpack$addCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
        XPackCommands.register(getCommands());
    }
}
