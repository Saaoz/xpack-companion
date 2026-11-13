package net.xpack.companion.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

/**
 * Scénario de bout en bout exécuté dans le vrai client, uniquement avec {@code -Dxpack.smoke=true}
 * (tâche Gradle {@code runSmoke}, voir mod/README.md). Il rejoue ce que ferait un joueur :
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
    private static final Logger LOG = LogUtils.getLogger();

    private enum Step { WAIT_WORLD, SEND_CHEAT, EXPECT_WARNING, GRANT, WAIT_UPLOAD, PAUSE, QUIT, DONE }

    private static Step step = Step.WAIT_WORLD;
    private static int wait;
    private static boolean consentSeen;

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

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    static void tick(Minecraft mc, boolean inWorld) {
        if (!ENABLED || step == Step.DONE) return;
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
                    step = Step.SEND_CHEAT;
                    wait = 40;
                }
            }
            case SEND_CHEAT -> {
                mc.player.connection.sendCommand("give @s minecraft:diamond 64");
                step = Step.EXPECT_WARNING;
                wait = 5;
            }
            case EXPECT_WARNING -> {
                Screen screen = mc.screen;
                if (screen instanceof CheatWarningScreen warning) {
                    log("warning screen shown");
                    warning.confirmForTest();
                    step = Step.GRANT;
                    wait = 20;
                } else {
                    log("FAIL no warning screen (screen=" + screen + ")");
                    step = Step.QUIT;
                }
            }
            case GRANT -> {
                log("integrity=" + XPackClient.core().integrity().status());
                // Le monde est déjà signalé : plus d'alerte, la commande part directement.
                mc.player.connection.sendCommand("advancement grant @s only minecraft:story/root");
                mc.player.connection.sendCommand("advancement grant @s only minecraft:story/mine_stone");
                step = Step.WAIT_UPLOAD;
                wait = 20 * 15; // laisse passer au moins deux envois périodiques et une demande de stats
            }
            case WAIT_UPLOAD -> {
                log("status before quit=" + XPackClient.core().status());
                mc.setScreen(new PauseScreen(true));
                step = Step.PAUSE;
                wait = 20;
            }
            case PAUSE -> {
                log("pause screen=" + (mc.screen instanceof PauseScreen) + " badge=" + XPackClient.core().config().badge);
                shot(mc, "xpack-badge.png");
                step = Step.QUIT;
                wait = 5;
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
