package net.xpack.companion.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.xpack.companion.client.SettingsScreen;
import net.xpack.companion.client.XPackClient;

/** Démarrage côté client : tout le reste est du code vanilla partagé avec Fabric et NeoForge. */
final class XPackForgeClient {
    private XPackForgeClient() {}

    static void init() {
        XPackClient.init(
                FMLPaths.CONFIGDIR.get(),
                FMLPaths.GAMEDIR.get(),
                "forge",
                ModList.get().getMods().stream().map(IModInfo::getModId).toList(),
                ModList.get().getModContainerById("xpack").map(c -> c.getModInfo().getVersion().toString()).orElse("dev"));
        // Bouton « Config » dans la liste des mods.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }
}
