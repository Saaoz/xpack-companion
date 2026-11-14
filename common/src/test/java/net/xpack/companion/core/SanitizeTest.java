package net.xpack.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SanitizeTest {
    private static final String S = String.valueOf((char) 0xA7);

    @Test
    void retireLesCodesDeMiseEnForme() {
        assertEquals("Pack", Sanitize.stripFormatting(S + "cPa" + S + "lck"));
        assertEquals("abc", Sanitize.stripFormatting("a" + S + "kb" + S + "rc"));
    }

    @Test
    void codeFinalIsoleEtNull() {
        assertEquals("ab", Sanitize.stripFormatting("ab" + S));
        assertNull(Sanitize.stripFormatting(null));
        assertEquals("rien", Sanitize.stripFormatting("rien"));
    }
}
