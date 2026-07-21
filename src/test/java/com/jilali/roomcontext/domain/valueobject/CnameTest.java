package com.jilali.roomcontext.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CnameTest {

    @Test
    void rejectsBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> new Cname(""));
        assertThrows(IllegalArgumentException.class, () -> new Cname("   "));
        assertThrows(IllegalArgumentException.class, () -> new Cname(null));
    }

    @Test
    void acceptsNonBlankValue() {
        Cname cname = new Cname("room-123");
        assertEquals("room-123", cname.value());
        assertEquals("room-123", cname.toString());
    }

    @Test
    void equalityIsValueBased() {
        assertEquals(new Cname("abc"), new Cname("abc"));
    }
}
