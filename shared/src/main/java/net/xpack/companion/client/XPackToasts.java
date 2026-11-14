package net.xpack.companion.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.xpack.companion.core.Ui;
import net.xpack.companion.mixin.ToastComponentAccessor;

/**
 * Le toast d'un succès XPack s'affiche dès la réponse du serveur, sous le toast du jeu s'il est
 * encore là (#210). Paliers et alertes passent <b>après</b> les toasts du jeu. Les succès XPack
 * arrivés ensemble sont regroupés en une seule notification (« 3 succès XPack · +45 points »).
 */
final class XPackToasts {

    /** Petit délai après le dernier toast du jeu, pour ne pas s'intercaler dans une rafale de progrès. */
    private static final long QUIET_MS = 1_500;

    private static final List<Ui.Unlock> unlocks = new ArrayList<>();
    private static final Deque<IntegrityToast> alerts = new ArrayDeque<>();
    private static final Deque<CompletionToast> completions = new ArrayDeque<>();
    private static long gameToastSeenAt;

    private XPackToasts() {}

    // ------------------------------------------------------------------ coin choisi (hors haut à droite)

    private static final long SLIDE_MS = 300;
    private static final int MARGIN = 4;
    private static final int GAP = 2;

    /** Un de nos toasts dessiné par nous, hors de la file du jeu. */
    private static final class Placed {
        final Toast toast;
        final long shownAt;
        /** Quand le toast a répondu « caché » : début du glissement de sortie (0 : encore affiché). */
        long hiddenAt;

        Placed(Toast toast, long shownAt) {
            this.toast = toast;
            this.shownAt = shownAt;
        }
    }

    private static final List<Placed> placed = new ArrayList<>();

    private static void show(ToastComponent toasts, Toast toast, boolean ownCorner, long now) {
        if (ownCorner) placed.add(new Placed(toast, now));
        else toasts.addToast(toast);
    }

    /** Pile dans le coin choisi, après les toasts du jeu : depuis le bord haut ou bas, entrée et sortie par le côté. */
    static void render(GuiGraphics g, ToastComponent toasts) {
        // Interface masquée (F1) : comme les toasts du jeu.
        if (placed.isEmpty() || Minecraft.getInstance().options.hideGui) return;
        String corner = corner();
        boolean left = corner.endsWith("_left");
        boolean bottom = corner.startsWith("bottom");
        int screenWidth = g.guiWidth();
        long now = System.currentTimeMillis();
        int y = bottom ? g.guiHeight() - MARGIN : MARGIN;
        for (Placed p : List.copyOf(placed)) {
            long age = now - p.shownAt;
            double slide = p.hiddenAt > 0 ? Math.max(0, 1 - (now - p.hiddenAt) / (double) SLIDE_MS) : Math.min(1, age / (double) SLIDE_MS);
            slide = 1 - (1 - slide) * (1 - slide);
            int width = p.toast.width();
            int hidden = (int) ((1 - slide) * (width + MARGIN));
            int x = left ? MARGIN - hidden : screenWidth - MARGIN - width + hidden;
            if (bottom) y -= p.toast.height();
            g.pose().pushPose();
            g.pose().translate(x, y, 800);
            if (p.toast.render(g, toasts, age) == Toast.Visibility.HIDE && p.hiddenAt == 0) p.hiddenAt = now;
            g.pose().popPose();
            y = bottom ? y - GAP : y + p.toast.height() + GAP;
        }
    }

    static void unlocked(Ui.Unlock unlock) {
        unlocks.add(unlock);
    }

    static void alert(IntegrityToast toast) {
        alerts.add(toast);
    }

    static void completed(CompletionToast toast) {
        completions.add(toast);
    }

    /** Coin choisi dans les réglages ; en haut à droite, nos toasts passent par la file du jeu. */
    private static String corner() {
        return XPackClient.core() == null ? "top_right" : XPackClient.core().config().toastPosition("top_right");
    }

    /** File d'attente du jeu (mixin facultatif) : sans lui, on ne sait pas si le jeu a des toasts en attente, on suppose que non. */
    private static boolean queuedByGame(ToastComponent toasts) {
        try {
            return !((ToastComponentAccessor) toasts).xpack$queued().isEmpty();
        } catch (ClassCastException | LinkageError e) {
            return false;
        }
    }

    /** Appelé à chaque tick client. */
    static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        placed.removeIf(p -> p.hiddenAt > 0 && now - p.hiddenAt >= SLIDE_MS);
        if (unlocks.isEmpty() && alerts.isEmpty() && completions.isEmpty()) return;
        ToastComponent toasts = mc.getToasts();
        // Autre coin que le haut à droite : seulement si le mixin de dessin est actif (facultatif) ; sinon repli sur la file du jeu.
        boolean ownCorner = !"top_right".equals(corner()) && XPackClient.placedToastsRender();
        boolean gameBusy = toasts.getToast(AdvancementToast.class, Toast.NO_TOKEN) != null
                || queuedByGame(toasts);
        if (gameBusy) gameToastSeenAt = now;
        boolean gameQuiet = !gameBusy && now - gameToastSeenAt >= QUIET_MS;
        // L'alerte d'intégrité, en haut à droite avec le jeu : elle porte la touche du support, active pendant son affichage.
        if (gameQuiet && !alerts.isEmpty()) {
            toasts.addToast(alerts.poll());
            XPackClient.alertShown();
        }
        // Succès : tout de suite, même si le toast du jeu est encore là (#210). Depuis la 1.20, la file du
        // jeu réserve deux emplacements à notre toast de 40 px : il s'affiche dessous, sans chevauchement.
        if (!unlocks.isEmpty()) {
            show(toasts, XPackToast.of(List.copyOf(unlocks)), ownCorner, now);
            // Un seul son pour le toast : celui du plus rare des succès qu'il regroupe.
            int count = unlocks.size();
            unlocks.stream().map(u -> u.rarity).max(Comparator.comparingInt(Ui.Unlock::rank))
                    .ifPresent(r -> XPackSounds.play(XPackClient.core().config(), r, count));
            unlocks.clear();
        }
        // Ailleurs qu'en haut à droite, rien ne se chevauche : pas besoin d'attendre le jeu.
        if (!ownCorner && !gameQuiet) return;
        // Palier (#60) : une fois le toast du dernier succès parti, avec son propre son.
        boolean unlockShown = toasts.getToast(XPackToast.class, Toast.NO_TOKEN) != null || placed.stream().anyMatch(p -> p.toast instanceof XPackToast);
        if (unlocks.isEmpty() && !completions.isEmpty() && !unlockShown) {
            CompletionToast toast = completions.poll();
            show(toasts, toast, ownCorner, now);
            if (toast.tier() == 100) XPackSounds.playCompletion(XPackClient.core().config());
            else XPackSounds.play(XPackClient.core().config(), toast.tier() == 75 ? "legendaire" : toast.tier() == 50 ? "epique" : "rare");
        }
    }
}
