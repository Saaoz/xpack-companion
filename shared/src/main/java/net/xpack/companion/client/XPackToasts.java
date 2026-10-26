package net.xpack.companion.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.xpack.companion.mixin.ToastComponentAccessor;

/**
 * Nos notifications passent <b>après</b> celles du jeu : on n'en affiche une que lorsqu'aucun
 * progrès vanilla n'est à l'écran et que la file du jeu est vide. Les succès XPack arrivés
 * ensemble sont regroupés en une seule notification (« 3 succès XPack · +45 points »).
 */
final class XPackToasts {
    record Unlock(String name, int points, String rarity) {}

    /** Petit délai après le dernier toast du jeu, pour ne pas s'intercaler dans une rafale de progrès. */
    private static final long QUIET_MS = 1_500;

    private static final List<Unlock> unlocks = new ArrayList<>();
    private static final Deque<IntegrityToast> alerts = new ArrayDeque<>();
    private static long gameToastSeenAt;

    private XPackToasts() {}

    static void unlocked(String name, int points, String rarity) {
        unlocks.add(new Unlock(name, points, rarity));
    }

    static void alert(IntegrityToast toast) {
        alerts.add(toast);
    }

    /** Appelé à chaque tick client. */
    static void tick(Minecraft mc) {
        if (unlocks.isEmpty() && alerts.isEmpty()) return;
        ToastComponent toasts = mc.getToasts();
        long now = System.currentTimeMillis();
        boolean gameBusy = toasts.getToast(AdvancementToast.class, Toast.NO_TOKEN) != null
                || !((ToastComponentAccessor) toasts).xpack$queued().isEmpty();
        if (gameBusy) {
            gameToastSeenAt = now;
            return;
        }
        if (now - gameToastSeenAt < QUIET_MS) return;
        // L'alerte d'intégrité d'abord : elle porte la touche du support, active pendant son affichage.
        if (!alerts.isEmpty()) {
            toasts.addToast(alerts.poll());
            XPackClient.alertShown();
        }
        if (!unlocks.isEmpty()) {
            toasts.addToast(XPackToast.of(List.copyOf(unlocks)));
            unlocks.clear();
        }
    }
}
