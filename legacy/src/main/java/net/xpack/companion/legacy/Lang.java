package net.xpack.companion.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.xpack.companion.core.J8;

/**
 * Textes du mod : les mêmes fichiers {@code assets/xpack/lang/*.json} que les versions récentes
 * (la 1.12.2 lit des {@code .lang}, on lit donc nos JSON nous-mêmes), avec {@code %s} comme paramètre.
 */
public final class Lang {
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    private Lang() {}

    public static String tr(String key, Object... args) {
        Minecraft mc = Minecraft.getMinecraft();
        String lang = mc.gameSettings == null || mc.gameSettings.language == null ? "en_us" : mc.gameSettings.language.toLowerCase(Locale.ROOT);
        String text = bundled(lang).get(key);
        if (text == null) text = bundled("en_us").get(key);
        if (text == null) return key;
        try {
            return args.length == 0 ? text : String.format(Locale.ROOT, text, args);
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static Map<String, String> bundled(String lang) {
        Map<String, String> cached = CACHE.get(lang);
        if (cached != null) return cached;
        Map<String, String> out = new HashMap<>();
        try (InputStream in = Lang.class.getResourceAsStream("/assets/xpack/lang/" + lang + ".json")) {
            if (in != null) {
                JsonObject json = J8.parseJson(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception ignored) {
            // fichier absent ou illisible : la clé brute s'affichera
        }
        CACHE.put(lang, out);
        return out;
    }
}
