package net.xpack.companion.forge1122;

import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import javax.annotation.Nullable;

/**
 * /xpack côté client : déclarée auprès de Forge pour la complétion avec Tab (les sous-commandes
 * sont proposées), exécutée par {@link Client1122#handleLocalCommand} et jamais envoyée au serveur.
 */
final class XPackCommand extends CommandBase {
    @Override
    public String getName() {
        return "xpack";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/xpack <" + String.join("|", Client1122.SUBCOMMANDS) + ">";
    }

    /** Disponible pour tous : le mod ne donne aucun pouvoir sur le monde. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        Client1122.handleLocalCommand(args.length == 0 ? "xpack" : "xpack " + String.join(" ", args));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, Client1122.SUBCOMMANDS) : java.util.Collections.<String>emptyList();
    }
}