package mod.xie.xm.content.model;

import java.util.Locale;

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
     * split where a reader would expect.
     */
    public static String toPath(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed";
        }
        StringBuilder out = new StringBuilder(name.length() + 4);
        char[] chars = name.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            boolean boundary = i > 0 && Character.isUpperCase(c)
                && (!Character.isUpperCase(chars[i - 1])
                    || (i + 1 < chars.length && Character.isLowerCase(chars[i + 1])));
            if (boundary && out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
            if (Character.isLetterOrDigit(c)) {
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
    public static String toVanillaPath(ItemRef ref) {
        String name = ref.isVanilla() ? ref.vanillaName() : ref.name();
        String lower = name.toLowerCase(Locale.ROOT);

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
            default -> toPath(name);
        };
    }
}
