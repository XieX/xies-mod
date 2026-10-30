package mod.xie.xm.content.model;

import mod.xie.xm.content.parse.Diagnostics;
import mod.xie.xm.content.parse.XieFileReader;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedReturnsTest {

    private static XieFileReader.Result read(String json) {
        return new XieFileReader(new Diagnostics()).read("test.json", "test", new StringReader(json));
    }

    private static List<SeedReturns.Finding> findings(XieFileReader.Result result) {
        return SeedReturns.in(result.blocks(), result.items());
    }

    /** A bush and the bean that plants it, dropping that bean at the given chance and amount. */
    private static XieFileReader.Result beanBush(String chance, int amount) {
        return read("""
            {
              "blocks": {
                "beanBush": {
                  "type": "bush",
                  "drops": { "beans": { "chance": %s, "amount": %d } }
                }
              },
              "items": {
                "beans": { "type": "seeds", "growsInto": "beanBush" }
              }
            }
            """.formatted(chance, amount));
    }

    /** Three rolls at 64 at every age: about two beans back for the one planted. */
    @Test
    void reportsASeedThatPaysForItselfAtAgeZero() {
        List<SeedReturns.Finding> found = findings(beanBush("{ \"0-4\": 64 }", 3));

        assertEquals(1, found.size());
        SeedReturns.Finding finding = found.get(0);
        assertEquals("beanBush", finding.block().name());
        assertEquals("beans", finding.seed().name());
        assertEquals(64, finding.chance());
        assertEquals(3, finding.amount());
        assertEquals(1.92, finding.perBreak(), 1e-9);
    }

    /** Only age 0 counts: a ripe bush paying out well is the point of growing it. */
    @Test
    void acceptsAGenerousRipeYieldWhenAgeZeroIsBelowOne() {
        assertTrue(findings(beanBush("{ \"0-2\": 25, \"3\": 50, \"4+\": 90 }", 3)).isEmpty());
        assertTrue(findings(beanBush("{ \"0\": 33, \"1+\": 100 }", 3)).isEmpty());
    }

    /** Exactly one back is reported too: planting and breaking then costs nothing. */
    @Test
    void reportsBreakingEven() {
        assertEquals(1, findings(beanBush("{ \"0\": 50 }", 2)).size());
        assertEquals(1, findings(beanBush("{ \"0\": 150 }", 1)).size());
        assertEquals(100, findings(beanBush("{ \"0\": 150 }", 1)).get(0).chance());
    }

    /** No chance map at all is a hundred percent, every age. */
    @Test
    void readsAnUnconditionalDropAsCertain() {
        XieFileReader.Result result = read("""
            {
              "blocks": { "beanBush": { "type": "crop", "drops": "beans" } },
              "items": { "beans": { "type": "seed", "growsInto": "beanBush" } }
            }
            """);
        assertEquals(1, findings(result).size());
    }

    /**
     * Only the seed that plants this block: another crop's seed, an item that is not a
     * seed, and a block a seed does not grow into all go unreported.
     */
    @Test
    void asksOnlyAboutTheSeedThatPlantsTheBlock() {
        XieFileReader.Result result = read("""
            {
              "blocks": {
                "beanBush": {
                  "type": "bush",
                  "drops": "peas,beanPods,item.wheat_seeds"
                },
                "peaBush": { "type": "bush" },
                "beanStone": { "type": "basic", "drops": "stoneBeans" }
              },
              "items": {
                "beans": { "type": "seeds", "growsInto": "beanBush" },
                "peas": { "type": "seeds", "growsInto": "peaBush" },
                "beanPods": { "type": "food", "growsInto": "beanBush" },
                "stoneBeans": { "type": "seeds", "growsInto": "beanStone" }
              }
            }
            """);
        assertTrue(findings(result).isEmpty(), () -> findings(result).toString());
    }

    /** A diagnostic, not a use: the unread-property report must see what it saw before. */
    @Test
    void consumesNothing() {
        XieFileReader.Result result = beanBush("{ \"0-4\": 64 }", 3);
        BlockDef bush = result.blocks().get("beanBush");
        ItemDef beans = result.items().get("beans");
        Set<String> bushBefore = Set.copyOf(bush.props().untouchedKeys());
        Set<String> beansBefore = Set.copyOf(beans.props().untouchedKeys());

        findings(result);

        assertEquals(bushBefore, bush.props().untouchedKeys());
        assertEquals(beansBefore, beans.props().untouchedKeys());
        assertTrue(bushBefore.contains("drops"));
        assertTrue(beansBefore.contains("growsinto"));
    }
}
