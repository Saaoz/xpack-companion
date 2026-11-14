package net.xpack.companion.forge1710;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.S0DPacketCollectItem;
import net.minecraft.network.play.server.S37PacketStatistics;

/**
 * Lit les paquets de la connexion sans les modifier : il n'y a pas de Mixin en 1.7.10, on s'insère
 * donc dans le pipeline Netty. Entrée : statistiques (et « achievements », qui voyagent avec elles)
 * et ramassages d'objets. Sortie : les commandes tapées, pour l'alerte avant une commande de triche.
 */
final class PacketSpy {
    static final String IN = "xpack_in";
    static final String OUT = "xpack_out";
    /** Commandes déjà confirmées par le joueur : elles repartent sans repasser par l'alerte. */
    private static final Set<String> CONFIRMED = ConcurrentHashMap.newKeySet();

    private PacketSpy() {}

    /** Autorise l'envoi de ce message, une seule fois (après confirmation de l'alerte). */
    static void allowOnce(String message) {
        CONFIRMED.add(message);
    }

    static final class Inbound extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                if (msg instanceof S37PacketStatistics) Client1710.onStatsPacket((S37PacketStatistics) msg);
                else if (msg instanceof S0DPacketCollectItem) Client1710.onCollectItemPacket((S0DPacketCollectItem) msg);
            } catch (RuntimeException | LinkageError e) {
                // Jamais gêner le jeu : au pire, ce paquet n'est pas compté.
            }
            ctx.fireChannelRead(msg);
        }
    }

    static final class Outbound extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof C01PacketChatMessage) {
                String text = ((C01PacketChatMessage) msg).func_149439_c();
                if (text.startsWith("/") && !CONFIRMED.remove(text) && !allowed(text.substring(1))) {
                    promise.setSuccess(); // retenue : l'alerte demande confirmation, puis renvoie la commande
                    return;
                }
            }
            ctx.write(msg, promise);
        }

        /** La décision se prend sur le fil du jeu (le cœur n'est pas prévu pour un autre) ; en cas de doute, la commande part. */
        private static boolean allowed(String command) {
            try {
                Callable<Boolean> decision = () -> Client1710.beforeCommand(command);
                return (Boolean) Minecraft.getMinecraft().func_152343_a(decision).get(2, TimeUnit.SECONDS);
            } catch (Exception | LinkageError e) {
                return true;
            }
        }
    }
}
