package net.xpack.companion.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Emplacement des fichiers d'état multijoueur ({@code <empreinte>.json} : identifiant de monde,
 * événements en attente). Ils sont rangés à côté des identifiants, <b>hors de l'instance</b> : le
 * dossier {@code config/} voyage avec les exports de modpack et les dépôts Git, pas le dossier de
 * l'utilisateur (voir {@link Credentials#defaultLocation()}).
 */
public final class ServerStore {
    private ServerStore() {}

    /** Dossier des fichiers multijoueur : {@code servers/} à côté du fichier des identifiants. */
    public static Path directory() {
        return directory(Credentials.defaultLocation());
    }

    static Path directory(Path credentialsFile) {
        Path parent = credentialsFile.toAbsolutePath().getParent();
        return (parent == null ? credentialsFile.toAbsolutePath() : parent).resolve("servers");
    }

    /**
     * Fichier d'état d'un serveur. Migre, une seule fois, l'ancien fichier de
     * {@code <config>/xpack/servers/} (déplacé, pas copié) ; un échec est journalisé et ignoré.
     */
    public static Path fileFor(String hash, Path legacyConfigDir) {
        return fileFor(directory(), hash, legacyConfigDir);
    }

    static Path fileFor(Path dir, String hash, Path legacyConfigDir) {
        Path target = dir.resolve(hash + ".json");
        if (legacyConfigDir != null) migrate(legacyConfigDir.resolve("xpack").resolve("servers").resolve(hash + ".json"), target);
        return target;
    }

    static void migrate(Path old, Path target) {
        try {
            if (!Files.isRegularFile(old)) return;
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.move(old, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.delete(old); // le nouvel emplacement fait foi : l'ancien fichier ne doit plus traîner dans config/
            }
        } catch (IOException | RuntimeException e) {
            XLog.warn("migration du stockage multijoueur impossible", e);
        }
    }
}
