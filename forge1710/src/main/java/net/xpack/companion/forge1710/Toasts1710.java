package net.xpack.companion.forge1710;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.xpack.companion.core.Ui;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Toasts XPack, au même dessin que les autres versions (icône du progrès, rareté, points), dans le
 * coin choisi dans les réglages ; en bas à droite par défaut : en haut à droite, ils passaient
 * derrière la fenêtre d'achievement du jeu. L'alerte anti-triche reste en haut à droite. La 1.7.10 n'a pas de système de toasts (il date de la 1.12) : la pile, le
 * glissement d'entrée et de sortie sont dessinés ici. Les succès obtenus ensemble sont regroupés en
 * un seul toast ; un palier attend que le toast des succès soit parti.
 */
public final class Toasts1710 {
    private static final int HEIGHT = 32;
    private static final int GAP = 2;
    private static final int UNLOCK_WIDTH = 200;
    private static final int UNLOCK_HEIGHT = 40;
    /** Icônes affichées dans un récapitulatif, puis « +N ». */
    private static final int MAX_ICONS = 3;
    private static final int MARGIN = 4;
    private static final long SLIDE_MS = 300;
    private static final int GOLD = 0xFFD45A;
    private static final int SILVER = 0xD0D6DE;
    private static final int BRONZE = 0xD08A4A;

    private static final Ui.Unlock.Tr TR = Lang::tr;
    private static final RenderItem ITEMS = new RenderItem();

    /** Un toast à l'écran ou en attente. */
    private static final class Toast {
        static final int UNLOCK = 0, COMPLETION = 1, ALERT = 2;
        final int kind;
        final int width;
        final int height;
        final long displayMs;
        final String title;
        final String detail;
        final int color;
        final int tier;
        /** Succès d'un toast UNLOCK. */
        final List<Ui.Unlock> unlocks;
        long shownAt;

        Toast(int kind, int width, long displayMs, String title, String detail, int color, int tier) {
            this(kind, width, HEIGHT, displayMs, title, detail, color, tier, null);
        }

        Toast(int kind, int width, int height, long displayMs, String title, String detail, int color, int tier, List<Ui.Unlock> unlocks) {
            this.kind = kind;
            this.width = width;
            this.height = height;
            this.displayMs = displayMs;
            this.title = title;
            this.detail = detail;
            this.color = color;
            this.tier = tier;
            this.unlocks = unlocks;
        }
    }

    private static final List<Ui.Unlock> unlocks = new ArrayList<>();
    private static final Deque<Toast> alerts = new ArrayDeque<>();
    private static final Deque<Toast> completions = new ArrayDeque<>();
    private static final List<Toast> active = new ArrayList<>();

    static void unlocked(Ui.Unlock unlock) {
        unlocks.add(unlock);
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

    /** Appelé à chaque tick client : retire les toasts finis, en met de nouveaux à l'écran. */
    static void tick() {
        long now = System.currentTimeMillis();
        active.removeIf(t -> now - t.shownAt >= t.displayMs + SLIDE_MS);
        if (unlocks.isEmpty() && alerts.isEmpty() && completions.isEmpty()) return;
        if (!alerts.isEmpty()) show(alerts.poll(), now);
        if (!unlocks.isEmpty()) {
            List<Ui.Unlock> list = new ArrayList<>(unlocks);
            String rarest = Ui.Unlock.rarest(list).rarity;
            show(new Toast(Toast.UNLOCK, UNLOCK_WIDTH, UNLOCK_HEIGHT, 5_000, null, null, Ui.Unlock.color(rarest), 0, list), now);
            // Un seul son pour le toast : celui du plus rare des succès qu'il regroupe.
            Sounds1710.play(Client1710.core().config(), rarest, list.size());
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

    /** Objet icône du progrès ; une fiole d'expérience pour un succès sans progrès du jeu. */
    private static ItemStack icon(String id) {
        Object item = id == null ? null : Item.itemRegistry.getObject(id);
        return new ItemStack(item instanceof Item ? (Item) item : Items.experience_bottle);
    }

    /**
     * Toast « Succès XPack » (#39), même dessin que les autres versions : icône du progrès dans un
     * cadre de la rareté, rareté, part des joueurs, points, barre du temps d'affichage restant.
     */
    private static void drawUnlock(Minecraft mc, FontRenderer font, Toast t, long age) {
        List<Ui.Unlock> list = t.unlocks;
        int rgb = 0xFF000000 | t.color;
        boolean legendary = "legendaire".equals(Ui.Unlock.rarest(list).rarity);
        Gui.drawRect(0, 0, t.width, t.height, legendary ? rgb : 0xFF3D4B60);
        Gui.drawRect(1, 1, t.width - 1, t.height - 1, 0xF0141A23);
        Gui.drawRect(1, 1, t.width - 1, 3, rgb);

        // Icônes : une par succès (3 au plus, puis « +N »), dans un cadre de leur rareté.
        int x = 6;
        int shown = Math.min(list.size(), list.size() > MAX_ICONS ? MAX_ICONS - 1 : MAX_ICONS);
        for (int i = 0; i < shown; i++) {
            Ui.Unlock u = list.get(i);
            Gui.drawRect(x, 11, x + 20, 31, 0xFF000000 | Ui.Unlock.color(u.rarity));
            Gui.drawRect(x + 2, 13, x + 18, 29, 0xFF0B0F15);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            ITEMS.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), icon(u.item), x + 2, 13);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            x += 22;
        }
        if (list.size() > shown) {
            Gui.drawRect(x, 11, x + 20, 31, 0xFFA9B6C4);
            Gui.drawRect(x + 2, 13, x + 18, 29, 0xFF0B0F15);
            String more = "+" + (list.size() - shown);
            font.drawString(more, x + 10 - font.getStringWidth(more) / 2, 17, 0xD6DEE8);
            x += 22;
        }

        // Points à droite, puis les trois lignes de texte.
        String pts = "+" + Ui.Unlock.totalPoints(list);
        String unit = Lang.tr(Ui.Unlock.unitKey(list));
        font.drawString(pts, t.width - 6 - font.getStringWidth(pts), 10, 0xFFD166);
        font.drawString(unit, t.width - 6 - font.getStringWidth(unit), 21, 0xA3B1C2);
        int textX = x + 4;
        int textWidth = t.width - textX - 10 - Math.max(font.getStringWidth(pts), font.getStringWidth(unit));
        font.drawString(fit(font, Ui.Unlock.header(list, TR), textWidth), textX, 6, rgb);
        font.drawString(fit(font, Ui.Unlock.names(list), textWidth), textX, 17, 0xFFFFFF);
        font.drawString(fit(font, Ui.Unlock.detail(list, TR), textWidth), textX, 28, 0xA3B1C2);

        // Temps d'affichage restant.
        int bar = (int) Math.round((t.width - 2) * Math.max(0, 1 - age / (double) t.displayMs));
        Gui.drawRect(1, t.height - 3, t.width - 1, t.height - 1, 0xFF0B0F15);
        if (bar > 0) Gui.drawRect(1, t.height - 3, 1 + bar, t.height - 1, rgb);
    }

    /** Texte coupé à la largeur donnée, avec « … » s'il dépasse. */
    private static String fit(FontRenderer font, String text, int width) {
        return font.getStringWidth(text) > width ? font.trimStringToWidth(text, width) + "…" : text;
    }

    /** En jeu, sans écran ouvert. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || active.isEmpty() || Minecraft.getMinecraft().currentScreen != null) return;
        draw(event.resolution.getScaledWidth(), event.resolution.getScaledHeight());
    }

    /** Par-dessus un écran (réglages, inventaire, machine) : comme les toasts des versions récentes. */
    @SubscribeEvent
    public void onScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (active.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 300); // devant les objets dessinés par l'écran
        draw(res.getScaledWidth(), res.getScaledHeight());
        GL11.glPopMatrix();
    }

    /** Coin par défaut en 1.7.10 : le haut à droite est la place de la fenêtre d'achievement du jeu. */
    static final String DEFAULT_POSITION = "bottom_right";

    private static void draw(int screenWidth, int screenHeight) {
        String corner = Client1710.core() == null ? DEFAULT_POSITION : Client1710.core().config().toastPosition(DEFAULT_POSITION);
        List<Toast> alertList = new ArrayList<>();
        List<Toast> successes = new ArrayList<>();
        for (Toast t : active) (t.kind == Toast.ALERT ? alertList : successes).add(t);
        GL11.glEnable(GL11.GL_BLEND);
        // L'alerte anti-triche reste en haut à droite ; les succès vont dans le coin choisi.
        drawStack(alertList, "top_right", screenWidth, screenHeight);
        drawStack(successes, corner, screenWidth, screenHeight);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** Pile de toasts dans un coin : depuis le bord haut ou bas, entrée et sortie par le côté. */
    private static void drawStack(List<Toast> toasts, String corner, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        long now = System.currentTimeMillis();
        boolean left = corner.endsWith("_left");
        boolean bottom = corner.startsWith("bottom");
        int y = bottom ? screenHeight - MARGIN : MARGIN;
        for (Toast t : toasts) {
            long age = now - t.shownAt;
            // Glissement d'entrée au début, de sortie à la fin.
            double slide = age < SLIDE_MS ? age / (double) SLIDE_MS : age > t.displayMs ? Math.max(0, 1 - (age - t.displayMs) / (double) SLIDE_MS) : 1;
            slide = 1 - (1 - slide) * (1 - slide);
            int hidden = (int) ((1 - slide) * (t.width + MARGIN));
            int x = left ? MARGIN - hidden : screenWidth - MARGIN - t.width + hidden;
            if (bottom) y -= t.height;
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            if (t.kind == Toast.UNLOCK) {
                drawUnlock(mc, font, t, age);
            } else {
                Gui.drawRect(0, 0, t.width, t.height, 0xFF5B5B5B);
                Gui.drawRect(1, 1, t.width - 1, t.height - 1, t.kind == Toast.ALERT ? 0xF0101418 : 0xF0141A23);
            }
            if (t.kind == Toast.ALERT) {
                font.drawString(fit(font, t.title, t.width - 16), 8, 6, 0xFF000000 | t.color);
                font.drawString(fit(font, t.detail, t.width - 16), 8, 18, 0xFFFFFF);
            } else if (t.kind == Toast.COMPLETION) {
                // Médaille ★ d'un palier à la place de l'icône d'objet.
                Gui.drawRect(8, 8, 24, 24, 0xFF000000 | t.color);
                Gui.drawRect(10, 10, 22, 22, 0xFF2A2418);
                font.drawString("★", 13, 12, 0xFF000000 | t.color);
                font.drawString(fit(font, t.title, t.width - 36), 30, 7, 0xFF000000 | t.color);
                font.drawString(fit(font, t.detail, t.width - 36), 30, 18, 0xFFFFFF);
            }
            GL11.glPopMatrix();
            y = bottom ? y - GAP : y + t.height + GAP;
        }
    }
}
