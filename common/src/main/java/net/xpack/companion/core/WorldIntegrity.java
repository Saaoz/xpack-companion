package net.xpack.companion.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Statut d'intégrité d'un monde (#43). Portage de {@code flagWorld} / {@code unlockCounts}.
 * Un monde signalé le reste : on ne fait qu'ajouter des raisons. Le serveur fait foi.
 */
public final class WorldIntegrity {
    public enum Status { PROPRE, SIGNALE, IMPORTE }

    public enum Reason {
        COMMAND("command"), CREATIVE("creative"), SPECTATOR("spectator"), ABILITIES("abilities"),
        CHEAT_MODE("cheat_mode"), BLACKLISTED_MOD("blacklisted_mod"), SERVER_REVIEW("server_review");

        public final String wire;

        Reason(String wire) {
            this.wire = wire;
        }
    }

    public record Flag(Reason reason, String at, String detail) {}

    private Status status = Status.PROPRE;
    private String flaggedAt;
    private final List<Flag> reasons = new ArrayList<>();

    public Status status() {
        return status;
    }

    public String flaggedAt() {
        return flaggedAt;
    }

    public List<Flag> reasons() {
        return List.copyOf(reasons);
    }

    public boolean isFlagged() {
        return status == Status.SIGNALE;
    }

    /** Signale le monde ; renvoie vrai si c'est la première triche (le monde vient de basculer). */
    public boolean flag(Reason reason, String atIso, String detail) {
        boolean first = status != Status.SIGNALE;
        status = Status.SIGNALE;
        if (flaggedAt == null || atIso.compareTo(flaggedAt) < 0) flaggedAt = atIso;
        reasons.add(new Flag(reason, atIso, detail));
        return first;
    }

    /** Alignement sur le statut renvoyé par le serveur. */
    public void syncFromServer(String serverStatus, String serverFlaggedAt) {
        if ("signale".equals(serverStatus)) {
            status = Status.SIGNALE;
            if (serverFlaggedAt != null && (flaggedAt == null || serverFlaggedAt.compareTo(flaggedAt) < 0)) flaggedAt = serverFlaggedAt;
        }
    }

    public void reset() {
        status = Status.PROPRE;
        flaggedAt = null;
        reasons.clear();
    }

    public boolean unlockCounts(String unlockedAtIso) {
        if (status == Status.IMPORTE) return false;
        if (status == Status.SIGNALE && flaggedAt != null) return unlockedAtIso.compareTo(flaggedAt) < 0;
        return true;
    }
}
