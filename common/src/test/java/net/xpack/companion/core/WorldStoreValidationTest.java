package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #7 : un xpack.json forgé (monde téléchargé) ne doit pas injecter n'importe quoi. */
class WorldStoreValidationTest {
    @TempDir
    Path dir;

    private XPackCore.WorldStore load(String json) throws Exception {
        Path f = dir.resolve("xpack.json");
        Files.writeString(f, json);
        return XPackCore.WorldStore.load(f);
    }

    @Test
    void idInvraisemblableRemplaceParUnNouvelId() throws Exception {
        assertNotEquals("../../etc\"x", load("{\"worldId\":\"../../etc\\\"x\"}").worldId);
        assertEquals(36, load("{\"worldId\":\"" + "a".repeat(500) + "\"}").worldId.length());
        assertEquals("0123456789abcdef", load("{\"worldId\":\"0123456789abcdef\"}").worldId);
        String uuid = "123e4567-e89b-12d3-a456-426614174000";
        assertEquals(uuid, load("{\"worldId\":\"" + uuid + "\"}").worldId);
    }

    @Test
    void fileEnAttenteBorneeEtEvenementsDemesuresEcartes() throws Exception {
        StringBuilder sb = new StringBuilder("{\"pending\":[{\"big\":\"" + "x".repeat(20000) + "\"}");
        for (int i = 0; i < XPackCore.WorldStore.MAX_PENDING + 50; i++) sb.append(",{\"k\":").append(i).append('}');
        sb.append("]}");
        XPackCore.WorldStore s = load(sb.toString());
        assertEquals(XPackCore.WorldStore.MAX_PENDING, s.pending.size());
        assertTrue(s.pending.get(0).has("k"), "l'événement trop gros est écarté");
    }

    @Test
    void typesIncorrectsRamenesAUnEtatNeuf() throws Exception {
        XPackCore.WorldStore s = load("{\"pending\":\"pas un tableau\"}");
        assertTrue(s.pending.isEmpty());
        s = load("{\"status\":\"hack\",\"playtime\":-5,\"firstSeen\":\"" + "z".repeat(100) + "\"}");
        assertEquals(null, s.status);
        assertEquals(0, s.playtime);
        assertEquals(null, s.firstSeen);
    }
}
