package net.xpack.companion.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Jeu en groupe (#126) : les UUID Minecraft des joueurs ne partent jamais en clair, seulement leur
 * empreinte. Même calcul que le serveur ({@code presenceHash} dans {@code server/src/presence.ts}).
 */
public final class Presence {
    /** Statut d'un autre joueur pour {@code /xpack check}. */
    public enum Status { LINKED, INSTALLED, UNKNOWN }

    public static final class Player {
        private final String name;
        private final Status status;

        public Player(String name, Status status) {
            this.name = name;
            this.status = status;
        }

        public String name() {
            return name;
        }

        public Status status() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Player)) return false;
            Player other = (Player) o;
            return java.util.Objects.equals(name, other.name) && java.util.Objects.equals(status, other.status);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, status);
        }

        @Override
        public String toString() {
            return "Player[" + "name=" + name + ", status=" + status + "]";
        }
    }

    private Presence() {}

    public static String hash(String uuid) {
        String normalized = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("xpack-presence:" + normalized).getBytes(StandardCharsets.UTF_8));
            return J8.hex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Status parse(String wire) {
        switch (wire == null ? "" : wire) {
            case "linked":
                return Status.LINKED;
            case "installed":
                return Status.INSTALLED;
            default:
                return Status.UNKNOWN;
        }
    }
}
