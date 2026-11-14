package net.xpack.companion.legacy;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.xpack.companion.core.Diagnostics;
import net.xpack.companion.core.J8;
import net.xpack.companion.core.ModpackDetector;
import net.xpack.companion.core.XLog;
import net.xpack.companion.core.XPackCore;

/**
 * Commandes /xpack, traitées dans le client et jamais envoyées au serveur : même logique en 1.12.2
 * et en 1.7.10. Chaque port y branche sa déclaration auprès de Forge ({@code XPackCommand}) et la
 * retenue des commandes avant l'envoi ; tout ce qui touche au jeu passe par {@link Platform}.
 */
public final class LocalCommand {
    /** Sous-commandes, dans l'ordre de l'aide. Leur description est la clé {@code xpack.command.<nom>}. */
    public static final List<String> SUBCOMMANDS = J8.listOf("settings", "toast", "team", "check", "diagnostic", "support", "help");

    private LocalCommand() {}

    /** Vrai si la commande est à nous (et donc traitée ici). */
    public static boolean handle(String command) {
        String c = command.trim();
        XPackCore core = Platform.core();
        if (core == null || !(c.equalsIgnoreCase("xpack") || c.toLowerCase(Locale.ROOT).startsWith("xpack "))) return false;
        String sub = c.length() > 5 ? c.substring(5).trim().toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "diagnostic":
            case "diag":
                runDiagnostic();
                break;
            case "check":
            case "verifier":
            case "vérifier":
                core.checkPresence();
                break;
            case "settings":
            case "reglages":
            case "réglages":
                Platform.defer(() -> Minecraft.getMinecraft().displayGuiScreen(new SettingsScreen(null)));
                break;
            case "toast":
            case "essai":
            case "test":
                core.previewToast(Lang.tr("xpack.toast.preview"));
                break;
            case "support":
                Platform.defer(() -> Minecraft.getMinecraft().displayGuiScreen(new SupportScreen(null)));
                break;
            case "team":
            case "equipe":
            case "équipe":
                Platform.defer(() -> Minecraft.getMinecraft().displayGuiScreen(new TeamScreen(null)));
                break;
            default:
                help();
        }
        return true;
    }

    private static void help() {
        LegacyUi.say(Lang.tr("xpack.command.help.title"));
        for (String sub : SUBCOMMANDS) LegacyUi.line("  " + Fmt.AQUA + "/xpack " + sub + Fmt.DARK_GRAY + " — " + Fmt.GRAY + Lang.tr("xpack.command." + sub));
    }

    /** /xpack diagnostic : fichiers du lanceur (anonymisés) + modpack détecté, dans un dossier à envoyer. */
    private static void runDiagnostic() {
        java.nio.file.Path gameDir = Platform.gameDir();
        List<String> modIds = Platform.modIds();
        ModpackDetector.ModpackInfo detected = ModpackDetector.detect(gameDir, modIds);
        String summary = "XPack Companion " + Platform.modVersion() + " (forge " + Platform.MC_VERSION + "), Minecraft " + Platform.MC_VERSION
                + "\nModpack détecté : " + Diagnostics.describe(detected) + "\nMods chargés : " + modIds.size();
        try {
            Diagnostics.Result r = Diagnostics.write(gameDir, gameDir.resolve("xpack-diagnostic"), summary, modIds, Clock.systemUTC());
            LegacyUi.say(Lang.tr("xpack.diagnostic.done", Diagnostics.describe(detected), r.found().isEmpty() ? "-" : String.join(", ", r.found())) + " " + r.folder().toAbsolutePath());
        } catch (IOException e) {
            XLog.warn("diagnostic non écrit", e);
            LegacyUi.say(Lang.tr("xpack.diagnostic.failed", e.getMessage()));
        }
    }
}
