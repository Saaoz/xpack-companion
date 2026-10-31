package net.xpack.companion.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Jeu en groupe (#126) : les UUID Minecraft des joueurs ne partent jamais en clair, seulement leur
 * empreinte. Même calcul que le serveur ({@code presenceHash} dans {@code server/src/presence.ts}).
 */
public final class Presence {
    /** Statut d'un autre joueur pour {@code /xpack check}. */
    public enum Status { LINKED, INSTALLED, UNKNOWN }

    public record Player(String name, Status status) {}

    private Presence() {}

    public static String hash(String uuid) {
        String normalized = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("xpack-presence:" + normalized).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Status parse(String wire) {
        return switch (wire == null ? "" : wire) {
            case "linked" -> Status.LINKED;
            case "installed" -> Status.INSTALLED;
            default -> Status.UNKNOWN;
        };
    }
}
