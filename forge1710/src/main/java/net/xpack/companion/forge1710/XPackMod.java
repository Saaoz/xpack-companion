package net.xpack.companion.forge1710;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Point d'entrée Forge 1.7.10. Client uniquement : jamais une dépendance du modpack, et un serveur
 * qui l'aurait dans ses mods l'ignore.
 */
@Mod(modid = "xpack", name = "XPack Companion", version = "0.4.0", acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public class XPackMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) Client1710.init();
    }
}
