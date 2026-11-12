package net.xpack.companion.forge1122;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.play.server.SPacketAdvancementInfo;
import net.minecraft.network.play.server.SPacketCollectItem;
import net.minecraft.network.play.server.SPacketStatistics;

/**
 * Lit les paquets du serveur sans les modifier ni les retenir : en 1.12.2 il n'y a pas de Mixin, on
 * s'insère donc dans le pipeline Netty de la connexion, juste avant le gestionnaire du jeu. Le
 * paquet continue toujours son chemin ; chaque lecture est reportée sur le fil du jeu.
 */
final class PacketSpy extends ChannelInboundHandlerAdapter {
    static final String NAME = "xpack_spy";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof SPacketAdvancementInfo) Client1122.onAdvancementPacket((SPacketAdvancementInfo) msg);
            else if (msg instanceof SPacketStatistics) Client1122.onStatsPacket((SPacketStatistics) msg);
            else if (msg instanceof SPacketCollectItem) Client1122.onCollectItemPacket((SPacketCollectItem) msg);
        } catch (RuntimeException e) {
            // Jamais gêner le jeu : au pire, ce paquet n'est pas compté.
        }
        ctx.fireChannelRead(msg);
    }
}
