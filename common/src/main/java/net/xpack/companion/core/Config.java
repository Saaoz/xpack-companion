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

    /** Serveur XPack en ligne. */
    public static final String ONLINE_API = "https://api.xpack.saaoz.fr";
    public static final String ONLINE_LINK = "https://xpack.saaoz.fr/link";
    /**
     * Serveur de développement sur le PC (Docker local : {@code npm run dev:docker}). Proposé dans les
     * réglages seulement quand il répond (voir {@link XPackCore#detectLocalServer}).
     */
    public static final String LOCAL_API = "http://localhost:8787";
    public static final String LOCAL_LINK = "http://localhost:5173/link";

    /** Adresse de l'API XPack. */
    public String apiUrl = ONLINE_API;
    /** Page du site où saisir le code de liaison. */
    public String linkUrl = ONLINE_LINK;

    /** Le mod parle au serveur de développement local. */
    public boolean usesLocalServer() {
        return apiUrl != null && apiUrl.startsWith(LOCAL_API);
    }

    /** Bascule entre le serveur en ligne et le serveur local (adresses de l'API et de la liaison). */
    public void useLocalServer(boolean local) {
        apiUrl = local ? LOCAL_API : ONLINE_API;
        linkUrl = local ? LOCAL_LINK : ONLINE_LINK;
    }
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
    /** Coins où l'on peut placer les toasts de succès. */
    public static final java.util.List<String> TOAST_POSITIONS = J8.listOf("top_right", "top_left", "bottom_right", "bottom_left");
    /** Délais proposés avant l'apparition d'un toast de succès, en secondes. */
    public static final java.util.List<Integer> TOAST_DELAYS = J8.listOf(0, 1, 2, 3, 5, 10);
    /**
     * Coin des toasts de succès et de paliers : {@code top_right}, {@code top_left},
     * {@code bottom_right} ou {@code bottom_left}. {@code null} : celui de la version (voir
     * {@link #toastPosition(String)}). L'alerte anti-triche reste en haut à droite.
     */
    public String toastPosition;
    /** Délai avant l'apparition d'un toast de succès, en secondes (0 à 10). */
    public int toastDelay = 0;
    /** Son joué avec le toast d'un succès (#59) : {@code gourmand}, {@code carillon}, {@code cloche}, {@code fanfare} ou {@code off}. */
    public String sound = "gourmand";
    /** Volume de ce son, de 0 à 100 (en plus du volume général du jeu). */
    public int soundVolume = 80;
    /** Badge d'état (comptabilisé / hors-ligne / non comptabilisé) dans le menu pause (#39). */
    public boolean badge = true;
    /**
     * Touche qui ouvre le support pendant une alerte de triche (~20 s). Nom de touche Minecraft
     * ({@code key.keyboard.k}) ; K par défaut, car J sert déjà à JourneyMap.
     */
    public String supportKey = "key.keyboard.k";

    /**
     * Coin effectif des toasts de succès. {@code versionDefault} : celui de la version quand le
     * joueur n'a rien choisi (en haut à droite avec les toasts du jeu ; en bas à droite en 1.7.10,
     * où le haut à droite est la place de la fenêtre d'achievement du jeu).
     */
    public String toastPosition(String versionDefault) {
        return TOAST_POSITIONS.contains(toastPosition) ? toastPosition : versionDefault;
    }

    /** Délai effectif avant un toast de succès, en millisecondes (borné à 0–10 s). */
    public long toastDelayMs() {
        return Math.max(0, Math.min(10, toastDelay)) * 1000L;
    }

    public static Config load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                Config c = GSON.fromJson(J8.readString(file, StandardCharsets.UTF_8), Config.class);
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
            J8.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // disque indisponible : la configuration reste valable pour la session
        }
    }
}
