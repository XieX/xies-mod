package mod.xie.xm.content.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The 1.6 numeric block ids, as modern block id paths.
 *
 * <p>{@link ResourceNames#toVanillaPath} maps a legacy <em>name</em> onto a modern
 * one, which covers almost everything content writes. Worldgen is the exception:
 * {@code Xie.parseBlock} accepted a bare number and indexed {@code Block.blocksList}
 * with it, so a {@code spawns} entry could — and in the live content does — name a
 * block by id. {@code salt.xie} says {@code "spawnsNear": "8,9"}, which is flowing
 * and still water rather than anything you could look up by name.
 *
 * <p>Numeric ids were removed in 1.7, so this is a frozen historical table rather
 * than something that can be derived. It is deliberately kept apart from the name
 * table: a name is ambiguous between a block and an item, but an id is not — 0 to
 * 255 were blocks and 256 up were items — so this only ever answers for blocks, and
 * callers that resolve items must not consult it.
 *
 * <p>Where 1.6 spent two ids on one block's two states — flowing and still water,
 * lit and unlit furnace, both halves of a double slab — both map onto the single
 * modern block, because the distinction is a blockstate property now.
 */
public final class LegacyBlockIds {

    private static final Map<Integer, String> PATHS = new HashMap<>(192);

    private LegacyBlockIds() { }

    private static void id(int id, String path) {
        PATHS.put(id, path);
    }

    static {
        id(0, "air");
        id(1, "stone");
        id(2, "grass_block");
        id(3, "dirt");
        id(4, "cobblestone");
        id(5, "oak_planks");
        id(6, "oak_sapling");
        id(7, "bedrock");
        id(8, "water");
        id(9, "water");
        id(10, "lava");
        id(11, "lava");
        id(12, "sand");
        id(13, "gravel");
        id(14, "gold_ore");
        id(15, "iron_ore");
        id(16, "coal_ore");
        id(17, "oak_log");
        id(18, "oak_leaves");
        id(19, "sponge");
        id(20, "glass");
        id(21, "lapis_ore");
        id(22, "lapis_block");
        id(23, "dispenser");
        id(24, "sandstone");
        id(25, "note_block");
        id(26, "red_bed");
        id(27, "powered_rail");
        id(28, "detector_rail");
        id(29, "sticky_piston");
        id(30, "cobweb");
        id(31, "short_grass");
        id(32, "dead_bush");
        id(33, "piston");
        id(34, "piston_head");
        id(35, "white_wool");
        id(36, "moving_piston");
        id(37, "dandelion");
        id(38, "poppy");
        id(39, "brown_mushroom");
        id(40, "red_mushroom");
        id(41, "gold_block");
        id(42, "iron_block");
        id(43, "smooth_stone_slab");
        id(44, "stone_slab");
        id(45, "bricks");
        id(46, "tnt");
        id(47, "bookshelf");
        id(48, "mossy_cobblestone");
        id(49, "obsidian");
        id(50, "torch");
        id(51, "fire");
        id(52, "spawner");
        id(53, "oak_stairs");
        id(54, "chest");
        id(55, "redstone_wire");
        id(56, "diamond_ore");
        id(57, "diamond_block");
        id(58, "crafting_table");
        id(59, "wheat");
        id(60, "farmland");
        id(61, "furnace");
        id(62, "furnace");
        id(63, "oak_sign");
        id(64, "oak_door");
        id(65, "ladder");
        id(66, "rail");
        id(67, "cobblestone_stairs");
        id(68, "oak_wall_sign");
        id(69, "lever");
        id(70, "stone_pressure_plate");
        id(71, "iron_door");
        id(72, "oak_pressure_plate");
        id(73, "redstone_ore");
        id(74, "redstone_ore");
        id(75, "redstone_torch");
        id(76, "redstone_torch");
        id(77, "stone_button");
        id(78, "snow");
        id(79, "ice");
        id(80, "snow_block");
        id(81, "cactus");
        id(82, "clay");
        id(83, "sugar_cane");
        id(84, "jukebox");
        id(85, "oak_fence");
        id(86, "carved_pumpkin");
        id(87, "netherrack");
        id(88, "soul_sand");
        id(89, "glowstone");
        id(90, "nether_portal");
        id(91, "jack_o_lantern");
        id(92, "cake");
        id(93, "repeater");
        id(94, "repeater");
        // 95 was the never-implemented locked chest and has no successor.
        id(96, "oak_trapdoor");
        id(97, "infested_stone");
        id(98, "stone_bricks");
        id(99, "brown_mushroom_block");
        id(100, "red_mushroom_block");
        id(101, "iron_bars");
        id(102, "glass_pane");
        id(103, "melon");
        id(104, "pumpkin_stem");
        id(105, "melon_stem");
        id(106, "vine");
        id(107, "oak_fence_gate");
        id(108, "brick_stairs");
        id(109, "stone_brick_stairs");
        id(110, "mycelium");
        id(111, "lily_pad");
        id(112, "nether_bricks");
        id(113, "nether_brick_fence");
        id(114, "nether_brick_stairs");
        id(115, "nether_wart");
        id(116, "enchanting_table");
        id(117, "brewing_stand");
        id(118, "cauldron");
        id(119, "end_portal");
        id(120, "end_portal_frame");
        id(121, "end_stone");
        id(122, "dragon_egg");
        id(123, "redstone_lamp");
        id(124, "redstone_lamp");
        id(125, "oak_slab");
        id(126, "oak_slab");
        id(127, "cocoa");
        id(128, "sandstone_stairs");
        id(129, "emerald_ore");
        id(130, "ender_chest");
        id(131, "tripwire_hook");
        id(132, "tripwire");
        id(133, "emerald_block");
        id(134, "spruce_stairs");
        id(135, "birch_stairs");
        id(136, "jungle_stairs");
        id(137, "command_block");
        id(138, "beacon");
        id(139, "cobblestone_wall");
        id(140, "flower_pot");
        id(141, "carrots");
        id(142, "potatoes");
        id(143, "oak_button");
        id(144, "skeleton_skull");
        id(145, "anvil");
        id(146, "trapped_chest");
        id(147, "light_weighted_pressure_plate");
        id(148, "heavy_weighted_pressure_plate");
        id(149, "comparator");
        id(150, "comparator");
        id(151, "daylight_detector");
        id(152, "redstone_block");
        id(153, "nether_quartz_ore");
        id(154, "hopper");
        id(155, "quartz_block");
        id(156, "quartz_stairs");
        id(157, "activator_rail");
        id(158, "dropper");
    }

    /** Whether a reference is a bare number, and so an id rather than a name. */
    public static boolean isNumeric(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** The modern id path for a 1.6 block id, or empty when nothing corresponds. */
    public static Optional<String> path(int id) {
        return Optional.ofNullable(PATHS.get(id));
    }

    /**
     * The modern id path for a reference written as a bare number, or empty when it
     * is not a number at all — in which case the caller should fall back to
     * {@link ResourceNames#toVanillaPath}.
     */
    public static Optional<String> path(String name) {
        if (!isNumeric(name)) {
            return Optional.empty();
        }
        try {
            return path(Integer.parseInt(name));
        } catch (NumberFormatException e) {
            // Longer than an int; certainly not a 1.6 block id.
            return Optional.empty();
        }
    }
}
