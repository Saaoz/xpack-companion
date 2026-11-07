package net.xpack.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Chronomètre de speedrun du mode libre (#219) : pur Java, sans Minecraft, pour être testé et partagé par
 * tous les loaders.
 *
 * <p>Deux temps tournent ensemble : l'<b>IGT</b> (ticks de jeu, un par {@link #gameTick()} : le jeu en pause
 * ne l'avance pas) et le <b>RTA</b> (temps réel). La pause du chrono arrête les deux. Les étapes (splits) sont
 * validées à la main, dans l'ordre de la liste d'étapes ; sans liste, elles s'appellent « Étape 1 », « Étape 2 »…
 * Un chrono libre n'est jamais vérifié et ne part jamais au classement.
 */
public final class SpeedrunTimer {
    /** LOST : chrono perdu (#220), les temps restent affichés jusqu'à la remise à zéro. */
    public enum State { IDLE, RUNNING, PAUSED, FINISHED, LOST }

    /** Déplacement toléré pendant la pause du chrono, en blocs : le bruit des flottants, pas un pas. */
    static final double MOVE_EPSILON = 0.01;

    /** Une étape validée : temps cumulés depuis le départ. */
    public static final class Split {
        public final String name;
        public final long igtTicks;
        public final long rtaMs;

        Split(String name, long igtTicks, long rtaMs) {
            this.name = name;
            this.igtTicks = igtTicks;
            this.rtaMs = rtaMs;
        }
    }

    private final LongSupplier clockMs;
    private State state = State.IDLE;
    private long igtTicks;
    private long rtaBaseMs;
    private long rtaStartedAt;
    private long startedAtMs;
    private List<String> steps = new ArrayList<>();
    private final List<Split> splits = new ArrayList<>();
    /** Meilleur temps personnel : IGT cumulé (en ticks) à chaque étape. */
    private List<Long> best = new ArrayList<>();

    public SpeedrunTimer(LongSupplier clockMs) {
        this.clockMs = clockMs;
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /** Vrai tant qu'un chrono est affiché : en cours, en pause ou terminé (avant la remise à zéro). */
    public boolean isActive() {
        return state != State.IDLE;
    }

    /** Heure de départ du chrono, lue sur l'horloge fournie (millisecondes depuis 1970 en jeu). */
    public long startedAtMs() {
        return startedAtMs;
    }

    public long igtTicks() {
        return igtTicks;
    }

    public long igtMs() {
        return igtTicks * 50;
    }

    public long rtaMs() {
        return rtaBaseMs + (state == State.RUNNING ? clockMs.getAsLong() - rtaStartedAt : 0);
    }

    public List<Split> splits() {
        return Collections.unmodifiableList(splits);
    }

    public List<String> steps() {
        return Collections.unmodifiableList(steps);
    }

    /** Remplace la liste d'étapes. Sans effet sur les étapes déjà validées d'un chrono en cours. */
    public void setSteps(List<String> names) {
        steps = new ArrayList<>(names);
    }

    public void setBest(List<Long> cumulativeIgtTicks) {
        best = cumulativeIgtTicks == null ? new ArrayList<Long>() : new ArrayList<>(cumulativeIgtTicks);
    }

    public List<Long> best() {
        return Collections.unmodifiableList(best);
    }

    /** Nom de la prochaine étape à valider, ou null quand toutes le sont. */
    public String nextStep() {
        if (steps.isEmpty()) return "Étape " + (splits.size() + 1);
        return splits.size() < steps.size() ? steps.get(splits.size()) : null;
    }

    /** Touche « démarrer / pause » : démarre, met en pause ou reprend selon l'état. */
    public void toggle() {
        switch (state) {
            case RUNNING:
                pause();
                break;
            case PAUSED:
                resume();
                break;
            default:
                start();
        }
    }

    /** Nouveau chrono à zéro, déjà lancé. */
    public void start() {
        reset();
        state = State.RUNNING;
        startedAtMs = clockMs.getAsLong();
        rtaStartedAt = clockMs.getAsLong();
    }

    public void pause() {
        if (state != State.RUNNING) return;
        rtaBaseMs = rtaMs();
        state = State.PAUSED;
    }

    public void resume() {
        if (state != State.PAUSED) return;
        rtaStartedAt = clockMs.getAsLong();
        state = State.RUNNING;
        anchor = null;
    }

    /** Arrête le chrono : les temps restent affichés jusqu'à la remise à zéro. */
    public void stop() {
        if (state != State.RUNNING && state != State.PAUSED) return;
        rtaBaseMs = rtaMs();
        state = State.FINISHED;
    }

    /**
     * Quitter le monde : un chrono en cours passe en pause, sans surveillance de déplacement tant qu'on n'est pas
     * revenu dans le monde. Le chrono est lié à son monde (voir {@link #toJson}).
     */
    public void pauseForExit() {
        pause();
        anchor = null;
    }

    /** L'état du chrono (temps, étapes validées), à garder avec son monde. Un chrono en cours doit d'abord être mis en pause. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("state", state.name());
        o.addProperty("igtTicks", igtTicks);
        o.addProperty("rtaMs", rtaMs());
        o.addProperty("startedAtMs", startedAtMs);
        JsonArray list = new JsonArray();
        for (Split sp : splits) {
            JsonObject j = new JsonObject();
            j.addProperty("name", sp.name);
            j.addProperty("igtTicks", sp.igtTicks);
            j.addProperty("rtaMs", sp.rtaMs);
            list.add(j);
        }
        o.add("splits", list);
        return o;
    }

    /**
     * Reprend un chrono gardé avec son monde. Un chrono qui tournait revient en pause (le joueur le reprend, ou
     * le départ automatique le fait) ; terminé ou perdu, il revient tel quel. Renvoie faux si les données sont illisibles.
     */
    public boolean restore(JsonObject o) {
        try {
            State saved = State.valueOf(o.get("state").getAsString());
            if (saved == State.IDLE) return false;
            long ticks = o.get("igtTicks").getAsLong();
            long rta = o.get("rtaMs").getAsLong();
            long started = o.get("startedAtMs").getAsLong();
            List<Split> list = new ArrayList<>();
            for (com.google.gson.JsonElement e : o.getAsJsonArray("splits")) {
                JsonObject j = e.getAsJsonObject();
                list.add(new Split(j.get("name").getAsString(), j.get("igtTicks").getAsLong(), j.get("rtaMs").getAsLong()));
            }
            reset();
            igtTicks = ticks;
            rtaBaseMs = rta;
            startedAtMs = started;
            splits.addAll(list);
            state = saved == State.RUNNING ? State.PAUSED : saved;
            return true;
        } catch (RuntimeException e) {
            reset();
            return false;
        }
    }

    public void reset() {
        state = State.IDLE;
        igtTicks = 0;
        rtaBaseMs = 0;
        splits.clear();
        anchor = null;
    }

    // ------------------------------------------------------------------ pause et déplacement (#220)

    private double[] anchor;
    private String anchorDimension;

    /**
     * Garde de la pause : à appeler à chaque tick où le jeu tourne. Tant que le chrono est en pause (touche de
     * pause), le joueur ne doit pas bouger : le moindre déplacement ou changement de dimension fait perdre le
     * chrono. La pause du jeu (Échap en solo) fige la partie, elle n'a pas besoin de garde. Renvoie vrai quand
     * le chrono vient d'être perdu.
     */
    public boolean watchMovement(double x, double y, double z, String dimension) {
        if (state != State.PAUSED) {
            anchor = null;
            return false;
        }
        if (anchor == null) { // première observation de la pause : le point de départ
            anchor = new double[] {x, y, z};
            anchorDimension = dimension;
            return false;
        }
        double dx = x - anchor[0];
        double dy = y - anchor[1];
        double dz = z - anchor[2];
        boolean moved = Math.sqrt(dx * dx + dy * dy + dz * dz) > MOVE_EPSILON || !String.valueOf(dimension).equals(anchorDimension);
        if (moved) lose();
        return moved;
    }

    /** Chrono perdu : les temps sont figés et ne comptent plus pour aucun record. */
    public void lose() {
        if (state != State.RUNNING && state != State.PAUSED) return;
        rtaBaseMs = rtaMs();
        state = State.LOST;
        anchor = null;
    }

    /** À appeler une fois par tick de jeu (monde chargé, jeu non en pause). */
    public void gameTick() {
        if (state == State.RUNNING) igtTicks++;
    }

    /** Valide l'étape suivante. La dernière étape de la liste termine le chrono. Renvoie null si impossible. */
    public Split split() {
        if (state != State.RUNNING) return null;
        String name = nextStep();
        if (name == null) return null;
        Split s = new Split(name, igtTicks, rtaMs());
        splits.add(s);
        if (!steps.isEmpty() && splits.size() == steps.size()) stop();
        return s;
    }

    /** Écart de l'IGT de l'étape {@code index} avec le meilleur temps personnel, en ticks (négatif = en avance), ou null. */
    public Long deltaTicks(int index) {
        if (index < 0 || index >= splits.size() || index >= best.size()) return null;
        return splits.get(index).igtTicks - best.get(index);
    }

    /**
     * Nouveau meilleur temps à enregistrer une fois le chrono terminé, ou null. Le chrono doit avoir au moins une
     * étape ; il remplace le meilleur temps s'il n'y en a pas, ou s'il a autant d'étapes et finit plus vite.
     */
    public List<Long> newBest() {
        if (state != State.FINISHED || splits.isEmpty()) return null;
        List<Long> run = new ArrayList<>();
        for (Split s : splits) run.add(s.igtTicks);
        if (best.isEmpty()) return run;
        if (best.size() == run.size() && run.get(run.size() - 1) < best.get(best.size() - 1)) return run;
        return null;
    }

    /** « 1:02:03.456 » ou « 02:03.456 » : heures seulement quand il y en a. */
    public static String format(long ms) {
        long abs = Math.abs(ms);
        long h = abs / 3_600_000;
        long m = abs / 60_000 % 60;
        long s = abs / 1000 % 60;
        long milli = abs % 1000;
        String body = h > 0 ? String.format("%d:%02d:%02d.%03d", h, m, s, milli) : String.format("%02d:%02d.%03d", m, s, milli);
        return (ms < 0 ? "-" : "") + body;
    }
}
