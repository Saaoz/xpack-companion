package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NBT en texte (SNBT) tel que FTB Quests l'écrit (04/11/2026) : clés sans guillemets (avec des
 * points), séparateurs virgule ou retour à la ligne, nombres suffixés (10L, 0.5d, 1b), tableaux
 * typés ([I; 1, 2]). Rendu sous forme d'arbre Gson ; même lecture que server/src/ftbQuests.ts.
 */
public final class Snbt {
    private static final Pattern NUMBER = Pattern.compile("([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)([bslfdBSLFD]?)");

    private final String text;
    private int i;

    private Snbt(String text) {
        this.text = text;
    }

    /** Lève {@link IllegalArgumentException} sur un texte illisible. */
    public static JsonElement parse(String text) {
        Snbt p = new Snbt(text);
        JsonElement v = p.value();
        p.ws();
        if (p.i < text.length()) throw p.fail("texte en trop");
        return v;
    }

    private void ws() {
        while (i < text.length() && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == ',')) i++;
    }

    private IllegalArgumentException fail(String what) {
        return new IllegalArgumentException("SNBT : " + what + " à la position " + i);
    }

    private char peek() {
        return i < text.length() ? text.charAt(i) : '\0';
    }

    private String str() {
        char quote = text.charAt(i++);
        StringBuilder out = new StringBuilder();
        while (i < text.length() && text.charAt(i) != quote) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char n = text.charAt(++i);
                out.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                out.append(c);
            }
            i++;
        }
        if (peek() != quote) throw fail("texte non fermé");
        i++;
        return out.toString();
    }

    /** Mot sans guillemets : une clé (sans « : ») ou une valeur (« minecraft:stone » permis). */
    private String word(boolean key) {
        int start = i;
        while (i < text.length()) {
            char c = text.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '+' || c == '-' || (!key && c == ':');
            if (!ok) break;
            i++;
        }
        if (start == i) throw fail("caractère inattendu « " + peek() + " »");
        return text.substring(start, i);
    }

    private static JsonElement scalar(String w) {
        if (w.equals("true")) return new JsonPrimitive(true);
        if (w.equals("false")) return new JsonPrimitive(false);
        Matcher m = NUMBER.matcher(w);
        if (m.matches()) {
            String n = m.group(1);
            if (n.contains(".") || n.contains("e") || n.contains("E")) return new JsonPrimitive(Double.parseDouble(n));
            try {
                return new JsonPrimitive(Long.parseLong(n));
            } catch (NumberFormatException e) {
                return new JsonPrimitive(Double.parseDouble(n));
            }
        }
        return new JsonPrimitive(w);
    }

    private JsonElement value() {
        ws();
        char c = peek();
        if (c == '{') {
            i++;
            JsonObject o = new JsonObject();
            for (ws(); peek() != '}'; ws()) {
                if (i >= text.length()) throw fail("objet non fermé");
                String key = peek() == '"' || peek() == '\'' ? str() : word(true);
                ws();
                if (peek() != ':') throw fail("« : » attendu");
                i++;
                o.add(key, value());
            }
            i++;
            return o;
        }
        if (c == '[') {
            i++;
            // Tableau typé : « [I; 1, 2] », « [L; …] », « [B; …] ».
            if ((peek() == 'I' || peek() == 'B' || peek() == 'L') && i + 1 < text.length() && text.charAt(i + 1) == ';') i += 2;
            JsonArray a = new JsonArray();
            for (ws(); peek() != ']'; ws()) {
                if (i >= text.length()) throw fail("liste non fermée");
                a.add(value());
            }
            i++;
            return a;
        }
        if (c == '"' || c == '\'') return new JsonPrimitive(str());
        if (c == '\0') throw fail("fin du texte");
        return scalar(word(false));
    }
}
