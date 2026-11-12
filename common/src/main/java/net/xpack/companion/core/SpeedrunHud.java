package net.xpack.companion.core;

/**
 * Place et taille de l'affichage du chrono à l'écran (#221). Pur Java : le calcul du coin d'ancrage, de l'échelle
 * et du décalage est testé ici, le dessin reste dans le client. Les valeurs de la configuration sont toujours
 * ramenées dans des bornes saines (un fichier édité à la main ne peut pas sortir le chrono de l'écran).
 */
public final class SpeedrunHud {
    /** Coin de l'écran auquel l'affichage est accroché. */
    public enum Anchor {
        TOP_LEFT("top_left", false, false),
        TOP_RIGHT("top_right", true, false),
        BOTTOM_LEFT("bottom_left", false, true),
        BOTTOM_RIGHT("bottom_right", true, true);

        public final String id;
        private final boolean right;
        private final boolean bottom;

        Anchor(String id, boolean right, boolean bottom) {
            this.id = id;
            this.right = right;
            this.bottom = bottom;
        }

        public boolean right() {
            return right;
        }

        public boolean bottom() {
            return bottom;
        }

        /** Coin suivant, dans l'ordre haut gauche, haut droit, bas gauche, bas droit. */
        public Anchor next() {
            Anchor[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        /** Le coin d'identifiant donné ; haut gauche (la place d'origine) si inconnu ou vide. */
        public static Anchor parse(String id) {
            for (Anchor a : values()) if (a.id.equals(id)) return a;
            return TOP_LEFT;
        }
    }

    public static final int MIN_SCALE = 50;
    public static final int MAX_SCALE = 200;
    public static final int DEFAULT_SCALE = 100;
    public static final int MAX_OFFSET = 400;
    public static final int DEFAULT_OFFSET = 6;
    /** Tailles proposées dans l'écran, en pourcentage. */
    public static final int[] SCALES = {50, 75, 100, 125, 150, 200};
    /** Décalages proposés dans l'écran, en pixels. */
    public static final int[] OFFSETS = {0, 6, 12, 24, 48, 96};

    private SpeedrunHud() {}

    public static int clampScale(int percent) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, percent));
    }

    public static int clampOffset(int pixels) {
        return Math.max(0, Math.min(MAX_OFFSET, pixels));
    }

    /** Taille suivante de la liste (la première après la dernière) ; une valeur hors liste passe à la suivante plus grande. */
    public static int nextScale(int percent) {
        return next(SCALES, clampScale(percent));
    }

    /** Décalage suivant de la liste, même règle que {@link #nextScale}. */
    public static int nextOffset(int pixels) {
        return next(OFFSETS, clampOffset(pixels));
    }

    private static int next(int[] steps, int value) {
        for (int s : steps) if (s > value) return s;
        return steps[0];
    }

    /**
     * Coin haut gauche, en pixels d'écran, du cadre du chrono une fois mis à l'échelle. {@code contentW} et
     * {@code contentH} sont la taille du contenu à l'échelle 100 %. Le décalage se compte depuis les bords du coin
     * choisi ; le cadre reste toujours dans l'écran.
     */
    public static int[] origin(Anchor anchor, int screenW, int screenH, int contentW, int contentH, int scalePercent, int offsetX, int offsetY) {
        int scale = clampScale(scalePercent);
        int w = (contentW * scale + 50) / 100;
        int h = (contentH * scale + 50) / 100;
        int ox = clampOffset(offsetX);
        int oy = clampOffset(offsetY);
        int x = anchor.right() ? screenW - w - ox : ox;
        int y = anchor.bottom() ? screenH - h - oy : oy;
        x = Math.max(0, Math.min(x, Math.max(0, screenW - w)));
        y = Math.max(0, Math.min(y, Math.max(0, screenH - h)));
        return new int[] {x, y};
    }

    public static Anchor anchor(Config c) {
        return Anchor.parse(c.speedrunHudAnchor);
    }

    public static int scale(Config c) {
        return clampScale(c.speedrunHudScale);
    }

    public static int offsetX(Config c) {
        return clampOffset(c.speedrunHudOffsetX);
    }

    public static int offsetY(Config c) {
        return clampOffset(c.speedrunHudOffsetY);
    }

    /** Remet la place d'origine : haut gauche, 100 %, 6 px de marge. */
    public static void reset(Config c) {
        c.speedrunHudAnchor = Anchor.TOP_LEFT.id;
        c.speedrunHudScale = DEFAULT_SCALE;
        c.speedrunHudOffsetX = DEFAULT_OFFSET;
        c.speedrunHudOffsetY = DEFAULT_OFFSET;
    }
}
