package net.xpack.companion.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

/**
 * Scénario de bout en bout exécuté dans le vrai client, uniquement avec {@code -Dxpack.smoke=true}
 * (tâche Gradle {@code runSmoke}). Il rejoue ce que ferait un joueur :
 * commande de triche → alerte → confirmation → progrès accordés → envoi au serveur, puis quitte le jeu.
 * Chaque étape écrit une ligne « [SMOKE] » dans le journal, vérifiée par le script de test.
 */
final class SmokeTest {
    /**
     * Actif seulement avec {@code -Dxpack.smoke=true} <b>et</b> quand le code tourne depuis un dossier de classes (espace de
     * travail Gradle : {@code runSmoke}), jamais depuis un jar. Un jar de release (donc un profil de lanceur qui passerait
     * l'argument JVM) ne peut ainsi pas activer ce scénario, qui accepte le consentement et confirme l'alerte tout seul.
     */
    static final boolean ENABLED = Boolean.getBoolean("xpack.smoke") && runsFromClassesDirectory();
    /**
     * Test de CI sans API ni compte ({@code -Dxpack.smoke.ci=true}, voir .github/workflows/runtime-test.yml) :
     * une fois dans le monde local, le mod accorde un progrès via le serveur intégré et écrit {@link #CI_OK}
     * quand le client le voit passer par le mixin. Sans cette ligne, le workflow échoue.
     * Pas de garde « dossier de classes » ici, contrairement à {@link #ENABLED} : le workflow installe le jar
     * construit et le lance avec cet argument. Ce mode n'accepte aucun consentement et ne fait qu'accorder
     * {@link #CI_ADVANCEMENT} dans le monde local.
     */
    static final boolean CI = Boolean.getBoolean("xpack.smoke.ci");
    static final String CI_OK = "XPACK SMOKE OK";
    private static final String CI_ADVANCEMENT = "minecraft:story/root";
    private static final Logger LOG = LogUtils.getLogger();

    private static int ciWait = 20;
    private static boolean ciGranted;

    private enum Step { WAIT_WORLD, SEND_CHEAT, EXPECT_WARNING, GRANT, WAIT_UPLOAD, PAUSE, AUDIT, QUIT, DONE }

    private static Step step = Step.WAIT_WORLD;
    private static int wait;
    private static boolean consentSeen;
    /** Ticks passés dans l'étape courante : au-delà de {@link #STEP_DEADLINE}, le scénario est gelé. */
    private static int stepTicks;
    private static final int STEP_DEADLINE = 20 * 120; // 2 minutes par étape
    /** Attente avant de conclure que l'API XPack n'est pas là et de continuer hors ligne. */
    private static final int OFFLINE_GRACE = 20 * 20;
    /** Attente avant de conclure qu'aucune partie ne se chargera (laisse le temps au jeu de démarrer). */
    private static final int NO_WORLD_GRACE = 20 * 60;

    private SmokeTest() {}

    private static boolean runsFromClassesDirectory() {
        try {
            java.net.URL here = SmokeTest.class.getResource("SmokeTest.class");
            return here != null && "file".equals(here.getProtocol());
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void log(String message) {
        if (ENABLED) LOG.info("[SMOKE] {}", message);
    }

    /** Trace écrite aussi en CI : le workflow relit les lignes « xpack » du journal quand il échoue. */
    static void ciLog(String message) {
        if (ENABLED || CI) LOG.info("[SMOKE] {}", message);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    /** Appelé pour chaque liste de progrès terminés reçue du serveur (CI uniquement). */
    static void onAdvancements(java.util.List<String> doneIds) {
        if (CI && doneIds.contains(CI_ADVANCEMENT)) LOG.info("{}: advancement {} detected", CI_OK, CI_ADVANCEMENT);
    }

    /**
     * Ouvre un écran du mod et vérifie que chacune de ses options est utilisable : libellé traduit
     * (une clé non traduite s'affiche telle quelle), taille non nulle, position dans l'écran, et,
     * pour les boutons à valeurs, que les presser ne lève rien. Les boutons simples ne sont pas
     * pressés : certains délient le compte ou ouvrent le navigateur.
     * Chaque problème est écrit « FAIL », ce que le script de test relit.
     */
    private static void auditScreen(Minecraft mc, String name, Screen screen) {
        try {
            mc.setScreen(screen);
        } catch (RuntimeException | LinkageError e) {
            log("FAIL écran " + name + " : ouverture impossible (" + e + ")");
            return;
        }
        int widgets = 0;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget w) || !w.visible) continue;
            widgets++;
            String label = w.getMessage().getString();
            if (label.startsWith("xpack.")) {
                log("FAIL écran " + name + " : libellé non traduit « " + label + " »");
            }
            if (w.getWidth() <= 0 || w.getHeight() <= 0) {
                log("FAIL écran " + name + " : option « " + label + " » de taille nulle");
            }
            if (w.getX() < 0 || w.getY() < 0
                    || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height) {
                log("FAIL écran " + name + " : option « " + label + " » hors de l'écran");
            }
            if (w instanceof CycleButton<?> cycle && w.active) {
                try {
                    cycle.onPress();
                } catch (RuntimeException | LinkageError e) {
                    log("FAIL écran " + name + " : l'option « " + label + " » lève " + e);
                }
            }
        }
        log("audit " + name + " : " + widgets + " option(s) visibles");
        if (widgets == 0) log("FAIL écran " + name + " : aucune option affichée");
    }

    private static void ciTick(Minecraft mc, boolean inWorld) {
        if (ciGranted || !inWorld || mc.getSingleplayerServer() == null) return;
        if (ciWait-- > 0) return; // laisse le client finir de rejoindre le monde
        ciGranted = true;
        var server = mc.getSingleplayerServer();
        LOG.info("XPACK SMOKE: mod loaded, granting {}", CI_ADVANCEMENT);
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "advancement grant @a only " + CI_ADVANCEMENT));
    }

    /** Passe à l'étape suivante et relance le chien de garde. */
    private static void go(Step next, int pause) {
        step = next;
        wait = pause;
        stepTicks = 0;
    }

    static void tick(Minecraft mc, boolean inWorld) {
        if (CI) ciTick(mc, inWorld);
        if (!ENABLED || step == Step.DONE) return;
        // Chien de garde : sans lui, une étape qui n'avance jamais laisse le client tourner
        // jusqu'au délai du script, sans dire où ça a bloqué.
        if (++stepTicks > STEP_DEADLINE && step != Step.QUIT) {
            log("FAIL gel : l'étape " + step + " n'a pas abouti en " + (STEP_DEADLINE / 20) + "s");
            go(Step.QUIT, 0);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case WAIT_WORLD -> {
                // Premier lancement : écran de consentement (#40), accepté comme le ferait le joueur.
                if (mc.screen instanceof ConsentScreen consent) {
                    if (!consentSeen) {
                        consentSeen = true;
                        log("consent screen shown");
                        wait = 40; // laisse l'écran s'afficher avant la capture
                        return;
                    }
                    shot(mc, "xpack-consent.png");
                    consent.acceptForTest();
                    wait = 10;
                    return;
                }
                if (inWorld && XPackClient.core().status() != net.xpack.companion.core.XPackCore.Status.OFFLINE) {
                    log("in world, status=" + XPackClient.core().status());
                    go(Step.SEND_CHEAT, 40);
                } else if (inWorld && stepTicks > OFFLINE_GRACE) {
                    // Sans l'API XPack (dépôt privé), le statut reste OFFLINE : on continue quand même.
                    // Tout ce qui ne dépend pas du serveur reste vérifié : alerte de triche, écrans,
                    // options, crash et gel. Seule la vérification de l'envoi n'a plus de sens.
                    log("hors ligne : API XPack absente, suite du scénario sans vérifier l'envoi");
                    go(Step.SEND_CHEAT, 40);
                } else if (!inWorld && stepTicks > NO_WORLD_GRACE) {
                    // Pas de monde « SmokeWorld » dans run-smoke/saves : --quickPlaySingleplayer
                    // laisse le client à l'écran titre. C'est le cas d'un poste de développement du
                    // dépôt public, le monde étant créé par le test de bout en bout privé. On fait
                    // alors ce qui ne demande pas de partie : l'audit des écrans et des options.
                    log("pas de monde SmokeWorld : audit des écrans seulement");
                    go(Step.AUDIT, 20);
                }
            }
            case SEND_CHEAT -> {
                mc.player.connection.sendCommand("give @s minecraft:diamond 64");
                go(Step.EXPECT_WARNING, 5);
            }
            case EXPECT_WARNING -> {
                Screen screen = mc.screen;
                if (screen instanceof CheatWarningScreen warning) {
                    log("warning screen shown");
                    warning.confirmForTest();
                    go(Step.GRANT, 20);
                } else {
                    log("FAIL no warning screen (screen=" + screen + ")");
                    go(Step.QUIT, 0);
                }
            }
            case GRANT -> {
                log("integrity=" + XPackClient.core().integrity().status());
                // Le monde est déjà signalé : plus d'alerte, la commande part directement.
                mc.player.connection.sendCommand("advancement grant @s only minecraft:story/root");
                mc.player.connection.sendCommand("advancement grant @s only minecraft:story/mine_stone");
                go(Step.WAIT_UPLOAD, 20 * 15); // laisse passer au moins deux envois périodiques et une demande de stats
            }
            case WAIT_UPLOAD -> {
                log("status before quit=" + XPackClient.core().status());
                mc.setScreen(new PauseScreen(true));
                go(Step.PAUSE, 20);
            }
            case PAUSE -> {
                log("pause screen=" + (mc.screen instanceof PauseScreen) + " badge=" + XPackClient.core().config().badge);
                shot(mc, "xpack-badge.png");
                go(Step.AUDIT, 5);
            }
            case AUDIT -> {
                // En dernier : l'audit presse des boutons et change donc des réglages.
                auditScreen(mc, "settings", new SettingsScreen(null));
                auditScreen(mc, "support", new SupportScreen(null));
                auditScreen(mc, "team", new TeamScreen(null));
                auditScreen(mc, "speedrun", new SpeedrunScreen(null));
                mc.setScreen(null);
                go(Step.QUIT, 5);
            }
            case QUIT -> {
                log("quitting");
                step = Step.DONE;
                mc.stop();
            }
            case DONE -> {}
        }
    }
}
