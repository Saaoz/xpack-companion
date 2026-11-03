package net.xpack.companion.forge1122;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.toasts.AdvancementToast;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.xpack.companion.core.Ui;

/**
 * Toasts XPack, au même dessin que les autres versions (icône du progrès, rareté, points). Ils
 * passent <b>après</b> ceux du jeu (aucun progrès vanilla à l'écran ni en attente) et
 * les succès obtenus ensemble sont regroupés en un seul toast.
 */
final class Toasts1122 {
    /** Largeur de l'emplacement d'un toast dans le jeu : un toast plus large déborde vers la gauche. */
    private static final int SLOT = 160;
    /** Petit délai après le dernier toast du jeu, pour ne pas s'intercaler dans une rafale de progrès. */
    private static final long QUIET_MS = 1_500;

    private static final Ui.Unlock.Tr TR = Lang::tr;

    private static final List<Ui.Unlock> unlocks = new ArrayList<>();
    private static final Deque<IToast> alerts = new ArrayDeque<>();
    private static final Deque<CompletionToast> completions = new ArrayDeque<>();
    private static long gameToastSeenAt;
    private static Field queueField;
    private static boolean lookedUp;

    private Toasts1122() {}

    static void unlocked(Ui.Unlock unlock) {
        unlocks.add(unlock);
    }

    static void alert(String reason) {
        alerts.add(new AlertToast(reason));
    }

    static void completed(String modpackName, int achievements, long playtimeTicks) {
        long hours = Math.max(1, Math.round(playtimeTicks / 72_000.0));
        completions.add(new CompletionToast(100, GOLD, Lang.tr("xpack.toast.completed"), detail(modpackName, Lang.tr("xpack.toast.completedDetail", achievements, hours))));
    }

    static void milestone(String modpackName, int tier, int achievements, int total) {
        int color = tier >= 75 ? GOLD : tier >= 50 ? SILVER : BRONZE;
        completions.add(new CompletionToast(tier, color, Lang.tr("xpack.toast.milestone", tier), detail(modpackName, Lang.tr("xpack.toast.milestoneDetail", achievements, total))));
    }

    private static String detail(String modpackName, String stats) {
        return modpackName == null ? stats : modpackName + " · " + stats;
    }

    /** Appelé à chaque tick client. */
    static void tick(Minecraft mc) {
        if (unlocks.isEmpty() && alerts.isEmpty() && completions.isEmpty()) return;
        GuiToast toasts = mc.getToastGui();
        long now = System.currentTimeMillis();
        boolean gameBusy = toasts.getToast(AdvancementToast.class, IToast.NO_TOKEN) != null || queued(toasts) > 0;
        if (gameBusy) {
            gameToastSeenAt = now;
            return;
        }
        if (now - gameToastSeenAt < QUIET_MS) return;
        // L'alerte d'intégrité d'abord.
        if (!alerts.isEmpty()) toasts.add(alerts.poll());
        if (!unlocks.isEmpty()) {
            toasts.add(new UnlockToast(new ArrayList<>(unlocks)));
            // Un seul son pour le toast : celui du plus rare des succès qu'il regroupe.
            Sounds1122.play(Client1122.core().config(), Ui.Unlock.rarest(unlocks).rarity);
            unlocks.clear();
        }
        // Palier (#60) : une fois le toast du dernier succès parti, avec son propre son.
        if (unlocks.isEmpty() && !completions.isEmpty() && toasts.getToast(UnlockToast.class, IToast.NO_TOKEN) == null) {
            CompletionToast toast = completions.poll();
            toasts.add(toast);
            if (toast.tier == 100) Sounds1122.playCompletion(Client1122.core().config());
            else Sounds1122.play(Client1122.core().config(), toast.tier == 75 ? "legendaire" : toast.tier == 50 ? "epique" : "rare");
        }
    }

    /** Toasts du jeu en attente : la file est privée, lue par réflexion. */
    private static int queued(GuiToast toasts) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                queueField = ReflectionHelper.findField(GuiToast.class, "toastsQueue", "field_191792_h");
            } catch (RuntimeException e) {
                queueField = null;
            }
        }
        try {
            return queueField == null ? 0 : ((Collection<?>) queueField.get(toasts)).size();
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ succès

    /** Objet icône du progrès ; une fiole d'expérience pour un succès sans progrès du jeu. */
    private static ItemStack icon(String id) {
        Item item = id == null ? null : Item.getByNameOrId(id);
        return new ItemStack(item == null || item == Items.AIR ? Items.EXPERIENCE_BOTTLE : item);
    }

    /** Fond des toasts, aux couleurs des autres versions. */
    private static void background(int width, int height, boolean system) {
        Gui.drawRect(0, 0, width, height, 0xFF5B5B5B);
        Gui.drawRect(1, 1, width - 1, height - 1, system ? 0xF0101418 : 0xF0141A23);
    }

    /** Texte coupé à la largeur donnée, avec « … » s'il dépasse. */
    private static String fit(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) > width ? font.trimStringToWidth(text, width) + "…" : text;
    }

    private static void alignRight(int width) {
        if (width > SLOT) GlStateManager.translate(SLOT - width, 0, 0);
    }

    /**
     * Toast « Succès XPack » (#39) : 5 s, 200 × 40, même dessin que les autres versions (icône du
     * progrès, rareté, part des joueurs, points, barre du temps restant). Le jeu range ses toasts
     * tous les 32 pixels : un toast du jeu arrivé juste dessous en recouvre le bas.
     */
    private static final class UnlockToast implements IToast {
        private static final long DISPLAY_MS = 5_000;
        private static final int WIDTH = 200;
        private static final int HEIGHT = 40;
        private static final int MAX_ICONS = 3;
        private final List<Ui.Unlock> unlocks;
        private final Ui.Unlock rarest;

        UnlockToast(List<Ui.Unlock> unlocks) {
            this.unlocks = unlocks;
            this.rarest = Ui.Unlock.rarest(unlocks);
        }

        @Override
        public Visibility draw(GuiToast gui, long delta) {
            Minecraft mc = gui.getMinecraft();
            FontRenderer font = mc.fontRenderer;
            int rgb = 0xFF000000 | Ui.Unlock.color(rarest.rarity);
            alignRight(WIDTH);
            Gui.drawRect(0, 0, WIDTH, HEIGHT, "legendaire".equals(rarest.rarity) ? rgb : 0xFF3D4B60);
            Gui.drawRect(1, 1, WIDTH - 1, HEIGHT - 1, 0xF0141A23);
            Gui.drawRect(1, 1, WIDTH - 1, 3, rgb);

            // Icônes : une par succès (3 au plus, puis « +N »), dans un cadre de leur rareté.
            int x = 6;
            int shown = Math.min(unlocks.size(), unlocks.size() > MAX_ICONS ? MAX_ICONS - 1 : MAX_ICONS);
            for (int i = 0; i < shown; i++) {
                Ui.Unlock u = unlocks.get(i);
                Gui.drawRect(x, 11, x + 20, 31, 0xFF000000 | Ui.Unlock.color(u.rarity));
                Gui.drawRect(x + 2, 13, x + 18, 29, 0xFF0B0F15);
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(null, icon(u.item), x + 2, 13);
                RenderHelper.disableStandardItemLighting();
                x += 22;
            }
            if (unlocks.size() > shown) {
                Gui.drawRect(x, 11, x + 20, 31, 0xFFA9B6C4);
                Gui.drawRect(x + 2, 13, x + 18, 29, 0xFF0B0F15);
                String more = "+" + (unlocks.size() - shown);
                font.drawString(more, x + 10 - font.getStringWidth(more) / 2, 17, 0xD6DEE8);
                x += 22;
            }

            // Points à droite, puis les trois lignes de texte.
            String pts = "+" + Ui.Unlock.totalPoints(unlocks);
            String unit = Lang.tr("xpack.toast.points");
            font.drawString(pts, WIDTH - 6 - font.getStringWidth(pts), 10, 0xFFD166);
            font.drawString(unit, WIDTH - 6 - font.getStringWidth(unit), 21, 0xA3B1C2);
            int textX = x + 4;
            int textWidth = WIDTH - textX - 10 - Math.max(font.getStringWidth(pts), font.getStringWidth(unit));
            font.drawString(fit(font, Ui.Unlock.header(unlocks, TR), textWidth), textX, 6, rgb);
            font.drawString(fit(font, Ui.Unlock.names(unlocks), textWidth), textX, 17, 0xFFFFFF);
            font.drawString(fit(font, Ui.Unlock.detail(unlocks, TR), textWidth), textX, 28, 0xA3B1C2);

            // Temps d'affichage restant.
            int bar = (int) Math.round((WIDTH - 2) * Math.max(0, 1 - delta / (double) DISPLAY_MS));
            Gui.drawRect(1, HEIGHT - 3, WIDTH - 1, HEIGHT - 1, 0xFF0B0F15);
            if (bar > 0) Gui.drawRect(1, HEIGHT - 3, 1 + bar, HEIGHT - 1, rgb);
            GlStateManager.color(1f, 1f, 1f, 1f);
            return delta >= DISPLAY_MS ? Visibility.HIDE : Visibility.SHOW;
        }
    }

    private static final int GOLD = 0xFFD45A;
    private static final int SILVER = 0xD0D6DE;
    private static final int BRONZE = 0xD08A4A;

    /** Toast d'un palier du modpack (#60) : plus large (200) et plus long (8 s). */
    private static final class CompletionToast implements IToast {
        private static final long DISPLAY_MS = 8_000;
        private static final int WIDTH = 200;
        final int tier;
        private final int color;
        private final String title;
        private final String detail;

        CompletionToast(int tier, int color, String title, String detail) {
            this.tier = tier;
            this.color = color;
            this.title = title;
            this.detail = detail;
        }

        @Override
        public Visibility draw(GuiToast gui, long delta) {
            FontRenderer font = gui.getMinecraft().fontRenderer;
            alignRight(WIDTH);
            background(WIDTH, 32, false);
            // Médaille : pastille à la couleur du palier à la place de l'icône d'objet.
            Gui.drawRect(8, 8, 24, 24, 0xFF000000 | color);
            Gui.drawRect(10, 10, 22, 22, 0xFF2A2418);
            font.drawString("★", 13, 12, 0xFF000000 | color);
            font.drawString(title, 30, 7, 0xFF000000 | color);
            font.drawString(fit(font, detail, WIDTH - 36), 30, 18, 0xFFFFFF);
            return delta >= DISPLAY_MS ? Visibility.HIDE : Visibility.SHOW;
        }
    }

    /** Alerte « ce monde n'est plus comptabilisé » : 20 s, 220 de large, la raison sur une ligne (le chat la donne en entier). */
    private static final class AlertToast implements IToast {
        private static final long DISPLAY_MS = 20_000;
        private static final int WIDTH = 220;
        private final String reason;

        AlertToast(String reason) {
            this.reason = reason;
        }

        @Override
        public Visibility draw(GuiToast gui, long delta) {
            FontRenderer font = gui.getMinecraft().fontRenderer;
            alignRight(WIDTH);
            background(WIDTH, 32, true);
            font.drawString(fit(font, Lang.tr("xpack.alert.title"), WIDTH - 16), 8, 6, 0xFFFFB347);
            font.drawString(fit(font, reason, WIDTH - 16), 8, 18, 0xFFFFFF);
            return delta >= DISPLAY_MS ? Visibility.HIDE : Visibility.SHOW;
        }
    }
}
