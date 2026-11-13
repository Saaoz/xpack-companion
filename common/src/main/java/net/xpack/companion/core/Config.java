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
    private static final String ONLINE_SITE = "https://xpack.saaoz.fr";
    private static final String LOCAL_SITE = "http://localhost:5173";

    /** Adresse de l'API XPack. */
    public String apiUrl = ONLINE_API;
    /** Page du site où saisir le code de liaison. */
    public String linkUrl = ONLINE_LINK;

    /** Le mod parle au serveur de développement local. */
    public boolean usesLocalServer() {
        return LOCAL_API.equals(apiUrl);
    }

    /**
     * Adresses d'API acceptées, comparées <b>exactement</b> (pas de {@code startsWith} : {@code http://localhost:8787.evil.com}
     * doit être refusée). Le dossier {@code config/} voyage avec les modpacks : ce qui y est écrit n'est pas de confiance.
     */
    public static boolean isAllowedApi(String url) {
        return ONLINE_API.equals(url) || LOCAL_API.equals(url);
    }

    /**
     * Pages de liaison acceptées : même origine (schéma, hôte, port) que le site XPack en ligne ou que le site de
     * développement local, sans identifiants dans l'adresse. Le chemin et la requête ne comptent pas.
     */
    public static boolean isAllowedLink(String url) {
        if (url == null) return false;
        try {
            java.net.URI u = new java.net.URI(url);
            if (u.getUserInfo() != null || u.getHost() == null || u.getScheme() == null) return false;
            String origin = u.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + u.getHost().toLowerCase(java.util.Locale.ROOT)
                    + (u.getPort() == -1 ? "" : ":" + u.getPort());
            return origin.equals(ONLINE_SITE) || origin.equals(LOCAL_SITE);
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    /** Remet les valeurs par défaut quand {@code apiUrl} ou {@code linkUrl} ne sont pas des adresses XPack connues. */
    void sanitizeUrls() {
        if (!isAllowedApi(apiUrl)) apiUrl = ONLINE_API;
        if (!isAllowedLink(linkUrl)) linkUrl = ONLINE_LINK;
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
     * affiché. Rien n'est envoyé sans {@code true}. <b>Jamais lue ni écrite dans {@code config/xpack.json}</b>
     * (le champ est {@code transient}) : ce dossier voyage avec les modpacks, qui pourraient pré-cocher l'accord.
     * La réponse est rangée hors de l'instance, dans {@link Credentials}.
     */
    public transient Boolean consented;
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
     * API d'événements locale pour les autres mods (voir {@code net.xpack.companion.api.XPackEvents}).
     * Désactivée par défaut : rien n'est diffusé tant que le joueur ne l'a pas activée ici. Rien ne quitte le PC.
     */
    public boolean eventsApi = false;
    /**
     * Touche qui ouvre le support pendant une alerte de triche (~20 s). Nom de touche Minecraft
     * ({@code key.keyboard.k}) ; K par défaut, car J sert déjà à JourneyMap.
     */
    public String supportKey = "key.keyboard.k";

    // ---- Speedrun libre (#219). Touches au nom Minecraft ; le pavé numérique évite les touches de jeu.
    /** Mode speedrun activé par le joueur. Désactivé par défaut : ni chrono, ni touches, ni run classée tant qu'il ne l'a pas voulu. */
    public boolean speedrunEnabled = false;
    /** Pause et reprise. */
    public String speedrunToggleKey = "key.keyboard.keypad.1";
    /** Valider l'étape suivante. */
    public String speedrunSplitKey = "key.keyboard.keypad.2";
    /** Fin du chrono (utile quand la fin est manuelle) ; les temps restent affichés. */
    public String speedrunStopKey = "key.keyboard.keypad.3";
    /** Remettre à zéro. */
    public String speedrunResetKey = "key.keyboard.keypad.4";
    /** Départ et fin du chrono : auto, auto_end, auto_start ou manual (voir SpeedrunMode). */
    public String speedrunMode = "auto";
    /** Touche de départ du chrono (utile quand le départ est manuel). */
    public String speedrunStartKey = "key.keyboard.keypad.0";
    /** Affichage du chrono et des étapes à l'écran. */
    public boolean speedrunHud = true;
    /** Coin de l'écran où l'affichage est accroché (voir SpeedrunHud.Anchor) : top_left, top_right, bottom_left ou bottom_right. */
    public String speedrunHudAnchor = "top_left";
    /** Taille de l'affichage en pourcentage (50 à 200). */
    public int speedrunHudScale = 100;
    /** Décalage horizontal depuis le bord du coin choisi, en pixels. */
    public int speedrunHudOffsetX = 6;
    /** Décalage vertical depuis le bord du coin choisi, en pixels. */
    public int speedrunHudOffsetY = 6;
    /** Préréglage choisi (voir SpeedrunPresets) ; « custom » pour une liste saisie à la main. */
    public String speedrunPreset = "custom";
    /** Étapes du chrono libre, dans l'ordre. Vide : étapes numérotées. */
    public java.util.List<String> speedrunSteps = new java.util.ArrayList<>();
    /** Meilleur temps personnel : IGT cumulé, en ticks, à chaque étape. */
    public java.util.List<Long> speedrunBest = new java.util.ArrayList<>();

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
                if (c != null) {
                    c.sanitizeUrls();
                    return c;
                }
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
