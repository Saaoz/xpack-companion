package net.xpack.companion.core;

import com.google.gson.JsonParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Journal minimal et non sensible du cœur : une ligne par échec (écriture de fichier, réseau, lecture
 * d'un fichier abîmé), pour que le support puisse diagnostiquer sans rien demander de personnel.
 *
 * <p>Ne sont jamais écrits : jeton, identifiant, corps de requête ou de réponse, contenu de fichier.
 * On garde le nom de la classe de l'exception et un message court, dont le dossier personnel de
 * l'utilisateur est retiré ; les erreurs d'analyse JSON ne gardent que leur classe. Un même message
 * n'est répété qu'une fois par minute.
 *
 * <p>Par défaut la ligne part sur {@code java.util.logging} (nom {@code xpack}). Un loader peut
 * brancher son propre journal avec {@link #setSink(Consumer)}.
 */
public final class XLog {
    private static final Logger LOGGER = Logger.getLogger("xpack");
    private static final long REPEAT_MS = 60_000L;
    private static final int MAX_MESSAGE = 120;
    private static final Map<String, Long> LAST = new HashMap<>();
    private static volatile Consumer<String> sink;

    private XLog() {}

    /** Remplace la destination des lignes (journal du loader) ; {@code null} revient à {@code java.util.logging}. */
    public static void setSink(Consumer<String> newSink) {
        sink = newSink;
    }

    /** Journalise un échec sans conséquence pour le joueur : {@code what} dit ce qui n'a pas marché. */
    public static void warn(String what, Throwable t) {
        write(what + " : " + describe(t));
    }

    /** Journalise une information courte. */
    public static void warn(String what) {
        write(what);
    }

    static String describe(Throwable t) {
        if (t == null) return "inconnu";
        String name = t.getClass().getSimpleName();
        if (t instanceof JsonParseException || t.getMessage() == null) return name;
        return name + " (" + sanitize(t.getMessage()) + ")";
    }

    static String sanitize(String message) {
        String s = message.replace('\r', ' ').replace('\n', ' ');
        for (String key : new String[] {"user.home", "user.name"}) {
            String v = System.getProperty(key);
            if (v != null && v.length() > 2) s = s.replace(v, key.equals("user.home") ? "~" : "<user>");
        }
        return s.length() > MAX_MESSAGE ? s.substring(0, MAX_MESSAGE) + "…" : s;
    }

    private static void write(String line) {
        long now = System.currentTimeMillis();
        synchronized (LAST) {
            Long last = LAST.get(line);
            if (last != null && now - last < REPEAT_MS) return;
            if (LAST.size() > 200) LAST.clear();
            LAST.put(line, now);
        }
        Consumer<String> s = sink;
        if (s != null) {
            try {
                s.accept(line);
                return;
            } catch (RuntimeException ignored) {
                // sink défaillant : repli sur java.util.logging
            }
        }
        LOGGER.log(Level.WARNING, line);
    }
}
