package mod.xie.xm.content.model;

/**
 * Where a definition came from.
 *
 * <p>The original engine identified everything as {@code parent@name}, where the
 * parent is the {@code .xie} file the definition was declared in. That naming is
 * kept because content packs in the wild reference it, and because it is what
 * disambiguates two packs that both define {@code tomato}.
 *
 * @param file     the {@code .xie} basename without extension, e.g. {@code tomato}
 * @param path     the path relative to the pack root, for diagnostics
 * @param name     the definition's own name, e.g. {@code tomatoBush}
 */
public record Origin(String file, String path, String name) {

    public String qualifiedName() {
        return file + ItemRef.QUALIFIER + name;
    }

    @Override
    public String toString() {
        return path + " [" + name + "]";
    }
}
