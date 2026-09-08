package mod.xie.xm.reg;

import com.mojang.logging.LogUtils;

import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.PropertyMap;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports properties the content declares that nothing in the port reads.
 *
 * <p>{@link PropertyMap} has tracked reads from the beginning, and its own javadoc
 * says {@code untouchedKeys()} "is how typos surface without the parser needing an
 * allowlist". Nothing called it outside the tests, so nothing surfaced: {@code spikey}
 * and {@code fertilisable} were both found by a human reading a definition file
 * beside the registrar, and the steel pack turned out to declare three more of the
 * same kind. This is that mechanism finally wired to the log.
 *
 * <p>It is the one report in the port that needs no knowledge of any archetype. A key
 * nobody read is one of four things, and all four are worth a line:
 *
 * <ul>
 *   <li><strong>A property the port does not carry.</strong> {@code spikey} was this
 *       until it was implemented; {@code toolMaterial} still is, and turns out never to
 *       have been implemented in 2013 either.</li>
 *   <li><strong>A typo, or a spelling the engine never knew.</strong>
 *       {@code steel.json} writes {@code contents} where the reader wants
 *       {@code content} and {@code smeltsInto} where the engine wanted {@code cooked},
 *       and in 1.6 both cost exactly what they cost here.</li>
 *   <li><strong>A property of an archetype nothing registers.</strong> A definition
 *       the port skips wholesale reports every key it has, which says how much of it
 *       is going unused rather than just that it was skipped.</li>
 *   <li><strong>A structure that parsed and then went nowhere</strong>, which is the
 *       one this could not see until it was taught to. {@code drops}, {@code sheds},
 *       {@code blocks}, {@code blockBounds} and the texture keys are read by
 *       {@code XieFileReader} for every definition, so marking them consumed there
 *       proved only that the parser understood them — and {@code blazeTree}'s
 *       {@code blockBounds} was silently going missing the whole time, found by a human
 *       reading the registrar rather than by this. They are marked consumed by the
 *       accessor now, so the question the report asks is whether an archetype wanted
 *       the value. See {@link mod.xie.xm.content.model.PropertyMap#markStructural}.</li>
 * </ul>
 *
 * <p>Two kinds of key are exempt, because reporting them would bury the rest:
 *
 * <ul>
 *   <li><strong>{@code id}</strong>, the 1.6 numeric block or item id. Every
 *       definition in some packs carries one, numeric ids are gone from Minecraft
 *       entirely, and the {@code .xid} files that assign them in bulk are read for
 *       diagnostics alone. It is the one property whose absence from the port is the
 *       whole design rather than a gap in it.</li>
 *   <li><strong>Anything beginning with an underscore</strong>, which is the format's
 *       comment convention — {@code steel.json} annotates each ingot with
 *       {@code "_comment": "10% improvement over iron"}. It is the same convention the
 *       leading underscore on a filename uses to mean "skip this".</li>
 * </ul>
 *
 * <p>An overriding definition is skipped whole. Its properties are read from
 * {@link XieOverrides}, which applies them from {@code ModifyDefaultComponentsEvent} —
 * later than this report runs, since that event waits for the datapack — so every one
 * of them would look unread here. What an override genuinely cannot carry is reported
 * by that path instead.
 */
public final class XieUnreadProperties {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** See the class comment: the id is gone by design and {@code _x} is a comment. */
    private static boolean isExempt(String key) {
        return key.equals("id") || key.startsWith("_");
    }

    private XieUnreadProperties() { }

    /**
     * Logs one line per definition with unread properties, then a count.
     *
     * <p>Called once every consumer has run, which is the end of pack emission: the
     * registrar reads a definition to build a block or an item and the emitters read it
     * again to draw and describe it, and a property either side consulted is marked
     * before this point.
     */
    public static void report(List<ContentPack> packs) {
        int properties = 0;
        int definitions = 0;

        for (ContentPack pack : packs) {
            List<String> lines = new ArrayList<>();
            properties += collect(pack, lines);
            definitions += lines.size();
            for (String line : lines) {
                LOGGER.warn("[{}] {}", pack.name(), line);
            }
        }

        if (properties > 0) {
            LOGGER.info("{} properties across {} definitions are declared by content and "
                + "read by nothing", properties, definitions);
        }
    }

    /** Adds a line per definition with unread keys; answers how many keys that was. */
    private static int collect(ContentPack pack, List<String> lines) {
        int count = 0;
        for (var block : pack.blocks().values()) {
            if (block.isOverride()) {
                continue;
            }
            count += describe(block.qualifiedName(), "block", block.props(), lines);
        }
        for (var item : pack.items().values()) {
            if (item.isOverride()) {
                continue;
            }
            count += describe(item.qualifiedName(), "item", item.props(), lines);
        }
        for (var recipe : pack.recipes()) {
            count += describe(recipe.origin().qualifiedName(), "recipe", recipe.props(), lines);
        }
        for (var spawn : pack.spawns()) {
            count += describe(spawn.origin().qualifiedName(), "spawn", spawn.props(), lines);
        }
        return count;
    }

    /**
     * Adds at most one line for one definition, splitting it by what kind of gap each
     * key is.
     *
     * <p>The two read differently and want looking at in different places. A key
     * nothing read at all is a property the port does not carry, or a spelling it never
     * knew; a key that <em>parsed</em> and then went unconsumed is a hole in an
     * archetype's own path, and the code to look at is the registrar or the emitter
     * rather than the parser.
     */
    private static int describe(String name, String kind, PropertyMap props, List<String> lines) {
        Set<String> parsed = props.unconsumedStructures();

        Set<String> unread = new LinkedHashSet<>();
        Set<String> unconsumed = new LinkedHashSet<>();
        for (String key : props.untouchedKeys()) {
            if (isExempt(key)) {
                continue;
            }
            (parsed.contains(key) ? unconsumed : unread).add(key);
        }
        if (unread.isEmpty() && unconsumed.isEmpty()) {
            return 0;
        }

        List<String> clauses = new ArrayList<>(2);
        if (!unread.isEmpty()) {
            clauses.add(String.join(", ", unread) + ", which nothing reads");
        }
        if (!unconsumed.isEmpty()) {
            clauses.add(String.join(", ", unconsumed)
                + ", which the parser reads and no archetype then consults");
        }
        lines.add(name + ": " + kind + " declares " + String.join(", and ", clauses));
        return unread.size() + unconsumed.size();
    }
}
