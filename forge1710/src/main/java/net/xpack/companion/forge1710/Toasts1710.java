package net.xpack.companion.forge1710;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

/**
 * Toasts XPack, au même dessin que les autres versions : pastille de rareté, titre coloré, détail en
 * blanc, coin haut droit. La 1.7.10 n'a pas de système de toasts (il date de la 1.12) : la pile, le
 * glissement d'entrée et de sortie sont dessinés ici. Les succès obtenus ensemble sont regroupés en
 * un seul toast ; un palier attend que le toast des succès soit parti.
 */
public final class Toasts1710 {
    private static final int HEIGHT = 32;
    private static final int GAP = 2;
    private static final int MARGIN = 4;
    private static final long SLIDE_MS = 300;
    private static final int GOLD = 0xFFD45A;
    private static final int SILVER = 0xD0D6DE;
    private static final int BRONZE = 0xD08A4A;

    private static final class Unlock {
        final String name;
        final int points;
        final String rarity;

        Unlock(String name, int points, String rarity) {
            this.name = name;
            this.points = points;
            this.rarity = rarity;
        }
    }

    /** Un toast à l'écran ou en attente. */
    private static final class Toast {
        static final int UNLOCK = 0, COMPLETION = 1, ALERT = 2;
        final int kind;
        final int width;
        final long displayMs;
        final String title;
        final String detail;
        final int color;
        final int tier;
        long shownAt;

        Toast(int kind, int width, long displayMs, String title, String detail, int color, int tier) {
            this.kind = kind;
            this.width = width;
            this.displayMs = displayMs;
            this.title = title;
            this.detail = detail;
            this.color = color;
            this.tier = tier;
        }
    }

    private static final List<Unlock> unlocks = new ArrayList<>();
    private static final Deque<Toast> alerts = new ArrayDeque<>();
    private static final Deque<Toast> completions = new ArrayDeque<>();
    private static final List<Toast> active = new ArrayList<>();

    static void unlocked(String name, int points, String rarity) {
        unlocks.add(new Unlock(name, points, rarity));
    }

    static void alert(String reason) {
        alerts.add(new Toast(Toast.ALERT, 220, 20_000, Lang.tr("xpack.alert.title"), reason, 0xFFB347, 0));
    }

    static void completed(String modpackName, int achievements, long playtimeTicks) {
        long hours = Math.max(1, Math.round(playtimeTicks / 72_000.0));
        completions.add(new Toast(Toast.COMPLETION, 200, 8_000, Lang.tr("xpack.toast.completed"), detail(modpackName, Lang.tr("xpack.toast.completedDetail", achievements, hours)), GOLD, 100));
    }

    static void milestone(String modpackName, int tier, int achievements, int total) {
        int color = tier >= 75 ? GOLD : tier >= 50 ? SILVER : BRONZE;
        completions.add(new Toast(Toast.COMPLETION, 200, 8_000, Lang.tr("xpack.toast.milestone", tier), detail(modpackName, Lang.tr("xpack.toast.milestoneDetail", achievements, total)), color, tier));
    }

    private static String detail(String modpackName, String stats) {
        return modpackName == null ? stats : modpackName + " · " + stats;
    }

    private static int rank(String rarity) {
        switch (rarity) {
            case "peu_commun":
                return 1;
            case "rare":
                return 2;
            case "epique":
                return 3;
            case "legendaire":
                return 4;
            default:
                return 0;
        }
    }

    private static int color(String rarity) {
        switch (rarity) {
            case "peu_commun":
                return 0x4CC38A;
            case "rare":
                return 0x4FA3FF;
            case "epique":
                return 0xB47CFF;
            case "legendaire":
                return 0xFFB347;
            default:
                return 0xA9B6C4;
        }
    }

    /** Appelé à chaque tick client : retire les toasts finis, en met de nouveaux à l'écran. */
    static void tick() {
        long now = System.currentTimeMillis();
        active.removeIf(t -> now - t.shownAt >= t.displayMs + SLIDE_MS);
        if (unlocks.isEmpty() && alerts.isEmpty() && completions.isEmpty()) return;
        if (!alerts.isEmpty()) show(alerts.poll(), now);
        if (!unlocks.isEmpty()) {
            show(unlockToast(new ArrayList<>(unlocks)), now);
            // Un seul son pour le toast : celui du plus rare des succès qu'il regroupe.
            String rarest = "commun";
            for (Unlock u : unlocks) if (rank(u.rarity) > rank(rarest)) rarest = u.rarity;
            Sounds1710.play(Client1710.core().config(), rarest);
            unlocks.clear();
        }
        // Palier (#60) : une fois le toast du dernier succès parti, avec son propre son.
        if (unlocks.isEmpty() && !completions.isEmpty() && active.stream().noneMatch(t -> t.kind == Toast.UNLOCK)) {
            Toast t = completions.poll();
            show(t, now);
            if (t.tier == 100) Sounds1710.playCompletion(Client1710.core().config());
            else Sounds1710.play(Client1710.core().config(), t.tier == 75 ? "legendaire" : t.tier == 50 ? "epique" : "rare");
        }
    }

    private static void show(Toast t, long now) {
        t.shownAt = now;
        active.add(t);
    }

    private static Toast unlockToast(List<Unlock> list) {
        // La couleur du plus rare des succès obtenus.
        Unlock rarest = list.get(0);
        for (Unlock u : list) if (rank(u.rarity) > rank(rarest.rarity)) rarest = u;
        if (list.size() == 1) return new Toast(Toast.UNLOCK, 160, 5_000, Lang.tr("xpack.toast.title", rarest.points), rarest.name, color(rarest.rarity), 0);
        int points = 0;
        List<String> names = new ArrayList<>();
        for (Unlock u : list) {
            points += u.points;
            names.add(u.name);
        }
        return new Toast(Toast.UNLOCK, 160, 5_000, Lang.tr("xpack.toast.summary", list.size(), points), String.join(", ", names), color(rarest.rarity), 0);
    }

    /** Texte coupé à la largeur donnée, avec « … » s'il dépasse. */
    private static String fit(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) > width ? font.trimStringToWidth(text, width) + "…" : text;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || active.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        long now = System.currentTimeMillis();
        int screenWidth = event.resolution.getScaledWidth();
        int y = MARGIN;
        GL11.glEnable(GL11.GL_BLEND);
        for (Toast t : new ArrayList<>(active)) {
            long age = now - t.shownAt;
            // Glissement d'entrée au début, de sortie à la fin.
            double slide = age < SLIDE_MS ? age / (double) SLIDE_MS : age > t.displayMs ? Math.max(0, 1 - (age - t.displayMs) / (double) SLIDE_MS) : 1;
            slide = 1 - (1 - slide) * (1 - slide);
            int x = screenWidth - MARGIN - t.width + (int) ((1 - slide) * (t.width + MARGIN));
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            Gui.drawRect(0, 0, t.width, HEIGHT, 0xFF5B5B5B);
            Gui.drawRect(1, 1, t.width - 1, HEIGHT - 1, t.kind == Toast.ALERT ? 0xF0101418 : 0xF0141A23);
            if (t.kind == Toast.ALERT) {
                font.drawString(fit(font, t.title, t.width - 16), 8, 6, 0xFF000000 | t.color);
                font.drawString(fit(font, t.detail, t.width - 16), 8, 18, 0xFFFFFF);
            } else {
                // Pastille de rareté (ou médaille ★ d'un palier) à la place de l'icône d'objet.
                Gui.drawRect(8, 8, 24, 24, 0xFF000000 | t.color);
                Gui.drawRect(10, 10, 22, 22, t.kind == Toast.COMPLETION ? 0xFF2A2418 : 0xFF141A23);
                if (t.kind == Toast.COMPLETION) font.drawString("★", 13, 12, 0xFF000000 | t.color);
                font.drawString(fit(font, t.title, t.width - 36), 30, 7, 0xFF000000 | t.color);
                font.drawString(fit(font, t.detail, t.width - 36), 30, 18, 0xFFFFFF);
            }
            GL11.glPopMatrix();
            y += HEIGHT + GAP;
        }
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
