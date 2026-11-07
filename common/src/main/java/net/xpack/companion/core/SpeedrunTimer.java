package net.xpack.companion.core;

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
    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

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
    }

    /** Arrête le chrono : les temps restent affichés jusqu'à la remise à zéro. */
    public void stop() {
        if (state != State.RUNNING && state != State.PAUSED) return;
        rtaBaseMs = rtaMs();
        state = State.FINISHED;
    }

    public void reset() {
        state = State.IDLE;
        igtTicks = 0;
        rtaBaseMs = 0;
        splits.clear();
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
