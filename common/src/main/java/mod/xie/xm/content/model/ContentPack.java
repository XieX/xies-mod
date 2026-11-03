package mod.xie.xm.content.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything one content pack declares, fully parsed and ready to register.
 *
 * <p>A pack is a directory (or zip) containing {@code .json} definition files,
 * optional {@code .loc} display names, and a {@code textures/} tree. The engine ships one of these; users drop more into
 * {@code <gamedir>/xiecontent/}.
 *
 * @param name          the pack directory name
 * @param blocks        block definitions by simple name
 * @param items         item definitions by simple name
 * @param recipes       every recipe, in declaration order
 * @param spawns        world generation entries
 * @param aliases       named item sets, merged across files
 * @param displayNames  {@code .loc} entries, overriding a definition's own name
 * @param texturePaths  {@code texturePaths} declarations, sheet alias to path
 */
public record ContentPack(
    String name,
    Map<String, BlockDef> blocks,
    Map<String, ItemDef> items,
    List<RecipeDef> recipes,
    List<SpawnDef> spawns,
    Map<String, AliasDef> aliases,
    Map<String, String> displayNames,
    Map<String, String> texturePaths
) {

    public ContentPack {
        blocks = Map.copyOf(blocks);
        items = Map.copyOf(items);
        recipes = List.copyOf(recipes);
        spawns = List.copyOf(spawns);
        aliases = Map.copyOf(aliases);
        displayNames = Map.copyOf(displayNames);
        texturePaths = Map.copyOf(texturePaths);
    }

    public static ContentPack empty(String name) {
        return new ContentPack(name, Map.of(), Map.of(), List.of(), List.of(),
            Map.of(), Map.of(), Map.of());
    }

    public int definitionCount() {
        return blocks.size() + items.size() + recipes.size() + spawns.size() + aliases.size();
    }

    public boolean isEmpty() {
        return definitionCount() == 0;
    }

    /**
     * The display name for a definition: a {@code .loc} entry wins over the
     * {@code name} property, matching how the original layered the two.
     */
    public Optional<String> displayNameFor(String definitionName) {
        String fromLoc = displayNames.get(definitionName);
        if (fromLoc != null) {
            return Optional.of(fromLoc);
        }
        BlockDef block = blocks.get(definitionName);
        if (block != null) {
            return Optional.of(block.displayName());
        }
        ItemDef item = items.get(definitionName);
        return item == null ? Optional.empty() : Optional.of(item.displayName());
    }

    /**
     * Every spritesheet filename referenced by any definition in this pack.
     *
     * <p>Deliberately not a consuming read of the texture declarations. Answering
     * "which sheets does this pack name" is a question about the content rather than a
     * use of it, and marking every definition's {@code tex} consumed here would leave
     * the unread-property report unable to see a texture assignment nothing draws.
     * Same distinction as {@code PropertyMap.peek}.
     */
    public java.util.SortedSet<String> referencedSheets() {
        java.util.SortedSet<String> sheets = new java.util.TreeSet<>();
        blocks.values().forEach(block -> block.peekTex().sheet().ifPresent(sheets::add));
        items.values().forEach(item -> item.peekTex().sheet().ifPresent(sheets::add));
        return sheets;
    }

    @Override
    public String toString() {
        return "ContentPack[" + name + ": " + blocks.size() + " blocks, " + items.size()
            + " items, " + recipes.size() + " recipes, " + spawns.size() + " spawns, "
            + aliases.size() + " aliases]";
    }
}
