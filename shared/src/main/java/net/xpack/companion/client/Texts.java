package net.xpack.companion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Textes du mod. Sur Fabric sans Fabric API, les fichiers de langue des mods ne sont pas chargés
 * par le jeu : on lit alors nos propres fichiers {@code assets/xpack/lang} en secours.
 */
public final class Texts {
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    private Texts() {}

    public static MutableComponent tr(String key, Object... args) {
        if (Language.getInstance().has(key)) return Component.translatable(key, args);
        String lang = Minecraft.getInstance().options == null ? "en_us" : Minecraft.getInstance().options.languageCode;
        String text = bundled(lang).getOrDefault(key, bundled("en_us").get(key));
        return text == null ? Component.translatable(key, args) : Component.translatableWithFallback(key, text, args);
    }

    private static Map<String, String> bundled(String lang) {
        return CACHE.computeIfAbsent(lang, l -> {
            Map<String, String> out = new HashMap<>();
            try (InputStream in = Texts.class.getResourceAsStream("/assets/xpack/lang/" + l + ".json")) {
                if (in != null) {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
                }
            } catch (Exception ignored) {
                // fichier absent ou illisible : la clé brute s'affichera
            }
            return out;
        });
    }
}
