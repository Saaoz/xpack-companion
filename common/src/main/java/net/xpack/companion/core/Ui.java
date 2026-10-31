package net.xpack.companion.core;

/** Affichage côté Minecraft, implémenté dans le code client (écrans, toasts, chat). */
public interface Ui {
    /** Alerte avant une commande de triche (#44). {@code onContinue} est appelé si le joueur confirme. */
    void showCheatWarning(String command, Runnable onContinue);

    /**
     * Mode triche d'un mod activé (#49) : {@code jei}, {@code rei}, {@code emi} (donner des objets) ou
     * {@code ftbquests} (mode édition des quêtes). {@code onDisable} l'éteint (aussi en fermant
     * l'écran), {@code onContinue} le garde et signale le monde.
     */
    void showCheatModeWarning(String mode, Runnable onDisable, Runnable onContinue);

    /** Toast « Succès débloqué » (#39). */
    void toastUnlocked(String name, int points, String rarity);

    /** Toast « Modpack terminé à 100 % » (#60), plus marqué qu'un succès. {@code modpackName} peut être null. */
    void toastCompleted(String modpackName, int achievements, long playtimeTicks);

    /** Toast « Palier 25/50/75 % atteint » (#60). {@code modpackName} peut être null. */
    void toastMilestone(String modpackName, int tier, int achievements, int total);

    /** Écran de consentement au premier lancement (#40). {@code answer} reçoit le choix du joueur. */
    void askConsent(java.util.function.Consumer<Boolean> answer);

    /** Message système dans le chat, à partir d'une clé de traduction du mod. */
    void notify(String translationKey, Object... args);

    /**
     * Triche détectée automatiquement (créatif, vol, mod interdit…) : le monde n'est plus
     * comptabilisé. Notification qui reste ~20 s avec un accès au support (contestation).
     */
    void integrityAlert(String translationKey, Object... args);

    /**
     * La difficulté vient de baisser (#116) : le badge de difficulté du monde baissera s'il reste
     * trop longtemps plus bas. Prévenir, pas punir : le joueur peut la remettre ({@code canRevert},
     * en solo) ou accepter. Montré une seule fois par monde.
     */
    void difficultyWarning(String from, String to, boolean canRevert);

    /**
     * Résultat de {@code /xpack check} (#126) : pour chaque autre joueur connecté, mod XPack lié,
     * installé sans compte, ou non détecté. {@code selfLinked} : le compte du joueur est lié.
     */
    void presenceReport(boolean selfLinked, java.util.List<Presence.Player> players);
}
