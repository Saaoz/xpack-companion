package net.xpack.companion.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Suivi des quêtes BetterQuesting d'un monde solo, pour une partie : le livre une fois, puis les
 * quêtes terminées chaque fois que BetterQuesting réécrit son fichier (sauvegarde du monde, pause).
 * Les fichiers sont lus hors du thread du jeu (GT: New Horizons : 14 Mo) ; les résultats passent au
 * cœur pendant {@link #tick()}, sur le thread du jeu.
 */
public final class QuestWatcher {
    static final int INTERVAL_TICKS = 100;
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XPack quêtes");
        t.setDaemon(true);
        return t;
    });

    private final XPackCore core;
    private final Path worldDir;
    private final String playerUuid;
    private final Function<String, String> translate;
    private final Supplier<String> lang;
    private final LongSupplier playtime;
    private final Executor background;
    private final Queue<Runnable> results = new ConcurrentLinkedQueue<>();
    private boolean catalogSent;
    private long lastModified = -1;
    private boolean busy;
    private int ticks;
    private volatile boolean closed;

    /**
     * {@code translate} : texte du jeu pour une clé de traduction (la clé telle quelle s'il ne la
     * connaît pas) ; {@code playtime} : temps de jeu des événements (#74).
     */
    public QuestWatcher(XPackCore core, Path worldDir, String playerUuid, Function<String, String> translate, Supplier<String> lang, LongSupplier playtime) {
        this(core, worldDir, playerUuid, translate, lang, playtime, READER);
    }

    QuestWatcher(XPackCore core, Path worldDir, String playerUuid, Function<String, String> translate, Supplier<String> lang, LongSupplier playtime, Executor background) {
        this.core = core;
        this.worldDir = worldDir;
        this.playerUuid = playerUuid;
        this.translate = translate;
        this.lang = lang;
        this.playtime = playtime;
        this.background = background;
    }

    /** Fin de la partie : les lectures encore en cours sont oubliées. */
    public void close() {
        closed = true;
        results.clear();
    }

    /** À chaque tick du client : applique les lectures finies, en lance une toutes les 5 s si besoin. */
    public void tick() {
        Runnable r;
        while (!closed && (r = results.poll()) != null) r.run();
        if (closed || busy || ticks++ % INTERVAL_TICKS != 0) return;
        // Modpack riche en progrès (300 et plus) : le livre ne compte pas, inutile de le lire.
        if (!core.wantsQuests()) return;
        Path progress = BetterQuesting.progressFile(worldDir, playerUuid);
        long modified = modified(progress);
        boolean readCatalog = !catalogSent;
        boolean readProgress = modified != lastModified;
        if (!readCatalog && !readProgress) return;
        busy = true;
        background.execute(() -> {
            List<BetterQuesting.Quest> quests = readCatalog ? BetterQuesting.readQuests(worldDir) : null;
            // Pas encore de fichier (monde neuf, jamais sauvegardé) : aucune quête terminée.
            Map<String, Long> done = !readProgress ? null : modified == 0 ? Collections.<String, Long>emptyMap() : BetterQuesting.readCompleted(worldDir, playerUuid);
            results.add(() -> {
                busy = false;
                if (quests != null && !quests.isEmpty()) {
                    core.onQuestCatalog(BetterQuesting.catalog(quests, translate), lang.get());
                    catalogSent = true;
                }
                if (done != null) { // null : fichier en cours d'écriture, relu au prochain passage
                    // Sans fichier, rien n'est terminé à cette heure-ci.
                    core.onQuests(done, modified == 0 ? System.currentTimeMillis() : modified, playtime.getAsLong());
                    lastModified = modified;
                }
            });
        });
    }

    private static long modified(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
