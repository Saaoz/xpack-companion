package net.xpack.companion.core;

/** Affichage côté Minecraft, implémenté dans le code client (écrans, toasts, chat). */
public interface Ui {
    /** Alerte avant une commande de triche (#44). {@code onContinue} est appelé si le joueur confirme. */
    void showCheatWarning(String command, Runnable onContinue);

    /** Toast « Succès débloqué » (#39). */
    void toastUnlocked(String name, int points, String rarity);

    /** Toast « Modpack terminé à 100 % » (#60), plus marqué qu'un succès. {@code modpackName} peut être null. */
    void toastCompleted(String modpackName, int achievements, long playtimeTicks);

    /** Écran de consentement au premier lancement (#40). {@code answer} reçoit le choix du joueur. */
    void askConsent(java.util.function.Consumer<Boolean> answer);

    /** Message système dans le chat, à partir d'une clé de traduction du mod. */
    void notify(String translationKey, Object... args);

    /**
     * Triche détectée automatiquement (créatif, vol, mod interdit…) : le monde n'est plus
     * comptabilisé. Notification qui reste ~20 s avec un accès au support (contestation).
     */
    void integrityAlert(String translationKey, Object... args);
}
