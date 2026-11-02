package mod.xie.xm.reg;

import mod.xie.xm.content.model.TreeShape;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the registrar actually registered.
 *
 * <p>The pack emitters used to re-derive this from the definitions, guessing at
 * which ones had made it into the registry. That stopped being possible once one
 * definition could become several objects: a multi-subtype definition registers one
 * id per entry in its {@code names} property, and only the registrar knows which
 * ids those turned out to be. Handing the answer over is both shorter and the only
 * way the two sides can be guaranteed to agree.
 *
 * @param blockPaths   block definition name to its id paths, indexed by metadata value
 * @param itemPaths    item definition name to its id paths, indexed by metadata value
 * @param maxAges      block id path to its highest age, for blockstate variants;
 *                     a block with no {@code age} property is simply absent
 * @param blockItems   block id paths that also registered a {@link
 *                     net.minecraft.world.item.BlockItem}, so the emitters know to
 *                     give them an item model and a drop-self loot table
 * @param trees        leaves block id path to the tree it grows, for the configured
 *                     feature the emitter writes
 * @param blockTags    generated block tag id to the block ids it holds. Three things
 *                     need one: a tool's {@code blocksEffective}, {@code canHarvest}
 *                     and {@code cannotHarvest} lists, because {@code minecraft:tool}
 *                     takes tags and not lists of ids; a stem's {@code growsOn}, for
 *                     the same reason on {@code StemBlock}; and
 *                     {@code #minecraft:logs}, which every {@code log} joins. The
 *                     registrar names them and the emitter writes them
 * @param translations translation key to display name
 */
public record RegisteredContent(
    Map<String, List<String>> blockPaths,
    Map<String, List<String>> itemPaths,
    Map<String, Integer> maxAges,
    Set<String> blockItems,
    Map<String, Tree> trees,
    Map<String, List<String>> blockTags,
    Map<String, String> translations
) {

    /**
     * The shape and materials of one {@code tree} archetype's tree.
     *
     * <p>Enough for {@code XieWorldgenEmitter} to write a {@code minecraft:tree}
     * configured feature, which is the only form the tree takes: worldgen places it
     * and a growing sapling places it, and neither needs anything else.
     *
     * @param trunkId   namespaced id of the log the trunk is built from
     * @param shape     which of 1.6's three tree generators this is
     * @param fruitAges the ripe stage of its leaves, or zero for a tree that bears
     *                  nothing; the feature places a few leaves already ripe
     */
    public record Tree(String trunkId, TreeShape shape, int fruitAges) { }

    public RegisteredContent {
        blockPaths = Map.copyOf(blockPaths);
        itemPaths = Map.copyOf(itemPaths);
        maxAges = Map.copyOf(maxAges);
        blockItems = Set.copyOf(blockItems);
        trees = Map.copyOf(trees);
        blockTags = Map.copyOf(blockTags);
        translations = Map.copyOf(translations);
    }

    public static RegisteredContent empty() {
        return new RegisteredContent(Map.of(), Map.of(), Map.of(), Set.of(), Map.of(),
            Map.of(), Map.of());
    }

    /**
     * The configured feature id path for a tree, derived from its leaves.
     *
     * <p>The registrar hands this key to the sapling's {@code TreeGrower} and the
     * emitter writes the file it names, so — like {@link
     * mod.xie.xm.content.model.ResourceNames} — the two sides have to derive it the
     * same way or a sapling looks up a feature that was never written.
     */
    public static String treeFeaturePath(String leavesPath) {
        return leavesPath + "_tree";
    }

    /**
     * The id path of the attached half of a stem — the bent one, once it has fruited.
     *
     * <p>One {@code stem} definition registers two blocks, because vanilla spends a
     * blockstate on what 1.6 worked out at render time. Derived rather than recorded,
     * for the same reason {@link #treeFeaturePath} is: the registrar names the block
     * and the emitter writes its blockstate file, and the two have to agree.
     */
    public static String attachedStemPath(String stemPath) {
        return "attached_" + stemPath;
    }

    /**
     * The id path for one subtype of a definition, or empty when that definition was
     * not registered or has no such subtype.
     *
     * <p>Items are consulted before blocks: a reference in a recipe wants the thing
     * that goes in an inventory slot.
     */
    public Optional<String> pathFor(String definitionName, int meta) {
        return pathIn(itemPaths, definitionName, meta)
            .or(() -> pathIn(blockPaths, definitionName, meta));
    }

    /**
     * Whether a registered block has an {@code age} blockstate property.
     *
     * <p>Only the growing archetypes do. It decides both how many blockstate
     * variants the block needs and whether a loot pool may name {@code age} in a
     * {@code block_state_property} condition — naming a property a block does not
     * have is a datapack load error, not a no-op.
     */
    public boolean hasAge(String path) {
        return maxAges.containsKey(path);
    }

    /** How many subtypes a definition registered; 0 when it registered nothing. */
    public int subtypeCount(String definitionName) {
        List<String> items = itemPaths.get(definitionName);
        if (items != null) {
            return items.size();
        }
        List<String> blocks = blockPaths.get(definitionName);
        return blocks == null ? 0 : blocks.size();
    }

    private static Optional<String> pathIn(Map<String, List<String>> paths, String name, int meta) {
        List<String> found = paths.get(name);
        if (found == null || meta < 0 || meta >= found.size()) {
            return Optional.empty();
        }
        return Optional.of(found.get(meta));
    }
}
