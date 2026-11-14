package net.xpack.companion.forge1122;

import net.xpack.companion.legacy.Lang;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.toasts.AdvancementToast;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.xpack.companion.core.Ui;

/**
 * Toasts XPack, au même dessin que les autres versions (icône du progrès, rareté, points). Un
 * succès s'affiche dès la réponse du serveur (#210) ; paliers et alertes passent <b>après</b> ceux
 * du jeu. Les succès obtenus ensemble sont regroupés en un seul toast.
 */
public final class Toasts1122 {
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

    /** Instance abonnée aux événements de dessin (coin choisi hors haut à droite). */
    Toasts1122() {}

    public static void unlocked(Ui.Unlock unlock) {
        unlocks.add(unlock);
    }

    public static void alert(String reason) {
        alerts.add(new AlertToast(reason));
    }

    public static void completed(String modpackName, int achievements, long playtimeTicks) {
        long hours = Math.max(1, Math.round(playtimeTicks / 72_000.0));
        completions.add(new CompletionToast(100, GOLD, Lang.tr("xpack.toast.completed"), modpackName, Lang.tr("xpack.toast.completedDetail", achievements, hours)));
    }

    public static void milestone(String modpackName, int tier, int achievements, int total) {
        int color = tier >= 75 ? GOLD : tier >= 50 ? SILVER : BRONZE;
        completions.add(new CompletionToast(tier, color, Lang.tr("xpack.toast.milestone", tier), modpackName, Lang.tr("xpack.toast.milestoneDetail", achievements, total)));
    }

    /** Coin choisi dans les réglages ; en haut à droite, nos toasts passent par la file du jeu. */
    private static String corner() {
        return Client1122.core() == null ? "top_right" : Client1122.core().config().toastPosition("top_right");
    }

    /** Appelé à chaque tick client. */
    static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        placed.removeIf(p -> p.hiddenAt > 0 && now - p.hiddenAt >= SLIDE_MS);
        if (unlocks.isEmpty() && alerts.isEmpty() && completions.isEmpty()) return;
        GuiToast toasts = mc.getToastGui();
        boolean ownCorner = !"top_right".equals(corner());
        // L'alerte d'intégrité, en haut à droite avec le jeu, n'attend que lui.
        boolean gameBusy = toasts.getToast(AdvancementToast.class, IToast.NO_TOKEN) != null || queued(toasts) > 0;
        if (gameBusy) gameToastSeenAt = now;
        boolean gameQuiet = !gameBusy && now - gameToastSeenAt >= QUIET_MS;
        if (gameQuiet && !alerts.isEmpty()) toasts.add(alerts.poll());
        // Succès : tout de suite, sous le toast du jeu s'il est encore là (#210). Limite 1.12.2 : un toast
        // du jeu arrivé juste après peut couvrir les 8 derniers pixels du nôtre (emplacements de 32 px).
        if (!unlocks.isEmpty()) {
            show(toasts, new UnlockToast(new ArrayList<>(unlocks)), UnlockToast.HEIGHT, ownCorner, now);
            // Un seul son pour le toast : celui du plus rare des succès qu'il regroupe.
            Sounds1122.play(Client1122.core().config(), Ui.Unlock.rarest(unlocks).rarity, unlocks.size());
            unlocks.clear();
        }
        // Ailleurs qu'en haut à droite, rien ne se chevauche : pas besoin d'attendre le jeu.
        if (!ownCorner && !gameQuiet) return;
        // Palier (#60) : une fois le toast du dernier succès parti, avec son propre son.
        boolean unlockShown = toasts.getToast(UnlockToast.class, IToast.NO_TOKEN) != null || placed.stream().anyMatch(p -> p.toast instanceof UnlockToast);
        if (unlocks.isEmpty() && !completions.isEmpty() && !unlockShown) {
            CompletionToast toast = completions.poll();
            show(toasts, toast, CompletionToast.HEIGHT, ownCorner, now);
            if (toast.tier == 100) Sounds1122.playCompletion(Client1122.core().config());
            else Sounds1122.play(Client1122.core().config(), toast.tier == 75 ? "legendaire" : toast.tier == 50 ? "epique" : "rare");
        }
    }

    // ------------------------------------------------------------------ coin choisi (hors haut à droite)

    private static final long SLIDE_MS = 300;
    private static final int MARGIN = 4;
    private static final int GAP = 2;

    /** Un de nos toasts dessiné par nous, hors de la file du jeu. */
    private static final class Placed {
        final IToast toast;
        final int height;
        final long shownAt;
        /** Quand le toast a répondu « caché » : début du glissement de sortie (0 : encore affiché). */
        long hiddenAt;

        Placed(IToast toast, int height, long shownAt) {
            this.toast = toast;
            this.height = height;
            this.shownAt = shownAt;
        }
    }

    private static final List<Placed> placed = new ArrayList<>();

    private static void show(GuiToast toasts, IToast toast, int height, boolean ownCorner, long now) {
        if (ownCorner) placed.add(new Placed(toast, height, now));
        else toasts.add(toast);
    }

    /** En jeu, sans écran ouvert. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || placed.isEmpty() || Minecraft.getMinecraft().currentScreen != null) return;
        draw(event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
    }

    /** Par-dessus un écran, comme les toasts du jeu. */
    @SubscribeEvent
    public void onScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (placed.isEmpty()) return;
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300);
        draw(res.getScaledWidth(), res.getScaledHeight());
        GlStateManager.popMatrix();
    }

    /** Pile dans le coin choisi : depuis le bord haut ou bas, entrée et sortie par le côté. */
    private static void draw(int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getMinecraft();
        String corner = corner();
        boolean left = corner.endsWith("_left");
        boolean bottom = corner.startsWith("bottom");
        long now = System.currentTimeMillis();
        int width = UnlockToast.WIDTH;
        int y = bottom ? screenHeight - MARGIN : MARGIN;
        GlStateManager.enableBlend();
        for (Placed p : new ArrayList<>(placed)) {
            long age = now - p.shownAt;
            double slide = p.hiddenAt > 0 ? Math.max(0, 1 - (now - p.hiddenAt) / (double) SLIDE_MS) : Math.min(1, age / (double) SLIDE_MS);
            slide = 1 - (1 - slide) * (1 - slide);
            int hidden = (int) ((1 - slide) * (width + MARGIN));
            int x = left ? MARGIN - hidden : screenWidth - MARGIN - width + hidden;
            if (bottom) y -= p.height;
            GlStateManager.pushMatrix();
            // Nos toasts se décalent eux-mêmes vers la gauche (alignRight) pour la file du jeu : on compense.
            GlStateManager.translate(x + Math.max(0, width - SLOT), y, 0);
            GlStateManager.color(1f, 1f, 1f, 1f);
            if (p.toast.draw(mc.getToastGui(), age) == IToast.Visibility.HIDE && p.hiddenAt == 0) p.hiddenAt = now;
            GlStateManager.popMatrix();
            y = bottom ? y - GAP : y + p.height + GAP;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
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

    /** Cadre des toasts XPack (#183, #213) : fin (de la couleur pour un moment fort), filet de couleur en haut. */
    private static void frame(int width, int height, int rgb, boolean strong) {
        Gui.drawRect(0, 0, width, height, strong ? rgb : 0xFF3D4B60);
        Gui.drawRect(1, 1, width - 1, height - 1, 0xF0141A23);
        Gui.drawRect(1, 1, width - 1, 3, rgb);
    }

    /** Objet dans un cadre de 20 px à la couleur donnée, coin haut gauche en (x, y). */
    private static void iconCell(Minecraft mc, int x, int y, int rgb, ItemStack stack) {
        Gui.drawRect(x, y, x + 20, y + 20, rgb);
        Gui.drawRect(x + 2, y + 2, x + 18, y + 18, 0xFF0B0F15);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(null, stack, x + 2, y + 2);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Barre du temps d'affichage restant ({@code left} de 1 à 0). */
    private static void timeBar(int width, int height, int rgb, double left) {
        int bar = (int) Math.round((width - 2) * Math.max(0, Math.min(1, left)));
        Gui.drawRect(1, height - 3, width - 1, height - 1, 0xFF0B0F15);
        if (bar > 0) Gui.drawRect(1, height - 3, 1 + bar, height - 1, rgb);
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
            String unit = Lang.tr(Ui.Unlock.unitKey(unlocks));
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

    /**
     * Toast d'un palier du modpack (#60) : 200 × 40, 8 s, même dessin que celui d'un succès (#213) :
     * médaille-objet (brique, lingot de fer, lingot d'or, étoile du Nether à 100 %), palier, modpack et
     * chiffres, barre du temps restant.
     */
    private static final class CompletionToast implements IToast {
        private static final long DISPLAY_MS = 8_000;
        private static final int WIDTH = 200;
        static final int HEIGHT = 40;
        final int tier;
        private final int color;
        private final String title;
        private final String modpackName;
        private final String stats;

        CompletionToast(int tier, int color, String title, String modpackName, String stats) {
            this.tier = tier;
            this.color = color;
            this.title = title;
            this.modpackName = modpackName;
            this.stats = stats;
        }

        private ItemStack medal() {
            return new ItemStack(tier >= 100 ? Items.NETHER_STAR : tier >= 75 ? Items.GOLD_INGOT : tier >= 50 ? Items.IRON_INGOT : Items.BRICK);
        }

        @Override
        public Visibility draw(GuiToast gui, long delta) {
            Minecraft mc = gui.getMinecraft();
            FontRenderer font = mc.fontRenderer;
            int rgb = 0xFF000000 | color;
            alignRight(WIDTH);
            frame(WIDTH, HEIGHT, rgb, tier >= 100);
            iconCell(mc, 6, 11, rgb, medal());
            int textWidth = WIDTH - 36;
            font.drawString(fit(font, title, textWidth), 30, 6, rgb);
            if (modpackName != null) {
                font.drawString(fit(font, modpackName, textWidth), 30, 17, 0xFFFFFF);
                font.drawString(fit(font, stats, textWidth), 30, 28, 0xA3B1C2);
            } else {
                font.drawString(fit(font, stats, textWidth), 30, 17, 0xFFFFFF);
            }
            timeBar(WIDTH, HEIGHT, rgb, 1 - delta / (double) DISPLAY_MS);
            GlStateManager.color(1f, 1f, 1f, 1f);
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
            Minecraft mc = gui.getMinecraft();
            int rgb = 0xFFFFB347;
            alignRight(WIDTH);
            // 32 de haut : il tient dans un emplacement du jeu. Barrière dans un cadre orange (#213).
            frame(WIDTH, 32, rgb, false);
            iconCell(mc, 6, 6, rgb, new ItemStack(Item.getItemFromBlock(Blocks.BARRIER)));
            font.drawString(fit(font, Lang.tr("xpack.alert.title"), WIDTH - 38), 30, 7, rgb);
            font.drawString(fit(font, reason, WIDTH - 38), 30, 18, 0xFFFFFF);
            timeBar(WIDTH, 32, rgb, 1 - delta / (double) DISPLAY_MS);
            GlStateManager.color(1f, 1f, 1f, 1f);
            return delta >= DISPLAY_MS ? Visibility.HIDE : Visibility.SHOW;
        }
    }
}
