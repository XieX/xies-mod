package mod.xie.xm.content.model;

import java.util.Locale;
import java.util.Objects;

/**
 * An unresolved reference to an item or block.
 *
 * <p>Deliberately <em>unresolved</em>: the parser records what the author wrote and
 * the registrar resolves it once every definition in every pack is known. That
 * removes the multi-pass "bump the job back onto the queue" retry loop the
 * original engine needed, because nothing here has to look anything up.
 *
 * <p>Legacy syntax, from {@code Xie.parseItemStack}:
 *
 * <pre>
 *   tomato              a Xie item or block by name
 *   farming@tomato      qualified by the file it was declared in
 *   tile.melon          a vanilla block by its legacy unlocalised name
 *   item.melon:4        ... with a stack count
 *   wheat:1:3           ... with a count and a damage/meta value
 *   $salady             a pack-defined alias — any of a named set of items
 *   #food               a built-in group the engine supplies
 * </pre>
 *
 * <p>Both group forms become item tags in modern Minecraft; they are kept distinct
 * because {@code $} groups are declared by content packs and {@code #} groups are
 * supplied by the engine.
 */
public record ItemRef(RefKind kind, String owner, String name, int count, int meta) {

    public enum RefKind {
        /** A concrete item or block. */
        ITEM,
        /** A pack-declared alias, written {@code $name}. */
        ALIAS,
        /** An engine-supplied group, written {@code #name}. */
        BUILTIN_GROUP
    }

    public static final int DEFAULT_COUNT = 1;
    public static final int ANY_META = -1;

    /** Separator between the declaring file and the name, {@code farming@tomato}. */
    public static final String QUALIFIER = "@";

    public ItemRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        if (count < 1) {
            count = DEFAULT_COUNT;
        }
    }

    public static ItemRef of(String name) {
        return new ItemRef(RefKind.ITEM, null, name, DEFAULT_COUNT, ANY_META);
    }

    /**
     * Parses one legacy reference. Never throws: a malformed count falls back to
     * the default so a single typo in a third-party pack cannot abort a whole load.
     */
    public static ItemRef parse(String raw) {
        String text = raw == null ? "" : raw.trim();

        RefKind kind = RefKind.ITEM;
        if (text.startsWith("$")) {
            kind = RefKind.ALIAS;
            text = text.substring(1).trim();
        } else if (text.startsWith("#")) {
            kind = RefKind.BUILTIN_GROUP;
            text = text.substring(1).trim();
        }

        int count = DEFAULT_COUNT;
        int meta = ANY_META;

        // "tile.melon" separates with a dot, so splitting on ':' is unambiguous.
        if (text.indexOf(':') >= 0) {
            String[] parts = text.split(":");
            text = parts[0].trim();
            if (parts.length > 1) {
                count = parseIntOr(parts[1], DEFAULT_COUNT);
            }
            if (parts.length > 2) {
                meta = parseIntOr(parts[2], ANY_META);
            }
        }

        String owner = null;
        int at = text.indexOf(QUALIFIER);
        if (at > 0) {
            owner = text.substring(0, at).trim();
            text = text.substring(at + QUALIFIER.length()).trim();
        }

        return new ItemRef(kind, owner, text, count, meta);
    }

    private static int parseIntOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean isGroup() {
        return kind != RefKind.ITEM;
    }

    /** True when this names a vanilla object by its legacy unlocalised name. */
    public boolean isVanilla() {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("tile.") || lower.startsWith("item.");
    }

    /** The vanilla name with its {@code tile.}/{@code item.} prefix stripped. */
    public String vanillaName() {
        return isVanilla() ? name.substring(name.indexOf('.') + 1) : name;
    }

    public boolean hasMeta() {
        return meta != ANY_META;
    }

    public String qualifiedName() {
        return owner == null ? name : owner + QUALIFIER + name;
    }

    /** This reference with its declaring file filled in, if it had none. */
    public ItemRef inheritOwner(String defaultOwner) {
        return owner != null ? this : new ItemRef(kind, defaultOwner, name, count, meta);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (kind) {
            case ALIAS -> sb.append('$');
            case BUILTIN_GROUP -> sb.append('#');
            case ITEM -> { }
        }
        sb.append(qualifiedName());
        if (count != DEFAULT_COUNT || hasMeta()) {
            sb.append(':').append(count);
        }
        if (hasMeta()) {
            sb.append(':').append(meta);
        }
        return sb.toString();
    }
}
