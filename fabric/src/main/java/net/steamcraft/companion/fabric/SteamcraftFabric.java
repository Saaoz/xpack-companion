package net.steamcraft.companion.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.steamcraft.companion.client.SteamcraftClient;

/** Point d'entrée Fabric : tout le reste est du code vanilla partagé avec NeoForge. */
public final class SteamcraftFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        SteamcraftClient.init(
                loader.getConfigDir(),
                loader.getGameDir(),
                "fabric",
                loader.getAllMods().stream().map(ModContainer::getMetadata).map(m -> m.getId()).toList(),
                loader.getModContainer("steamcraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev"));
    }
}
