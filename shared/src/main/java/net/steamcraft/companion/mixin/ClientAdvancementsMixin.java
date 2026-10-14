package net.steamcraft.companion.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.steamcraft.companion.client.SteamcraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Progrès en temps réel (#35) : le serveur envoie ce paquet au client, en solo comme sur un
 * serveur distant sans mod. On lit l'état après sa mise à jour par le jeu.
 */
@Mixin(ClientAdvancements.class)
public abstract class ClientAdvancementsMixin {
    @Shadow
    @Final
    private Map<AdvancementHolder, AdvancementProgress> progress;

    @Shadow
    public abstract AdvancementHolder get(ResourceLocation id);

    @Inject(method = "update", at = @At("TAIL"))
    private void steamcraft$afterUpdate(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        List<String> done = new ArrayList<>();
        for (ResourceLocation id : packet.getProgress().keySet()) {
            AdvancementHolder holder = get(id);
            AdvancementProgress p = holder == null ? null : progress.get(holder);
            if (p != null && p.isDone()) done.add(id.toString());
        }
        if (!done.isEmpty()) SteamcraftClient.onAdvancements(done, packet.shouldReset());
    }
}
