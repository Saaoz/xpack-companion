package net.xpack.companion.forge1710;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/**
 * /xpack côté client : déclarée auprès de Forge pour la complétion avec Tab (les sous-commandes
 * sont proposées), exécutée par {@link Client1710#handleLocalCommand} et jamais envoyée au serveur.
 */
final class XPackCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "xpack";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/xpack <" + String.join("|", Client1710.SUBCOMMANDS) + ">";
    }

    /** Disponible pour tous : le mod ne donne aucun pouvoir sur le monde. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Client1710.handleLocalCommand(args.length == 0 ? "xpack" : "xpack " + String.join(" ", args));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, Client1710.SUBCOMMANDS.toArray(new String[0])) : Collections.emptyList();
    }
}
