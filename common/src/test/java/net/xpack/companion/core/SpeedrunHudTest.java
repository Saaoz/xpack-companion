package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeedrunHudTest {
    @Test
    void parDefautLAffichageResteOuIlEtaitEnHautAGauche() {
        Config c = new Config();
        assertEquals(SpeedrunHud.Anchor.TOP_LEFT, SpeedrunHud.anchor(c));
        assertEquals(100, SpeedrunHud.scale(c));
        assertArrayEquals(new int[] {6, 6}, SpeedrunHud.origin(SpeedrunHud.anchor(c), 400, 300, 90, 40, SpeedrunHud.scale(c), SpeedrunHud.offsetX(c), SpeedrunHud.offsetY(c)));
        assertFalse(c.speedrunEnabled);
    }

    @Test
    void lesQuatreCoinsPlacentLeCadreContreLesBonsBords() {
        assertArrayEquals(new int[] {10, 20}, SpeedrunHud.origin(SpeedrunHud.Anchor.TOP_LEFT, 400, 300, 100, 50, 100, 10, 20));
        assertArrayEquals(new int[] {290, 20}, SpeedrunHud.origin(SpeedrunHud.Anchor.TOP_RIGHT, 400, 300, 100, 50, 100, 10, 20));
        assertArrayEquals(new int[] {10, 230}, SpeedrunHud.origin(SpeedrunHud.Anchor.BOTTOM_LEFT, 400, 300, 100, 50, 100, 10, 20));
        assertArrayEquals(new int[] {290, 230}, SpeedrunHud.origin(SpeedrunHud.Anchor.BOTTOM_RIGHT, 400, 300, 100, 50, 100, 10, 20));
    }

    @Test
    void lEchelleAgrandiLeCadreVersLInterieurDeLEcran() {
        // 200 % : 200 x 100 pixels, collé au bord droit et au bord bas avec 10 px de marge.
        assertArrayEquals(new int[] {190, 190}, SpeedrunHud.origin(SpeedrunHud.Anchor.BOTTOM_RIGHT, 400, 300, 100, 50, 200, 10, 10));
        // 50 % : 50 x 25.
        assertArrayEquals(new int[] {340, 265}, SpeedrunHud.origin(SpeedrunHud.Anchor.BOTTOM_RIGHT, 400, 300, 100, 50, 50, 10, 10));
    }

    @Test
    void leCadreNeSortJamaisDeLEcran() {
        assertArrayEquals(new int[] {0, 0}, SpeedrunHud.origin(SpeedrunHud.Anchor.TOP_RIGHT, 100, 60, 300, 200, 200, 400, 400));
        int[] p = SpeedrunHud.origin(SpeedrunHud.Anchor.TOP_LEFT, 400, 300, 100, 50, 100, 400, 400);
        assertTrue(p[0] + 100 <= 400 && p[1] + 50 <= 300);
    }

    @Test
    void lesValeursDeLaConfigurationSontBornees() {
        Config c = new Config();
        c.speedrunHudScale = 5000;
        c.speedrunHudOffsetX = -30;
        c.speedrunHudOffsetY = 99999;
        c.speedrunHudAnchor = "au milieu";
        assertEquals(SpeedrunHud.MAX_SCALE, SpeedrunHud.scale(c));
        assertEquals(0, SpeedrunHud.offsetX(c));
        assertEquals(SpeedrunHud.MAX_OFFSET, SpeedrunHud.offsetY(c));
        assertEquals(SpeedrunHud.Anchor.TOP_LEFT, SpeedrunHud.anchor(c));
        c.speedrunHudScale = 1;
        assertEquals(SpeedrunHud.MIN_SCALE, SpeedrunHud.scale(c));
    }

    @Test
    void leMenuFaitLeTourDesTaillesEtDesDecalages() {
        int s = 100;
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < SpeedrunHud.SCALES.length; i++) {
            s = SpeedrunHud.nextScale(s);
            seen.append(s).append(' ');
        }
        assertEquals("125 150 200 50 75 100 ", seen.toString());
        assertEquals(12, SpeedrunHud.nextOffset(6));
        assertEquals(0, SpeedrunHud.nextOffset(96));
        assertEquals(100, SpeedrunHud.nextScale(90)); // hors liste : la suivante plus grande
        assertEquals(50, SpeedrunHud.nextScale(200));
    }

    @Test
    void lesCoinsFontLeTourEtLIdentifiantSeRelit() {
        SpeedrunHud.Anchor a = SpeedrunHud.Anchor.TOP_LEFT;
        for (int i = 0; i < 4; i++) {
            assertEquals(a, SpeedrunHud.Anchor.parse(a.id));
            a = a.next();
        }
        assertEquals(SpeedrunHud.Anchor.TOP_LEFT, a);
        assertEquals(SpeedrunHud.Anchor.TOP_RIGHT, SpeedrunHud.Anchor.TOP_LEFT.next());
        assertEquals(SpeedrunHud.Anchor.TOP_LEFT, SpeedrunHud.Anchor.parse(null));
    }

    @Test
    void remettreParDefautRetrouveLaPlaceDOrigine() {
        Config c = new Config();
        c.speedrunHudAnchor = "bottom_right";
        c.speedrunHudScale = 150;
        c.speedrunHudOffsetX = 48;
        c.speedrunHudOffsetY = 0;
        SpeedrunHud.reset(c);
        assertEquals("top_left", c.speedrunHudAnchor);
        assertEquals(100, c.speedrunHudScale);
        assertEquals(6, c.speedrunHudOffsetX);
        assertEquals(6, c.speedrunHudOffsetY);
    }
}
