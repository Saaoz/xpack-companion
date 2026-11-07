package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeedrunModeTest {
    @Test
    void chaqueModeDitCeQuiEstAutomatique() {
        assertTrue(SpeedrunMode.AUTO.startAuto() && SpeedrunMode.AUTO.endAuto());
        assertFalse(SpeedrunMode.AUTO_END.startAuto());
        assertTrue(SpeedrunMode.AUTO_END.endAuto());
        assertTrue(SpeedrunMode.AUTO_START.startAuto());
        assertFalse(SpeedrunMode.AUTO_START.endAuto());
        assertFalse(SpeedrunMode.MANUAL.startAuto() || SpeedrunMode.MANUAL.endAuto());
    }

    @Test
    void seulLeToutAutomatiquePeutEtreClasse() {
        for (SpeedrunMode m : SpeedrunMode.values()) assertEquals(m == SpeedrunMode.AUTO, m.ranked(), m.id);
    }

    @Test
    void lIdentifiantEnregistreSeRelitEtUnInconnuDonneToutAutomatique() {
        for (SpeedrunMode m : SpeedrunMode.values()) assertEquals(m, SpeedrunMode.parse(m.id));
        assertEquals(SpeedrunMode.AUTO, SpeedrunMode.parse(null));
        assertEquals(SpeedrunMode.AUTO, SpeedrunMode.parse("n'importe quoi"));
    }

    @Test
    void leMenuFaitLeTourDesQuatreModes() {
        SpeedrunMode m = SpeedrunMode.AUTO;
        for (int i = 0; i < 4; i++) m = m.next();
        assertEquals(SpeedrunMode.AUTO, m);
        assertEquals(SpeedrunMode.AUTO_END, SpeedrunMode.AUTO.next());
    }
}
