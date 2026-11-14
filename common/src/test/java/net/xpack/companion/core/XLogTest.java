package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class XLogTest {
    @AfterEach
    void reset() {
        XLog.setSink(null);
    }

    @Test
    void lineGoesToSinkAndRepeatIsSkipped() {
        List<String> lines = new ArrayList<>();
        XLog.setSink(lines::add);
        XLog.warn("test répétition", new IOException("disque plein"));
        XLog.warn("test répétition", new IOException("disque plein"));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("IOException (disque plein)"));
    }

    @Test
    void jsonErrorKeepsOnlyClass() {
        assertEquals("JsonSyntaxException", XLog.describe(new JsonSyntaxException("contenu secret")));
    }

    @Test
    void homeDirectoryIsRemoved() {
        String home = System.getProperty("user.home");
        String s = XLog.sanitize(home + "/xpack/credentials.json");
        assertFalse(s.contains(home));
        assertTrue(s.startsWith("~"));
    }
}
