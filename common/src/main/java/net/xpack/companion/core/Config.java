package net.xpack.companion.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuration du mod ({@code config/xpack.json}). */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Adresse de l'API XPack (en développement : http://localhost:8787). */
    public String apiUrl = "https://api.xpack.saaoz.fr";
    /** Page du site où saisir le code de liaison (en développement : http://localhost:5173/link). */
    public String linkUrl = "https://xpack.saaoz.fr/link";
    /**
     * Anciennes versions uniquement : le jeton était rangé ici, dans {@code config/}, qui part
     * avec les exports de modpack. Il est déplacé au démarrage vers {@link Credentials} puis effacé
     * (Gson n'écrit pas les valeurs nulles).
     */
    public String token;
    public String tokenUuid;
    /**
     * Réponse du joueur à l'écran de consentement (#40) : {@code null} tant qu'il n'a pas été
     * affiché. Rien n'est envoyé sans {@code true}.
     */
    public Boolean consented;
    /** Envoi activé (désactivable sans délier le compte). */
    public boolean enabled = true;
    /** Notifications « Succès débloqué » en jeu (#39). */
    public boolean toasts = true;
    /** Badge d'état (comptabilisé / hors-ligne / non comptabilisé) dans le menu pause (#39). */
    public boolean badge = true;
    /**
     * Touche qui ouvre le support pendant une alerte de triche (~20 s). Nom de touche Minecraft
     * ({@code key.keyboard.k}) ; K par défaut, car J sert déjà à JourneyMap.
     */
    public String supportKey = "key.keyboard.k";

    public static Config load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                Config c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Config.class);
                if (c != null) return c;
            }
        } catch (IOException | RuntimeException ignored) {
            // configuration illisible : valeurs par défaut
        }
        return new Config();
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // disque indisponible : la configuration reste valable pour la session
        }
    }
}
