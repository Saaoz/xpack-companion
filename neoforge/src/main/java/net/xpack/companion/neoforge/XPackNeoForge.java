package net.xpack.companion.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforgespi.language.IModInfo;
import net.xpack.companion.client.SettingsScreen;
import net.xpack.companion.client.XPackClient;

/**
 * Point d'entrée NeoForge. {@code dist = CLIENT} : le mod n'est jamais chargé sur un serveur
 * dédié, et un serveur n'a pas besoin de l'avoir pour accepter le joueur.
 */
@Mod(value = "xpack", dist = Dist.CLIENT)
public final class XPackNeoForge {
    public XPackNeoForge(ModContainer container) {
        XPackClient.init(
                FMLPaths.CONFIGDIR.get(),
                FMLPaths.GAMEDIR.get(),
                "neoforge",
                ModList.get().getMods().stream().map(IModInfo::getModId).toList(),
                container.getModInfo().getVersion().toString());
        // Bouton « Configurer » dans la liste des mods (#40).
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }
}
