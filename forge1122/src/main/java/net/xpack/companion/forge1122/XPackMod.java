package net.xpack.companion.forge1122;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** Point d'entrée Forge 1.12.2. Client uniquement : jamais une dépendance du modpack ni du serveur. */
@Mod(modid = "xpack", name = "XPack Companion", version = "0.4.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
public class XPackMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Client1122.init();
    }
}