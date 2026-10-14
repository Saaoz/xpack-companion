package net.steamcraft.companion.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import net.steamcraft.companion.client.SteamcraftClient;

/**
 * Point d'entrée NeoForge. {@code dist = CLIENT} : le mod n'est jamais chargé sur un serveur
 * dédié, et un serveur n'a pas besoin de l'avoir pour accepter le joueur.
 */
@Mod(value = "steamcraft", dist = Dist.CLIENT)
public final class SteamcraftNeoForge {
    public SteamcraftNeoForge(ModContainer container) {
        SteamcraftClient.init(
                FMLPaths.CONFIGDIR.get(),
                FMLPaths.GAMEDIR.get(),
                "neoforge",
                ModList.get().getMods().stream().map(IModInfo::getModId).toList(),
                container.getModInfo().getVersion().toString());
    }
}
