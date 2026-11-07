package net.xpack.companion.core;

/**
 * Départ et fin du chrono, au choix du joueur (#232). Une run n'est classée que si le départ ET la fin sont
 * automatiques : sinon le joueur choisirait lui-même ses bornes, et rien ne la distinguerait d'un chrono libre.
 */
public enum SpeedrunMode {
    /** Départ à l'apparition dans un monde neuf, fin au début des crédits. */
    AUTO("auto", true, true),
    /** Départ à la touche, fin au début des crédits. */
    AUTO_END("auto_end", false, true),
    /** Départ à l'apparition dans un monde neuf, fin à la touche. */
    AUTO_START("auto_start", true, false),
    /** Départ et fin à la touche. */
    MANUAL("manual", false, false);

    public final String id;
    private final boolean startAuto;
    private final boolean endAuto;

    SpeedrunMode(String id, boolean startAuto, boolean endAuto) {
        this.id = id;
        this.startAuto = startAuto;
        this.endAuto = endAuto;
    }

    public boolean startAuto() {
        return startAuto;
    }

    public boolean endAuto() {
        return endAuto;
    }

    /** Vrai si la run peut être classée : départ et fin automatiques. */
    public boolean ranked() {
        return startAuto && endAuto;
    }

    /** Le mode d'un identifiant enregistré ; tout automatique si l'identifiant est inconnu ou absent. */
    public static SpeedrunMode parse(String id) {
        for (SpeedrunMode m : values()) if (m.id.equals(id)) return m;
        return AUTO;
    }

    /** Mode suivant dans l'ordre du menu. */
    public SpeedrunMode next() {
        SpeedrunMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
