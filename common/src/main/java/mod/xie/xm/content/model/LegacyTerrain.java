package mod.xie.xm.content.model;

import java.util.Map;
import java.util.Optional;

/**
 * Vanilla's own {@code terrain.png}, which is the spritesheet a definition addresses
 * when it names none of its own.
 *
 * <p>{@link TexDef} keeps a {@code texFile} and a set of sprite indices into it. The
 * file is optional and always was: {@code SpritesheetManager.getTextureFilepath}
 * answered a definition that named no sheet — or named one the pack did not ship —
 * with {@code getDefaultTextureFile()}, and that method returns {@code "/terrain.png"}.
 * A bare index is therefore not a missing declaration. It is a position in the
 * <em>game's</em> sheet, and 2013 content used it to borrow a vanilla drawing for a
 * block of its own.
 *
 * <p>One definition in the content does that. {@code blazeTree} declares
 * {@code "tex": {"all": {"0": 31}}} and no {@code texFile}, and 31 is where
 * {@code Block.fire} drew from — {@code new BlockFire(51, 31)}. That is the whole
 * point of the block: the 2013 announcement says a blaze tree's trunks "are made from
 * blaze rods, and their leaves from flame", and the definition's {@code "lightValue":
 * 1.0} is fire's own light. Cutting sprite 31 out of the pack's {@code nether_crops.png}
 * would draw nothing at all, because that cell of that sheet is empty; the index was
 * never for it.
 *
 * <p>The table is short because the layout it describes is frozen and unrecoverable.
 * {@code terrain.png} was retired in 1.5, so an index is a historical fact about an
 * image rather than something derivable from a modern id, and an entry nothing
 * addresses is a claim about a picture no test can check. Filling it out is the work
 * of whoever brings in content that needs the next index — {@code XiePackEmitter}
 * warns by name and number for one it cannot answer, so an unmigrated pack says which
 * entries it wants rather than quietly rendering purple.
 *
 * <p>Kept apart from {@link LegacyBlockIds} on the same grounds that table is kept
 * apart from {@link ResourceNames}: an index addresses a drawing, not a block. The two
 * numberings are unrelated — 31 is fire here and the tall grass there — so a lookup
 * that consulted either would be wrong half the time.
 */
public final class LegacyTerrain {

    /**
     * Sprite index to modern block texture path.
     *
     * <p>Fire is two sprites in modern Minecraft as it was two cells here: the second
     * layer sat directly below the first, at 47, and became {@code fire_1}. It is not
     * an entry because nothing addresses it — a definition borrowing fire borrows the
     * face vanilla puts on the outside of the flame.
     */
    private static final Map<Integer, String> TEXTURES = Map.of(31, "fire_0");

    private LegacyTerrain() { }

    /**
     * The modern texture path for a {@code terrain.png} sprite index, or empty if this
     * table does not have that index yet.
     *
     * @param index the sprite index, counted across and then down the 16-wide sheet
     */
    public static Optional<String> texture(int index) {
        return Optional.ofNullable(TEXTURES.get(index));
    }
}
