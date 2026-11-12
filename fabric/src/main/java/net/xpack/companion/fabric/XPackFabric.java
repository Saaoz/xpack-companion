package net.xpack.companion.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.xpack.companion.client.XPackClient;

/** Point d'entrée Fabric : tout le reste est du code vanilla partagé avec NeoForge. */
public final class XPackFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        XPackClient.init(
                loader.getConfigDir(),
                loader.getGameDir(),
                "fabric",
                loader.getAllMods().stream().map(ModContainer::getMetadata).map(m -> m.getId()).toList(),
                loader.getModContainer("xpack").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev"));
    }
}
