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
    static final boolean ENABLED = Boolean.getBoolean("xpack.smoke");
    /**
     * Test de CI sans API ni compte ({@code -Dxpack.smoke.ci=true}, voir .github/workflows/runtime-test.yml) :
     * une fois dans le monde local, le mod accorde un progrès via le serveur intégré et écrit {@link #CI_OK}
     * quand le client le voit passer par le mixin. Sans cette ligne, le workflow échoue.
     */
    static final boolean CI = Boolean.getBoolean("xpack.smoke.ci");
    static final String CI_OK = "XPACK SMOKE OK";
    private static final String CI_ADVANCEMENT = "minecraft:story/root";
    private static final Logger LOG = LogUtils.getLogger();

    private static int ciWait = 20;
    private static boolean ciGranted;

    private enum Step { WAIT_WORLD, SEND_CHEAT, EXPECT_WARNING, GRANT, WAIT_UPLOAD, PAUSE, QUIT, DONE }

    private static Step step = Step.WAIT_WORLD;
    private static int wait;
    private static boolean consentSeen;

    private SmokeTest() {}

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

    private static void ciTick(Minecraft mc, boolean inWorld) {
        if (ciGranted || !inWorld || mc.getSingleplayerServer() == null) return;
        if (ciWait-- > 0) return; // laisse le client finir de rejoindre le monde
        ciGranted = true;
        var server = mc.getSingleplayerServer();
        LOG.info("XPACK SMOKE: mod loaded, granting {}", CI_ADVANCEMENT);
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "advancement grant @a only " + CI_ADVANCEMENT));
    }

    static void tick(Minecraft mc, boolean inWorld) {
        if (CI) ciTick(mc, inWorld);
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
