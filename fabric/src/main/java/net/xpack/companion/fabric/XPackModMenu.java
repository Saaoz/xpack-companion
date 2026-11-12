package net.xpack.companion.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.xpack.companion.client.SettingsScreen;

/** Bouton « Réglages » dans Mod Menu (#40). Chargé seulement si Mod Menu est installé. */
public final class XPackModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
