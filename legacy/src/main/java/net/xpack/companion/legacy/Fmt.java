package net.xpack.companion.legacy;

/**
 * Codes de couleur du chat (« § » + lettre). Même valeur que {@code TextFormatting} (1.12.2) et
 * {@code EnumChatFormatting} (1.7.10), dont les noms diffèrent : ce code commun n'en dépend pas.
 */
public final class Fmt {
    public static final String AQUA = "§b";
    public static final String DARK_GRAY = "§8";
    public static final String GRAY = "§7";
    public static final String GREEN = "§a";
    public static final String RED = "§c";
    public static final String WHITE = "§f";
    public static final String YELLOW = "§e";
    public static final String BOLD = "§l";
    public static final String RESET = "§r";

    private Fmt() {}

    /** Retire les codes « § » d'un texte non fiable (nom de modpack, pseudo, texte du serveur) avant affichage. */
    public static String plain(String untrusted) {
        return net.xpack.companion.core.Sanitize.stripFormatting(untrusted);
    }
}
