package mod.xie.xm.content.model;

import java.util.Optional;

/**
 * One item declaration from an {@code "items"} section.
 *
 * @param origin      where it was declared
 * @param name        the declaration key, e.g. {@code tomatoSeeds}
 * @param displayName the {@code name} property, or the key if absent
 * @param archetype   the {@code type} property; {@code basic} when unset
 * @param override    the vanilla object this replaces, from {@code override}
 * @param drops       what using it leaves behind, from {@code drops}
 * @param blockDrops  the blocks this works on and what each yields, from {@code blocks}
 * @param tex         spritesheet assignment ({@code texFile} plus {@code icon})
 * @param props       every remaining property
 */
public record ItemDef(
    Origin origin,
    String name,
    String displayName,
    String archetype,
    Optional<ItemRef> override,
    DropTable drops,
    java.util.List<ItemDef.BlockDrops> blockDrops,
    TexDef tex,
    PropertyMap props
) {

    public static final String DEFAULT_ARCHETYPE = "basic";

    /**
     * One entry of a {@code blocks} map: a block this item acts on, and the drop
     * table that block yields when it does.
     *
     * <p>Only the {@code stick} archetype declares it. {@code XieItemStick} kept two
     * parallel lists — {@code blocksAffected} and {@code drops} — walked in step and
     * consulted in declaration order, with the first match winning, so this is a list
     * rather than a map: order is part of the meaning and nothing forbids a
     * definition naming the same block twice.
     *
     * @param block what has to be right-clicked
     * @param drops what that block yields, written exactly as a block's {@code drops}
     */
    public record BlockDrops(ItemRef block, DropTable drops) { }

    /** Archetypes the original engine dispatched on, from {@code JobItem.doWork}. */
    public static final java.util.Set<String> KNOWN_ARCHETYPES = java.util.Set.of(
        // "seed" and "seeds" are the same archetype: JobItem.doWork dispatched
        // subtype.equals("seeds") || subtype.equals("seed") to the one doSeeds.
        "basic", "food", "seeds", "seed", "tool", "sword", "bow", "armor", "armour",
        "bucket", "potion", "block", "stick", "set", "fertiliser", "interface"
    );

    public ItemDef {
        blockDrops = java.util.List.copyOf(blockDrops);
    }

    /**
     * What using this item leaves behind.
     *
     * <p>A consuming read, like the two below it, and for the reason
     * {@link BlockDef#drops()} gives: the parser fills it in for every definition, so
     * only a consumer asking for it says anything about whether it survived.
     *
     * <p>Here that consumer is {@code food} alone, as the use remainder. 1.6 set the
     * drops on every archetype and read them back in three: {@code XieItemFood} handed
     * them over when you ate, {@code XieItem.onItemRightClick} turned a plain item
     * into them, and {@code XieItemSeeds.onItemUse} gave them to you for planting.
     * Only the first is carried, so a {@code drops} on a {@code basic} or a
     * {@code seeds} item is reported rather than quietly lost.
     */
    public DropTable drops() {
        props.markUsed("drops");
        return drops;
    }

    /**
     * The blocks this works on and what each yields. {@code stick} alone consults it,
     * which is where {@code JobItem} read it too — {@code doStick} and nowhere else.
     */
    public java.util.List<BlockDrops> blockDrops() {
        props.markUsed("blocks");
        return blockDrops;
    }

    /**
     * The sprite this item draws, consumed by the texture copy for every registered
     * item.
     *
     * <p>There is no item counterpart of {@link BlockDef#textureBase()} because there
     * was none in 2013 either: {@code setTextureBase} is on {@code IXieBlock} and
     * {@code JobItem} has no branch for the key at all. An item declaring one is
     * therefore reported, exactly as {@code smeltsInto} is.
     */
    public TexDef tex() {
        props.markUsed("tex", "texFile", "icon");
        return tex;
    }

    /** {@link #tex()} without marking it consumed, for diagnostics. */
    TexDef peekTex() {
        return tex;
    }

    public boolean isOverride() {
        return override.isPresent();
    }

    public boolean hasKnownArchetype() {
        return KNOWN_ARCHETYPES.contains(archetype);
    }

    public String qualifiedName() {
        return origin.qualifiedName();
    }

    /**
     * Convenience for the food archetype; empty when this is not edible.
     *
     * <p>A consuming read, like every other accessor that answers with what a
     * definition declared: {@link PropertyMap#peek} would leave the key looking unread
     * to {@code untouchedKeys()}, and every food in the content would be reported as
     * declaring a nutrition value nobody looked at.
     */
    public Optional<Integer> hunger() {
        return props.getInt("hunger");
    }

    /** Convenience for the food archetype; empty when this is not edible. */
    public Optional<Float> nourishment() {
        return props.getFloat("nourishment");
    }
}
