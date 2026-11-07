package net.xpack.companion.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Run de speedrun classée d'un monde (#225) : démarrage automatique à l'apparition du joueur dans un monde neuf,
 * étapes automatiques sur les progrès du parcours, fin à la mort du dragon. Pur Java, sauvegardé dans le fichier
 * du monde (c'est pourquoi les champs sont publics et simples).
 *
 * <p>Le mod ne fait que constater et envoyer les événements {@code speedrun_start}, {@code speedrun_split} et
 * {@code speedrun_end} : le serveur fait foi et décide si la run est vérifiée. Le temps qui compte est l'IGT
 * (ticks de jeu depuis l'apparition) ; le RTA est donné à titre indicatif.
 */
public final class RankedRun {
    public enum State { ACTIVE, FINISHED, INVALID }

    /** Catégorie par défaut tant que les catégories des modpacks (#53) ne sont pas lues par le mod. */
    public static final String CATEGORY = "any";
    /** Fin de la run : mort du dragon. */
    public static final String FINISH = "minecraft:end/kill_dragon";
    /** Étapes intermédiaires envoyées (progrès du parcours « Par progrès »). */
    public static final List<String> SPLITS = Collections.unmodifiableList(Arrays.asList(
            "minecraft:story/enter_the_nether",
            "minecraft:nether/find_fortress",
            "minecraft:story/follow_ender_eye",
            "minecraft:story/enter_the_end"));
    /** Un monde neuf : la run ne démarre que si l'âge du monde à la première observation est inférieur à ça (1 min). */
    static final long START_WINDOW_TICKS = 1200;

    public State state = State.ACTIVE;
    public long startTicks;
    public long startMs;
    public List<String> splits = new ArrayList<>();
    public long endIgtTicks;
    public long endRtaMs;
    public String invalidReason;

    /** Nature de ce qui vient de se passer pour {@link #onAdvancement}. */
    public enum Kind { SPLIT, END }

    public static final class Outcome {
        public final Kind kind;
        public final String advancement;
        public final long igtTicks;
        public final long rtaMs;

        Outcome(Kind kind, String advancement, long igtTicks, long rtaMs) {
            this.kind = kind;
            this.advancement = advancement;
            this.igtTicks = igtTicks;
            this.rtaMs = rtaMs;
        }
    }

    /** Démarre une run, ou renvoie null si le monde n'est plus neuf (âge du monde trop avancé). */
    public static RankedRun start(long worldAgeTicks, long nowMs) {
        if (worldAgeTicks < 0 || worldAgeTicks > START_WINDOW_TICKS) return null;
        RankedRun r = new RankedRun();
        r.startTicks = worldAgeTicks;
        r.startMs = nowMs;
        return r;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    /** IGT en ticks : en direct tant que la run est active, figé ensuite. */
    public long igtTicks(long playtimeTicks) {
        return state == State.ACTIVE ? Math.max(0, playtimeTicks - startTicks) : endIgtTicks;
    }

    public long rtaMs(long nowMs) {
        return state == State.ACTIVE ? Math.max(0, nowMs - startMs) : endRtaMs;
    }

    /**
     * Un progrès vient d'être obtenu. Renvoie l'étape ou la fin à envoyer, ou null (run non active, progrès
     * hors parcours, étape déjà faite).
     */
    public Outcome onAdvancement(String id, long playtimeTicks, long nowMs) {
        if (state != State.ACTIVE) return null;
        long igt = igtTicks(playtimeTicks);
        long rta = rtaMs(nowMs);
        if (FINISH.equals(id)) {
            state = State.FINISHED;
            endIgtTicks = igt;
            endRtaMs = rta;
            return new Outcome(Kind.END, id, igt, rta);
        }
        if (SPLITS.contains(id) && !splits.contains(id)) {
            splits.add(id);
            return new Outcome(Kind.SPLIT, id, igt, rta);
        }
        return null;
    }

    /** Run perdue (monde signalé, triches activées…). Sans effet si elle n'est plus active. Renvoie vrai si elle l'était. */
    public boolean invalidate(String reason, long playtimeTicks, long nowMs) {
        if (state != State.ACTIVE) return false;
        endIgtTicks = igtTicks(playtimeTicks);
        endRtaMs = rtaMs(nowMs);
        state = State.INVALID;
        invalidReason = reason;
        return true;
    }
}
