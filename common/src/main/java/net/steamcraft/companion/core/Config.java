package net.steamcraft.companion.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuration du mod ({@code config/steamcraft.json}). */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Adresse de l'API Steamcraft. */
    public String apiUrl = "http://localhost:8787";
    /** Page du site où saisir le code de liaison. */
    public String linkUrl = "http://localhost:5173/link";
    /** Jeton du mod, obtenu par la liaison (#34). Absent tant que le compte n'est pas lié. */
    public String token;
    /** UUID Minecraft auquel le jeton est lié. */
    public String tokenUuid;
    /** Le joueur a accepté l'envoi des données (#40). */
    public boolean consented = true;
    /** Envoi activé (désactivable sans délier le compte). */
    public boolean enabled = true;
    /** Notifications « Succès débloqué » en jeu (#39). */
    public boolean toasts = true;

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
