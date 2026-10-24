package net.xpack.companion.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

/**
 * Scénario de bout en bout exécuté dans le vrai client, uniquement avec {@code -Dxpack.smoke=true}
 * (tâche Gradle {@code runSmoke}, voir mod/README.md). Il rejoue ce que ferait un joueur :
 * commande de triche → alerte → confirmation → progrès accordés → envoi au serveur, puis quitte le jeu.
 * Chaque étape écrit une ligne « [SMOKE] » dans le journal, vérifiée par le script de test.
 */
final class SmokeTest {
    static final boolean ENABLED = Boolean.getBoolean("xpack.smoke");
    private static final Logger LOG = LogUtils.getLogger();

    private enum Step { WAIT_WORLD, SEND_CHEAT, EXPECT_WARNING, GRANT, WAIT_UPLOAD, QUIT, DONE }

    private static Step step = Step.WAIT_WORLD;
    private static int wait;

    private SmokeTest() {}

    static void log(String message) {
        if (ENABLED) LOG.info("[SMOKE] {}", message);
    }

    static void tick(Minecraft mc, boolean inWorld) {
        if (!ENABLED || step == Step.DONE) return;
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case WAIT_WORLD -> {
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
                step = Step.QUIT;
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
