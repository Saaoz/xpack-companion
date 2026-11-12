package net.xpack.companion.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.xpack.companion.client.XPackClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Progrès en temps réel (#35), version 1.20.1 : les progrès sont des {@code Advancement} (pas
 * encore d'{@code AdvancementHolder}), rangés dans une {@code AdvancementList}.
 */
@Mixin(ClientAdvancements.class)
public abstract class ClientAdvancementsMixin {
    @Shadow
    @Final
    private AdvancementList advancements;

    @Shadow
    @Final
    private Map<Advancement, AdvancementProgress> progress;

    @Inject(method = "update", at = @At("TAIL"))
    private void xpack$afterUpdate(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        List<String> done = new ArrayList<>();
        for (ResourceLocation id : packet.getProgress().keySet()) {
            Advancement advancement = advancements.get(id);
            AdvancementProgress p = advancement == null ? null : progress.get(advancement);
            if (p != null && p.isDone()) done.add(id.toString());
        }
        if (!done.isEmpty()) XPackClient.onAdvancements(done, packet.shouldReset());
        // Progrès nouvellement visibles (multijoueur) : le catalogue envoyé à XPack s'enrichit (#80).
        if (packet.shouldReset() || !packet.getAdded().isEmpty()) XPackClient.refreshCatalog(Minecraft.getInstance());
    }
}
