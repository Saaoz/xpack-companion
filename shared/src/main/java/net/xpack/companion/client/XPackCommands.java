package net.xpack.companion.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Commandes /xpack : déclarées dans l'arbre du client pour les suggestions du chat, exécutées par
 * {@link XPackClient#handleLocalCommand} (jamais envoyées au serveur).
 */
public final class XPackCommands {
    /** Sous-commandes, dans l'ordre de l'aide. Leur description est la clé {@code xpack.command.<nom>}. */
    static final List<String> SUBCOMMANDS = List.of("settings", "diagnostic", "support", "help");

    private XPackCommands() {}

    public static void register(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        // executes(…) ne sert qu'à l'analyse du chat (sinon « commande incomplète » en rouge).
        LiteralArgumentBuilder<SharedSuggestionProvider> root = LiteralArgumentBuilder.<SharedSuggestionProvider>literal("xpack").executes(c -> 0);
        for (String sub : SUBCOMMANDS) root.then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal(sub).executes(c -> 0));
        dispatcher.register(root);
    }

    /** Une ligne par commande, cliquable (la commande s'écrit dans le chat) avec sa description. */
    static void help() {
        MinecraftUi.say(Texts.tr("xpack.command.help.title"));
        for (String sub : SUBCOMMANDS) {
            String command = "/xpack " + sub;
            Component line = Component.literal("  " + command).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Texts.tr("xpack.command.click"))))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Texts.tr("xpack.command." + sub).withStyle(ChatFormatting.GRAY));
            MinecraftUi.line(line);
        }
    }
}
