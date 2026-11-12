package net.xpack.companion.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;

/**
 * Point d'entrée Forge 1.20.1. Le mod ne fait rien sur un serveur dédié, et un serveur n'a pas
 * besoin de l'avoir pour accepter le joueur.
 */
@Mod("xpack")
public final class XPackForge {
    public XPackForge() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        // Classe à part : les classes du client ne sont jamais chargées sur un serveur dédié.
        if (FMLEnvironment.dist == Dist.CLIENT) XPackForgeClient.init();
    }
}
