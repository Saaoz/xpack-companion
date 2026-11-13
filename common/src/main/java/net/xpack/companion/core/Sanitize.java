package net.xpack.companion.core;

/**
 * Nettoyage des textes non fiables (nom de modpack, pseudo, équipe, texte venu du serveur) avant
 * affichage. Les interfaces 1.12.2 et 1.7.10 interprètent « § » + code comme une mise en forme : un
 * nom qui en contient pourrait colorer ou masquer du texte. Les codes voulus par le mod lui-même
 * ({@code Fmt}) sont ajoutés <b>après</b> ce nettoyage, jamais dessus.
 */
public final class Sanitize {
    private static final char SECTION = (char) 0xA7; // « § »

    private Sanitize() {}

    /** Retire chaque « § » et le caractère de code qui le suit ; {@code null} reste {@code null}. */
    public static String stripFormatting(String s) {
        if (s == null || s.indexOf(SECTION) < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == SECTION) {
                i++; // saute aussi le code (couleur, style, reset)
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
