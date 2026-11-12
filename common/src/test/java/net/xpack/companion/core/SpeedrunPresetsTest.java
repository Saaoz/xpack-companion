package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpeedrunPresetsTest {
    @Test
    void leParcoursClassiqueSuitVillageNetherPerleEndDragon() {
        SpeedrunPresets.Preset p = SpeedrunPresets.find("classic");
        assertNotNull(p);
        StringBuilder order = new StringBuilder();
        for (SpeedrunPresets.Step s : p.steps) order.append(s.id).append(' ');
        assertEquals("village nether pearl end dragon ", order.toString());
        assertEquals("minecraft:end/kill_dragon", p.steps.get(p.steps.size() - 1).advancement);
    }

    @Test
    void leParcoursParProgresEstEntierementAutomatique() {
        for (SpeedrunPresets.Step s : SpeedrunPresets.PROGRESS.steps) {
            assertNotNull(s.advancement, s.id);
            assertTrue(s.advancement.startsWith("minecraft:"), s.id);
        }
    }

    @Test
    void lesIdentifiantsSontUniquesEtPersonnaliseVientEnPremier() {
        Set<String> seen = new HashSet<>();
        for (String id : SpeedrunPresets.ids()) assertTrue(seen.add(id), id);
        assertEquals(SpeedrunPresets.CUSTOM, SpeedrunPresets.ids().get(0));
        assertNull(SpeedrunPresets.find(SpeedrunPresets.CUSTOM));
        assertNull(SpeedrunPresets.find("inconnu"));
        for (SpeedrunPresets.Preset p : SpeedrunPresets.ALL) {
            assertFalse(p.steps.isEmpty());
            Set<String> stepIds = new HashSet<>();
            for (SpeedrunPresets.Step s : p.steps) assertTrue(stepIds.add(s.id), p.id + "/" + s.id);
        }
    }
}
