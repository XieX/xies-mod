package mod.xie.xm.content.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The scalar properties of a definition, keyed case-insensitively.
 *
 * <p>The {@code .xie} format spreads roughly eighty properties across sixteen block
 * and item archetypes, and third-party packs are free to use any of them. Rather
 * than a typed field per property — which would make the parser the bottleneck
 * every time an archetype gains an option — the parser normalises the
 * <em>structural</em> parts of a definition (drops, textures, bounds, recipes) and
 * leaves archetype-specific scalars here for the registrar to interpret.
 *
 * <p>Reads are tracked, so {@link #untouchedKeys()} reports properties nobody
 * consumed. That is how typos surface without the parser needing an allowlist.
 */
public final class PropertyMap {

    private final Map<String, JsonElement> values = new LinkedHashMap<>();
    private final Set<String> touched = new LinkedHashSet<>();

    public static PropertyMap empty() {
        return new PropertyMap();
    }

    public static PropertyMap of(JsonObject object) {
        PropertyMap map = new PropertyMap();
        if (object != null) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                map.values.put(normalise(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    private static String normalise(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    /** Every key present, in declaration order, normalised to lower case. */
    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /** Keys nobody has read yet — candidates for an "unknown property" warning. */
    public Set<String> untouchedKeys() {
        Set<String> remaining = new LinkedHashSet<>(values.keySet());
        remaining.removeAll(touched);
        return remaining;
    }

    /** Marks a key as consumed without reading it, for keys handled structurally. */
    public PropertyMap markUsed(String... keys) {
        for (String key : keys) {
            touched.add(normalise(key));
        }
        return this;
    }

    public boolean has(String key) {
        return values.containsKey(normalise(key));
    }

    public Optional<JsonElement> raw(String key) {
        String normalised = normalise(key);
        touched.add(normalised);
        return Optional.ofNullable(values.get(normalised));
    }

    /** Reads a key without marking it consumed. */
    public Optional<JsonElement> peek(String key) {
        return Optional.ofNullable(values.get(normalise(key)));
    }

    public Optional<String> getString(String key) {
        return raw(key).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
    }

    public String getString(String key, String fallback) {
        return getString(key).orElse(fallback);
    }

    public Optional<Integer> getInt(String key) {
        return raw(key).flatMap(PropertyMap::asInt);
    }

    public int getInt(String key, int fallback) {
        return getInt(key).orElse(fallback);
    }

    public Optional<Float> getFloat(String key) {
        return raw(key).flatMap(PropertyMap::asFloat);
    }

    public float getFloat(String key, float fallback) {
        return getFloat(key).orElse(fallback);
    }

    /**
     * Boolean coercion. Accepts real JSON booleans, the strings "true"/"false"
     * and any non-zero number.
     *
     * <p>Deviates from the original {@code Job.getBoolean}, which required a number
     * strictly greater than 1 and so read {@code 1} as false. That was a bug; real
     * content only ever used JSON booleans here.
     */
    public boolean getBoolean(String key, boolean fallback) {
        Optional<JsonElement> element = raw(key);
        if (element.isEmpty() || !element.get().isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.get().getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble() != 0d;
        }
        String text = primitive.getAsString().trim().toLowerCase(Locale.ROOT);
        if (text.equals("true") || text.equals("yes")) {
            return true;
        }
        if (text.equals("false") || text.equals("no")) {
            return false;
        }
        return fallback;
    }

    /**
     * Splits a comma-separated string property, as used by {@code growsOn},
     * {@code smelts}, {@code burnLevels} and friends. A JSON array works too.
     */
    public List<String> getStringList(String key) {
        Optional<JsonElement> element = raw(key);
        if (element.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        JsonElement value = element.get();
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(item -> {
                if (item.isJsonPrimitive()) {
                    addTrimmed(out, item.getAsString());
                }
            });
        } else if (value.isJsonPrimitive()) {
            for (String part : value.getAsString().split(",")) {
                addTrimmed(out, part);
            }
        }
        return out;
    }

    private static void addTrimmed(List<String> out, String text) {
        String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            out.add(trimmed);
        }
    }

    /** {@link #getStringList} parsed as item references. */
    public List<ItemRef> getRefList(String key) {
        List<ItemRef> refs = new ArrayList<>();
        for (String text : getStringList(key)) {
            refs.add(ItemRef.parse(text));
        }
        return refs;
    }

    /**
     * Reads a property that may be either a scalar or a metadata-keyed object, and
     * converts each value with {@code converter}.
     *
     * <p>Values that {@code converter} maps to null are dropped; the caller sees a
     * map missing that range rather than an exception.
     */
    public <T> Optional<MetaMap<T>> getMetaMap(String key, Function<JsonElement, T> converter) {
        Optional<JsonElement> element = raw(key);
        if (element.isEmpty()) {
            return Optional.empty();
        }
        JsonElement value = element.get();

        if (value.isJsonObject()) {
            MetaMap<T> map = MetaMap.varying();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                Optional<MetaRange> range = MetaRange.parse(entry.getKey());
                if (range.isEmpty()) {
                    continue;
                }
                T converted = converter.apply(entry.getValue());
                if (converted != null) {
                    map.put(range.get(), converted);
                }
            }
            return map.isEmpty() ? Optional.empty() : Optional.of(map);
        }

        T converted = converter.apply(value);
        return converted == null ? Optional.empty() : Optional.of(MetaMap.uniform(converted));
    }

    /** The metadata keys a range-mapped property failed to parse, for diagnostics. */
    public List<String> badMetaKeys(String key) {
        Optional<JsonElement> element = peek(key);
        if (element.isEmpty() || !element.get().isJsonObject()) {
            return List.of();
        }
        List<String> bad = new ArrayList<>();
        for (String rangeKey : element.get().getAsJsonObject().keySet()) {
            if (MetaRange.parse(rangeKey).isEmpty()) {
                bad.add(rangeKey);
            }
        }
        return bad;
    }

    /** Coerces a JSON value to an int. Real content quotes numbers about half the time. */
    public static Optional<Integer> asInt(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            // Values arrive as both numbers and quoted strings in real content.
            return Optional.of((int) Double.parseDouble(element.getAsString().trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Coerces a JSON value to a float. */
    public static Optional<Float> asFloat(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Float.parseFloat(element.getAsString().trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
