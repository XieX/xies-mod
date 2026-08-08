package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRefTest {

    @Test
    void parsesPlainName() {
        ItemRef ref = ItemRef.parse("tomato");
        assertEquals("tomato", ref.name());
        assertNull(ref.owner());
        assertEquals(1, ref.count());
        assertFalse(ref.hasMeta());
        assertFalse(ref.isGroup());
    }

    @Test
    void parsesQualifiedName() {
        ItemRef ref = ItemRef.parse("farming@tomato");
        assertEquals("farming", ref.owner());
        assertEquals("tomato", ref.name());
        assertEquals("farming@tomato", ref.qualifiedName());
    }

    @Test
    void parsesVanillaNameWithCount() {
        ItemRef ref = ItemRef.parse("item.melon:4");
        assertEquals("item.melon", ref.name());
        assertEquals(4, ref.count());
        assertTrue(ref.isVanilla());
        assertEquals("melon", ref.vanillaName());
    }

    @Test
    void parsesCountAndMeta() {
        ItemRef ref = ItemRef.parse("wheat:2:3");
        assertEquals("wheat", ref.name());
        assertEquals(2, ref.count());
        assertEquals(3, ref.meta());
        assertTrue(ref.hasMeta());
    }

    @Test
    void parsesAliasSigil() {
        ItemRef ref = ItemRef.parse("$salady");
        assertEquals(ItemRef.RefKind.ALIAS, ref.kind());
        assertEquals("salady", ref.name());
        assertTrue(ref.isGroup());
    }

    @Test
    void parsesBuiltinGroupSigil() {
        ItemRef ref = ItemRef.parse("#food");
        assertEquals(ItemRef.RefKind.BUILTIN_GROUP, ref.kind());
        assertEquals("food", ref.name());
        assertTrue(ref.isGroup());
    }

    @Test
    void toleratesGarbageCount() {
        ItemRef ref = ItemRef.parse("tomato:banana");
        assertEquals("tomato", ref.name());
        assertEquals(1, ref.count(), "a bad count should fall back rather than throw");
    }

    @Test
    void inheritsOwnerOnlyWhenAbsent() {
        assertEquals("farming", ItemRef.parse("tomato").inheritOwner("farming").owner());
        assertEquals("other", ItemRef.parse("other@tomato").inheritOwner("farming").owner());
    }

    @Test
    void trimsWhitespace() {
        assertEquals("tomato", ItemRef.parse("  tomato  ").name());
    }
}
