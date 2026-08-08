package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaRangeTest {

    @Test
    void parsesSingleValue() {
        MetaRange range = MetaRange.parse("2").orElseThrow();
        assertEquals(new MetaRange(2, 2), range);
        assertTrue(range.isSingle());
        assertTrue(range.contains(2));
        assertFalse(range.contains(3));
    }

    @Test
    void parsesClosedRange() {
        MetaRange range = MetaRange.parse("0-3").orElseThrow();
        assertEquals(new MetaRange(0, 3), range);
        assertEquals(4, range.size());
        assertTrue(range.contains(0));
        assertTrue(range.contains(3));
        assertFalse(range.contains(4));
    }

    @Test
    void parsesOpenEndedRange() {
        MetaRange range = MetaRange.parse("4+").orElseThrow();
        assertEquals(MetaRange.atLeast(4), range);
        assertTrue(range.openEnded());
        assertTrue(range.contains(15));
        assertFalse(range.contains(3));
    }

    /** Real content contains "14-15"; it must not be read as "14+". */
    @Test
    void closedRangeEndingAtMaximumIsNotOpenEnded() {
        MetaRange closed = MetaRange.parse("14-15").orElseThrow();
        assertFalse(closed.openEnded());
        assertEquals(15, closed.boundedMax());
        assertEquals(14, MetaRange.parse("14+").orElseThrow().boundedMax());
        assertFalse(closed.equals(MetaRange.parse("14+").orElseThrow()));
    }

    @Test
    void parsesAll() {
        assertEquals(MetaRange.ALL, MetaRange.parse("all").orElseThrow());
        assertEquals(MetaRange.ALL, MetaRange.parse("ALL").orElseThrow());
    }

    @Test
    void clampsOutOfRangeValues() {
        assertEquals(new MetaRange(0, 15), MetaRange.parse("0-99").orElseThrow());
    }

    @Test
    void normalisesInvertedRange() {
        assertEquals(new MetaRange(2, 5), MetaRange.parse("5-2").orElseThrow());
    }

    @Test
    void rejectsGarbageWithoutThrowing() {
        assertEquals(Optional.empty(), MetaRange.parse("banana"));
        assertEquals(Optional.empty(), MetaRange.parse(""));
        assertEquals(Optional.empty(), MetaRange.parse(null));
    }

    @Test
    void roundTripsThroughToString() {
        for (String text : new String[] {"all", "2", "0-3", "4+", "14-15", "0-15"}) {
            MetaRange range = MetaRange.parse(text).orElseThrow();
            assertEquals(range, MetaRange.parse(range.toString()).orElseThrow(),
                "round trip failed for " + text);
        }
    }

    @Test
    void openEndedRangeReportsItsStartAsTheDeclaredMaximum() {
        // "8+" means "from 8 onwards", i.e. 8 is the final stage — not that
        // sixteen stages exist.
        assertEquals(8, MetaRange.parse("8+").orElseThrow().boundedMax());
        assertEquals(15, MetaRange.parse("8+").orElseThrow().max());
        assertEquals(4, MetaRange.parse("0-4").orElseThrow().boundedMax());
        assertEquals(2, MetaRange.parse("2").orElseThrow().boundedMax());
        assertEquals(0, MetaRange.ALL.boundedMax());
    }

    @Test
    void metaMapDistinguishesDeclaredFromLiteralMaximum() {
        MetaMap<String> map = MetaMap.<String>varying()
            .put(MetaRange.parse("0-2").orElseThrow(), "small")
            .put(MetaRange.parse("8+").orElseThrow(), "ripe");
        assertEquals(15, map.highestMeta());
        assertEquals(8, map.highestDeclaredMeta());
    }

    @Test
    void metaMapResolvesLastMatchWins() {
        MetaMap<Integer> map = MetaMap.<Integer>varying()
            .put(MetaRange.ALL, 1)
            .put(MetaRange.parse("4+").orElseThrow(), 9);
        assertEquals(1, map.get(0).orElseThrow());
        assertEquals(9, map.get(5).orElseThrow());
        assertEquals(15, map.highestMeta());
    }

    @Test
    void uniformMetaMapCoversEveryValue() {
        MetaMap<String> map = MetaMap.uniform("x");
        assertTrue(map.isUniform());
        assertEquals("x", map.get(0).orElseThrow());
        assertEquals("x", map.get(15).orElseThrow());
    }
}
