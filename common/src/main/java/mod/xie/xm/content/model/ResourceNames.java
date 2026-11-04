package mod.xie.xm.content.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Converts Xie definition names into namespaced-id paths.
 *
 * <p>2013 content is written in camelCase ({@code tomatoBush}); Minecraft ids must
 * match {@code [a-z0-9_.-]}. Both the registrar and the pack emitter have to agree
 * on the mapping exactly, or a block registers under one name and looks for its
 * model under another — so the conversion lives here, in the shared module, rather
 * than being reimplemented on each side.
 */
public final class ResourceNames {

    private ResourceNames() { }

    /**
     * {@code tomatoBush} to {@code tomato_bush}, {@code OJ} to {@code oj},
     * {@code sammichSSP} to {@code sammich_ssp}.
     *
     * <p>An underscore is inserted at each lower-to-upper boundary, and before the
     * final capital of a run followed by a lowercase letter, so acronym-ish names
     * split where a reader would expect. Accents are dropped ({@code Crème} is
     * {@code creme}) and any other letter outside ASCII separates, because an id
     * takes nothing else.
     */
    public static String toPath(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed";
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        StringBuilder out = new StringBuilder(plain.length() + 4);
        char[] chars = plain.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            boolean boundary = i > 0 && Character.isUpperCase(c)
                && (!Character.isUpperCase(chars[i - 1])
                    || (i + 1 < chars.length && Character.isLowerCase(chars[i + 1])));
            if (boundary && out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
            if (c < 128 && Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            } else if (c == '.' || c == '-' || c == '_') {
                out.append(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
        }

        String path = out.toString().toLowerCase(Locale.ROOT);
        while (path.startsWith("_")) {
            path = path.substring(1);
        }
        while (path.endsWith("_")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "unnamed" : path;
    }

    /**
     * Maps a legacy vanilla reference onto a modern id path.
     *
     * <p>Handles the {@code tile.}/{@code item.} prefixes and the handful of names
     * Mojang has renamed since 1.6. This is a starting set, not the full table —
     * unmapped names fall through to {@link #toPath} unchanged.
     */
    /**
     * The sixteen damage values of 1.6's {@code dyePowder}, which was one item id
     * with a subtype per colour. Every one is a separate item now, so the reference
     * cannot be resolved from the name alone — {@code dyePowder:1:3} is cocoa beans
     * and {@code dyePowder:1:15} is bone meal.
     */
    private static final String[] DYE_SUBTYPES = {
        "ink_sac", "red_dye", "green_dye", "cocoa_beans",
        "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye",
        "gray_dye", "pink_dye", "lime_dye", "yellow_dye",
        "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"
    };

    public static String toVanillaPath(ItemRef ref) {
        String name = ref.isVanilla() ? ref.vanillaName() : ref.name();
        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.equals("dyepowder") || lower.equals("dye")) {
            // Without a subtype there is nothing sensible to pick; ink sac was
            // metadata 0 and is what a bare reference meant.
            int meta = ref.hasMeta() ? ref.meta() : 0;
            if (meta >= 0 && meta < DYE_SUBTYPES.length) {
                return DYE_SUBTYPES[meta];
            }
        }

        // A block and an item could share a name in 1.6 and mean different things,
        // which is exactly what the tile./item. prefixes were for. Modern ids have
        // no such pairing, so the prefix has to be consulted before the name.
        // "tile.melon" is the block and "item.melon" is a slice; resolving both to
        // "melon" turns the vanilla melon recipe into an item duplicator.
        if (ref.isVanilla() && ref.name().toLowerCase(Locale.ROOT).startsWith("item.")) {
            String asItem = switch (lower) {
                case "melon" -> "melon_slice";
                default -> null;
            };
            if (asItem != null) {
                return asItem;
            }
        }

        return switch (lower) {
            // "grass" meant the grass *block* in 1.6; it now means the tall plant.
            case "grass" -> "grass_block";
            case "tallgrass" -> "short_grass";
            case "workbench" -> "crafting_table";
            case "doorwood" -> "oak_door";
            case "trapdoor" -> "oak_trapdoor";
            case "fence" -> "oak_fence";
            case "fencegate" -> "oak_fence_gate";
            case "sign" -> "oak_sign";
            case "log" -> "oak_log";
            case "wood" -> "oak_planks";
            case "sapling" -> "oak_sapling";
            case "deadbush" -> "dead_bush";
            case "crops" -> "wheat";
            case "seeds" -> "wheat_seeds";
            case "porkchopcooked" -> "cooked_porkchop";
            case "beefcooked" -> "cooked_beef";
            case "chickencooked" -> "cooked_chicken";
            case "fishcooked" -> "cooked_cod";
            case "milkbucket" -> "milk_bucket";
            case "bonemeal" -> "bone_meal";

            // Raw foods were "<thing>Raw"; the modern id is the bare noun.
            case "beefraw" -> "beef";
            case "porkchopraw" -> "porkchop";
            case "chickenraw" -> "chicken";
            case "fishraw" -> "cod";

            // 1.6 had exactly two flowers, named for their colours. "rose" is the
            // red one and "flower" the yellow one — the bare name meant yellow.
            case "rose" -> "poppy";
            case "flower", "yellowflower" -> "dandelion";

            // Buckets were "bucket<Contents>"; the modern id reverses the order.
            case "bucketwater" -> "water_bucket";
            case "bucketlava" -> "lava_bucket";
            case "bucketmilk", "milk" -> "milk_bucket";
            case "bucketempty" -> "bucket";

            // 1.6 had one "mushroom" (the brown one, block id 39) and a separate
            // "redMushroom"; NameMapFile lists both spellings of each. Unmapped,
            // "mushroom" resolved to an id nothing registers.
            case "mushroom", "brownmushroom", "mushroombrown" -> "brown_mushroom";
            case "redmushroom", "mushroomred" -> "red_mushroom";

            case "cocoabeans" -> "cocoa_beans";
            case "seeds_melon", "melonseeds" -> "melon_seeds";
            case "seeds_pumpkin", "pumpkinseeds" -> "pumpkin_seeds";
            case "sugarcane", "reed", "reeds" -> "sugar_cane";
            case "slimeball" -> "slime_ball";
            case "enderpearl" -> "ender_pearl";
            case "blazerod" -> "blaze_rod";
            case "blazepowder" -> "blaze_powder";
            case "glassbottle" -> "glass_bottle";
            // "netherStalk" was the crop block and "netherStalkSeeds" the item you
            // plant; both are minecraft:nether_wart now, which is one block and one
            // item sharing an id the way 1.6 could not.
            case "netherstalkseeds", "netherwart", "netherstalk" -> "nether_wart";
            case "goldnugget" -> "gold_nugget";
            case "ingotiron" -> "iron_ingot";
            case "ingotgold" -> "gold_ingot";

            // Ores were named the 1.6 way round, the noun first. A tool's
            // blocksEffective and canHarvest lists are written in these.
            case "orecoal" -> "coal_ore";
            case "oreiron" -> "iron_ore";
            case "oregold" -> "gold_ore";
            case "orediamond" -> "diamond_ore";
            case "orelapis" -> "lapis_ore";
            case "oreredstone", "oreredstoneglowing" -> "redstone_ore";
            case "oreemerald" -> "emerald_ore";

            // Names Mojang has simply changed since.
            case "sandstone" -> "sandstone";
            case "stonebrick" -> "stone_bricks";
            case "stonemoss", "cobblestonemossy" -> "mossy_cobblestone";
            case "hellrock" -> "netherrack";
            // 1.6 spent two names on clay: the item was "clay" and the block
            // "clayBlock". The block is minecraft:clay now, so the bare word keeps
            // resolving to it untouched and only the block's old name needs a row.
            case "clayblock" -> "clay";
            case "web" -> "cobweb";
            // "slowSand" is Block's own field name and "hellSand" its 1.6 texture
            // name; the nether packs write it the way a player says it.
            case "hellsand", "slowsand", "soulsand" -> "soul_sand";
            case "goldenrail" -> "powered_rail";
            // 1.6's stoneSlab was one block whose metadata picked the material; its
            // subtype 0, the plain stone one, is smooth stone slab now.
            case "stoneslab" -> "smooth_stone_slab";

            default -> {
                String tool = legacyToolPath(lower);
                yield tool != null ? tool : toPath(name);
            }
        };
    }

    /**
     * The potion a legacy {@code potion} reference names, as a modern potion id.
     *
     * <p>1.6's potion was one item whose damage value encoded the brew, and damage 0
     * was the water bottle. The modern item is still {@code minecraft:potion}, with
     * the brew in its {@code potion_contents} component, so the id alone matches every
     * potion. A bare reference is damage 0, as it is for {@code dyePowder}. Only the
     * water bottle is mapped: the other damage values were a bit field no content uses.
     *
     * @return the potion id, or empty when this is not a mapped potion reference
     */
    public static Optional<String> legacyPotion(ItemRef ref) {
        String name = ref.isVanilla() ? ref.vanillaName() : ref.name();
        if (!name.equalsIgnoreCase("potion") || (ref.hasMeta() && ref.meta() != 0)) {
            return Optional.empty();
        }
        return Optional.of("minecraft:water");
    }

    /** The five tool kinds 1.6 had, longest first so {@code axe} cannot eat a pickaxe. */
    private static final String[] TOOL_KINDS = {"pickaxe", "sword", "axe", "shovel", "hoe"};

    /**
     * A tool written the 1.6 way round, the noun first: {@code pickaxeWood} rather
     * than {@code woodenPickaxe}.
     *
     * <p>The same convention as the ores above, and for the same reason — these are
     * {@code Item}'s own field names, which is what a definition file could name.
     * {@code survivalpickaxe.json} overrides two of them. The iron tier was spelt
     * {@code Steel} in 1.6, so both spellings answer, on the same grounds
     * {@link PotionNames} takes both spellings of an effect.
     *
     * <p>Note that the content is not consistent about this: {@code cullStoneTools.json}
     * writes {@code stonePickaxe}, tier first, which {@link #toPath} already turns into
     * the modern id without help.
     *
     * @return the modern id path, or null when this is not a legacy tool name
     */
    private static String legacyToolPath(String lower) {
        for (String kind : TOOL_KINDS) {
            if (!lower.startsWith(kind)) {
                continue;
            }
            String tier = switch (lower.substring(kind.length())) {
                case "wood" -> "wooden";
                case "stone" -> "stone";
                case "iron", "steel" -> "iron";
                case "gold" -> "golden";
                case "diamond" -> "diamond";
                default -> null;
            };
            if (tier != null) {
                return tier + "_" + kind;
            }
        }
        return null;
    }
}
