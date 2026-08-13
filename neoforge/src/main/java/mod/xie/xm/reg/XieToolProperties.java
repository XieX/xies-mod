package mod.xie.xm.reg;

import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ToolTier;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Translates the {@code tool} and {@code sword} archetypes into data components.
 *
 * <p>The same trade the food work made. {@code XieItemSword} and {@code XieItemTool}
 * were two hundred lines of overrides between them, and vanilla now has a word for
 * almost every one: {@code uses} is {@code minecraft:max_damage},
 * {@code enchantability} is {@code minecraft:enchantable}, {@code damage} is an
 * attack damage modifier in {@code minecraft:attribute_modifiers},
 * {@code blocksEffective} and {@code efficiency} are rules on
 * {@code minecraft:tool}, and {@code canBlock} — which no item has done since
 * shields arrived — is {@code minecraft:blocks_attacks}. Neither class survives;
 * one property of {@code XieItemTool} does, and it is
 * {@link mod.xie.xm.item.XieToolItem}.
 *
 * <p>What a swing is worth needs one conversion. 1.6's {@code getDamageVsEntity}
 * was the <em>total</em> damage dealt, and the modern component holds a modifier
 * added to the player's base attack damage of 1, so every number moves down by one:
 * an iron sword was 6 in 1.6 and is a modifier of 5 now, which is exactly what
 * {@code ToolMaterial.IRON} plus vanilla's sword baseline of 3 comes to. That the
 * arithmetic lands on vanilla's own values is the check that the reading is right.
 *
 * <p>The one property with no modern counterpart is attack speed, because 1.6 had
 * no attack cooldown at all. Each archetype takes the speed of the vanilla item it
 * is: a sword swings at a sword's rate and a tool at a pickaxe's.
 *
 * <p>Each archetype is written twice over, because a definition may describe an item
 * that does not exist yet or one that already does. {@link #forTool} and
 * {@link #forSword} build the components onto an {@code Item.Properties} as it
 * registers; {@link #applyToolTo} and {@link #applySwordTo} set the same components
 * onto an existing vanilla item, which is what {@code override} means. Only the
 * two-line applier differs — everything that reads the definition is shared, so the
 * two cannot drift.
 */
public final class XieToolProperties {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * {@code XieItemSword}'s constructor defaults, which were 1.6's iron sword. Note
     * that they overwrote whatever the tool material had just set, so a sword's
     * {@code material} decided nothing — every sword in the content declares all
     * three of these anyway.
     */
    private static final int SWORD_DAMAGE = 6;
    private static final int SWORD_USES = 250;
    private static final int SWORD_ENCHANTABILITY = 14;

    /**
     * Vanilla's own {@code Items.BOW}, which is what {@code XieItemBow} inherited and
     * defaulted to: {@code ItemBow}'s 384 uses untouched, and an explicit
     * {@code enchantability = 1} that is the same number.
     */
    private static final int BOW_USES = 384;
    private static final int BOW_ENCHANTABILITY = 1;

    /**
     * {@code JobItem.getToolDamage}. Unlike a sword's, a tool's {@code damage} is a
     * bonus over its tier's rather than the whole of it — {@code ItemTool} added the
     * two together — which is the same shape as vanilla's baselines: a pickaxe
     * passes 1, a sword 3.
     */
    private static final int TOOL_DAMAGE = 1;

    /** Vanilla's own baselines, from {@code Items}. 1.6 had no attack cooldown. */
    private static final float SWORD_ATTACK_SPEED = -2.4f;
    private static final float TOOL_ATTACK_SPEED = -2.8f;

    /** {@code ToolMaterial}'s own item damage per hit: 1 for a sword, 2 for a tool. */
    private static final Weapon SWORD_WEAPON = new Weapon(1);
    private static final Weapon TOOL_WEAPON = new Weapon(2);

    /** The player's base attack damage, which the item's modifier adds to. */
    private static final float BASE_ATTACK_DAMAGE = 1.0f;

    /** What {@code getStrVsBlock} returned for a block a tool was no good against. */
    private static final float PLAIN_MINING_SPEED = 1.0f;

    /** {@code XieItemSword}'s default {@code canCut}, and vanilla's sword rule. */
    private static final float COBWEB_MINING_SPEED = 15.0f;

    /** Blocking halved the damage in 1.6; a shield stops all of it. */
    private static final float BLOCKED_FRACTION = 0.5f;

    /** What {@code blocksEffective} says when a tool is good against everything. */
    private static final String EVERYTHING = "all";

    private XieToolProperties() { }

    /**
     * A list of block names a definition writes, and the generated tag it becomes.
     *
     * <p>{@code minecraft:tool} takes block tags or block sets rather than a list of
     * ids, so each declared list is written into a tag of its own. The registrar
     * names the tag and the emitter fills it; both go through {@link #tagPath} so
     * they cannot disagree about which file the rule points at.
     */
    public enum BlockList {
        /** {@code blocksEffective} — what the tool mines quickly. */
        EFFECTIVE("blocksEffective", "effective"),
        /** {@code canHarvest} — what it mines and gets the drops from. */
        HARVESTABLE("canHarvest", "harvestable"),
        /** {@code cannotHarvest} — what it explicitly does not. */
        UNHARVESTABLE("cannotHarvest", "unharvestable"),
        /** {@code canCut} — a sword's, defaulting to cobweb alone. */
        CUTS("canCut", "cuts");

        private final String property;
        private final String tagName;

        BlockList(String property, String tagName) {
            this.property = property;
            this.tagName = tagName;
        }

        public String property() {
            return property;
        }

        /** Which lists an archetype reads. A sword only ever cut things. */
        public static List<BlockList> forArchetype(String archetype) {
            return archetype.equals("sword")
                ? List.of(CUTS)
                : List.of(EFFECTIVE, HARVESTABLE, UNHARVESTABLE);
        }
    }

    /**
     * The tag one of a definition's block lists is written into.
     *
     * <p>Grouped under the item so that a generated pack lists a tool's tags
     * together: {@code tags/block/tool/bone_sword/cuts.json}.
     */
    public static String tagPath(String itemPath, BlockList list) {
        return "tool/" + itemPath + "/" + list.tagName;
    }

    // ------------------------------------------------------------------ sword

    /**
     * A sword: {@code damage}, {@code uses}, {@code enchantability} and
     * {@code canBlock}.
     *
     * <p>{@code flame} is the one property left out, and it is the one that was
     * behaviour rather than description — {@code XieItemSword.hitEntity} set the
     * target alight. Vanilla's only word for that is an enchantment, which would put
     * a glint and a tooltip line on the sword and let an anvil move it to another
     * one, none of which the property did.
     */
    public static Item.Properties forSword(ItemDef def, String path, Item.Properties properties) {
        properties
            .durability(def.props().getInt("uses", SWORD_USES))
            .enchantable(def.props().getInt("enchantability", SWORD_ENCHANTABILITY))
            .attributes(swordAttributes(def))
            .component(DataComponents.TOOL, swordTool(def, path, bootstrapBlockLookup()))
            .component(DataComponents.WEAPON, SWORD_WEAPON);

        // XieItemSword.onItemRightClick put the sword up unless canBlock said not
        // to, which is how every sword worked before 1.9.
        if (blocks(def)) {
            properties.delayedComponent(DataComponents.BLOCKS_ATTACKS, XieToolProperties::blocksAttacks);
        }
        return properties;
    }

    /**
     * A sword's components written over an item that already exists. See
     * {@link #applyToolTo} for what "over" means and why it is a replacement.
     */
    public static void applySwordTo(ItemDef def, String path, DataComponentMap.Builder components,
                                    HolderLookup.Provider context) {
        durability(components, def.props().getInt("uses", SWORD_USES))
            .set(DataComponents.ENCHANTABLE,
                new Enchantable(def.props().getInt("enchantability", SWORD_ENCHANTABILITY)))
            .set(DataComponents.ATTRIBUTE_MODIFIERS, swordAttributes(def))
            .set(DataComponents.TOOL, swordTool(def, path, context.lookupOrThrow(Registries.BLOCK)))
            .set(DataComponents.WEAPON, SWORD_WEAPON)
            // Null removes rather than doing nothing, which is what "canBlock": false
            // has to mean over an item that may already have the component.
            .set(DataComponents.BLOCKS_ATTACKS, blocks(def) ? blocksAttacks(context) : null);
    }

    private static ItemAttributeModifiers swordAttributes(ItemDef def) {
        return meleeAttributes(def.props().getInt("damage", SWORD_DAMAGE), SWORD_ATTACK_SPEED);
    }

    /** Whether this sword may be raised. {@code glassSword} is the one that says no. */
    private static boolean blocks(ItemDef def) {
        return def.props().getBoolean("canBlock", true);
    }

    /**
     * What raising a 1.6 sword did: halve the incoming damage, the instant it went
     * up, for no durability and with nothing able to disable it.
     */
    private static BlocksAttacks blocksAttacks(HolderLookup.Provider context) {
        return new BlocksAttacks(
            0.0f,
            1.0f,
            List.of(new BlocksAttacks.DamageReduction(90.0f, Optional.empty(), 0.0f, BLOCKED_FRACTION)),
            new BlocksAttacks.ItemDamageFunction(1.0f, 0.0f, 0.0f),
            Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
            Optional.empty(),
            Optional.empty());
    }

    /**
     * A sword's mining rules, which are vanilla's with {@code canCut} substituted
     * for the cobweb.
     *
     * <p>{@code XieItemSword.getStrVsBlock} gave 15 against anything in
     * {@code canCut} and 1.5 against everything else; vanilla narrows that 1.5 to
     * the plants in {@code #minecraft:sword_efficient} and adds the instant-mining
     * set, which is the same idea with thirteen years of blocks in it.
     */
    private static Tool swordTool(ItemDef def, String path, HolderGetter<Block> lookup) {
        List<Tool.Rule> rules = new ArrayList<>();

        rules.add(Tool.Rule.minesAndDrops(
            tagFor(def, path, BlockList.CUTS, lookup)
                .orElseGet(() -> HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBWEB))),
            COBWEB_MINING_SPEED));
        rules.add(Tool.Rule.overrideSpeed(lookup.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE));
        rules.add(Tool.Rule.overrideSpeed(lookup.getOrThrow(BlockTags.SWORD_EFFICIENT), 1.5f));

        return new Tool(rules, PLAIN_MINING_SPEED, 2, false);
    }

    // -------------------------------------------------------------------- bow

    /**
     * A bow's two component-shaped properties, which are the sword's two.
     *
     * <p>{@code JobItem.doBow} read {@code uses} and {@code enchantability} and
     * nothing else that a component can hold — the rest is behaviour and lives in
     * {@link mod.xie.xm.item.XieBowItem}. Unlike a tool, and like a sword, a bow
     * really did read both: {@code setNumberOfUses} is {@code setMaxDamage} and
     * {@code getItemEnchantability} returns whatever was declared.
     *
     * <p>{@code redstoneBow} declares no {@code uses} and so keeps vanilla's 384,
     * which {@code weapons/readme.txt} states in so many words — "as durable as a
     * normal bow".
     */
    public static Item.Properties forBow(ItemDef def, Item.Properties properties) {
        return properties
            .durability(def.props().getInt("uses", BOW_USES))
            .enchantable(def.props().getInt("enchantability", BOW_ENCHANTABILITY));
    }

    // ------------------------------------------------------------------- tool

    /**
     * A tool: its tier, what it is good against, and how fast.
     *
     * <p>Durability and enchantability come from the tier and nothing else, because
     * {@code JobItem.doTool} never read {@code uses} or {@code enchantability} —
     * only the sword branch did.
     *
     * <p>{@code transforms} is the one property of the archetype that is not here,
     * and the only one that is not a description of the tool: it replaces the block
     * you right-click, which no component says. It lives on
     * {@link mod.xie.xm.item.XieToolItem}, which only a definition declaring the
     * property is given — everything below applies either way.
     */
    public static Item.Properties forTool(ItemDef def, String path, Item.Properties properties) {
        ToolMaterial material = materialFor(def);

        return properties
            .durability(material.durability())
            .enchantable(material.enchantmentValue())
            .repairable(material.repairItems())
            .attributes(toolAttributes(def, material))
            .component(DataComponents.TOOL, tool(def, path, material, bootstrapBlockLookup()))
            .component(DataComponents.WEAPON, TOOL_WEAPON);
    }

    /**
     * The same components, written over an item that already exists.
     *
     * <p>This is the whole of what a {@code tool} override needs, and it needs
     * nothing else: {@code XieRegistrar} registers no item for one, and NeoForge's
     * {@code ModifyDefaultComponentsEvent} hands over the vanilla item's default
     * components to patch in place. What {@link #forTool} says with
     * {@code Item.Properties} this says with the same component types directly, and
     * {@code Item.Properties.durability} is the only one that is more than a rename —
     * it sets three.
     *
     * <p><strong>Every set is a replacement, and that is the point.</strong> A
     * vanilla pickaxe already carries a {@code minecraft:tool} built from its
     * {@code ToolMaterial}, and {@code survivalpickaxe.xie} exists to make a wooden
     * pickaxe mine <em>less</em> than vanilla's — so the existing rules have to go
     * rather than be added to. {@code DataComponentMap.Builder.set} overwrites, which
     * is exactly that.
     *
     * <p>The lookup comes from the context rather than from
     * {@link #bootstrapBlockLookup}, because by the time this runs the registries are
     * long frozen and that call would throw. It is the better lookup anyway: the
     * event fires from {@code ReloadableServerResources} with
     * {@code lookupWithUpdatedTags}, so the generated block tags a rule names are
     * already loaded and bound rather than being created empty and filled in later.
     */
    public static void applyToolTo(ItemDef def, String path, DataComponentMap.Builder components,
                                   HolderLookup.Provider context) {
        ToolMaterial material = materialFor(def);

        durability(components, material.durability())
            .set(DataComponents.ENCHANTABLE, new Enchantable(material.enchantmentValue()))
            .set(DataComponents.REPAIRABLE, repairable(material, context))
            .set(DataComponents.ATTRIBUTE_MODIFIERS, toolAttributes(def, material))
            .set(DataComponents.TOOL, tool(def, path, material, context.lookupOrThrow(Registries.BLOCK)))
            .set(DataComponents.WEAPON, TOOL_WEAPON);
    }

    private static ItemAttributeModifiers toolAttributes(ItemDef def, ToolMaterial material) {
        int damage = def.props().getInt("damage", TOOL_DAMAGE);
        return meleeAttributes(damage + material.attackDamageBonus(), TOOL_ATTACK_SPEED);
    }

    /** {@code Item.Properties.repairable}, which is a tag lookup and a component. */
    private static Repairable repairable(ToolMaterial material, HolderLookup.Provider context) {
        return new Repairable(context.lookupOrThrow(Registries.ITEM).getOrThrow(material.repairItems()));
    }

    /** The three components {@code Item.Properties.durability} sets between them. */
    private static DataComponentMap.Builder durability(DataComponentMap.Builder components, int uses) {
        return components
            .set(DataComponents.MAX_DAMAGE, uses)
            .set(DataComponents.MAX_STACK_SIZE, 1)
            .set(DataComponents.DAMAGE, 0);
    }

    /**
     * A tool's mining rules.
     *
     * <p>Three lists, in the order {@code XieItemTool} consulted them:
     * {@code cannotHarvest} refused first, {@code canHarvest} allowed, and
     * {@code blocksEffective} decided the speed. First match wins per field in
     * {@link Tool}, which is the same precedence.
     *
     * <p>The tier contributes nothing here. {@code XieItemTool.canHarvestBlock}
     * ignored the material entirely — it asked the declared lists and nothing else —
     * so no {@code incorrect_for_*_tool} rule is added, and a wooden-tier Xie tool
     * mines whatever its content says it does.
     */
    private static Tool tool(ItemDef def, String path, ToolMaterial material,
                             HolderGetter<Block> lookup) {
        float efficiency = def.props().getFloat("efficiency", material.speed());
        List<Tool.Rule> rules = new ArrayList<>();

        tagFor(def, path, BlockList.UNHARVESTABLE, lookup)
            .ifPresent(blocks -> rules.add(Tool.Rule.deniesDrops(blocks)));
        tagFor(def, path, BlockList.HARVESTABLE, lookup)
            .ifPresent(blocks -> rules.add(new Tool.Rule(blocks, Optional.empty(), Optional.of(true))));

        // With no canHarvest of its own, a tool drops what it is effective against.
        // XieItemTool answered false to everything in that case, which made a tool
        // that mined fast and yielded nothing; the content plainly means the other.
        Optional<Boolean> drops = declares(def, BlockList.HARVESTABLE)
            ? Optional.empty()
            : Optional.of(true);

        effective(def, path, lookup)
            .ifPresent(blocks -> rules.add(new Tool.Rule(blocks, Optional.of(efficiency), drops)));

        return new Tool(rules, PLAIN_MINING_SPEED, 1, true);
    }

    /**
     * What {@code blocksEffective} covers.
     *
     * <p>{@code "all"} is not a list and is not written out as one. It was a flag —
     * {@code makeUltimate} — and the answer is every block there is, taken from the
     * registry as the item registers. A tag would have to enumerate the same
     * thousand-odd ids into the generated pack and would then stop working if
     * somebody turned that pack off.
     */
    private static Optional<HolderSet<Block>> effective(ItemDef def, String path,
                                                        HolderGetter<Block> lookup) {
        if (isEverything(def)) {
            List<Holder<Block>> everything = BuiltInRegistries.BLOCK.listElements()
                .map(holder -> (Holder<Block>) holder).toList();
            LOGGER.info("Tool {} is effective against every block, which is {} of them",
                def.qualifiedName(), everything.size());
            return Optional.of(HolderSet.direct(everything));
        }
        return tagFor(def, path, BlockList.EFFECTIVE, lookup);
    }

    /** Whether {@code blocksEffective} is the {@code "all"} flag rather than a list. */
    public static boolean isEverything(ItemDef def) {
        return def.props().getString(BlockList.EFFECTIVE.property())
            .map(value -> value.trim().equalsIgnoreCase(EVERYTHING))
            .orElse(false);
    }

    /**
     * The tier a tool's {@code material} names.
     *
     * <p>An unrecognised name was wood, and said so in the log — which is what
     * happened to {@code ultimateFist}, whose {@code "diamond"} matched no
     * {@code EnumToolMaterial} constant in 1.6 because the diamond tier was called
     * {@code EMERALD}. {@link ToolTier} answers for both spellings now.
     */
    private static ToolMaterial materialFor(ItemDef def) {
        Optional<String> declared = def.props().getString("material");
        if (declared.isEmpty()) {
            return ToolMaterial.WOOD;
        }
        Optional<ToolTier> tier = ToolTier.forName(declared.get());
        if (tier.isEmpty()) {
            LOGGER.warn("Tool {} asks for the material \"{}\", which no tier answers to; using {}",
                def.qualifiedName(), declared.get(), ToolTier.DEFAULT);
        }
        return switch (tier.orElse(ToolTier.DEFAULT)) {
            case WOOD -> ToolMaterial.WOOD;
            case STONE -> ToolMaterial.STONE;
            case COPPER -> ToolMaterial.COPPER;
            case IRON -> ToolMaterial.IRON;
            case DIAMOND -> ToolMaterial.DIAMOND;
            case GOLD -> ToolMaterial.GOLD;
            case NETHERITE -> ToolMaterial.NETHERITE;
        };
    }

    // --------------------------------------------------------------- plumbing

    /**
     * The attack damage and speed a melee item carries.
     *
     * @param totalDamage what 1.6 dealt in full, which the modifier is one less than
     */
    private static ItemAttributeModifiers meleeAttributes(float totalDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                    totalDamage - BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                    attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }

    /**
     * The generated tag for one of a definition's block lists, or empty when it
     * declares none.
     *
     * <p>Naming a tag through {@link #bootstrapBlockLookup} creates it unbound, which
     * is exactly what vanilla's own tools do with {@code #minecraft:mineable/pickaxe}
     * — the contents arrive when the datapack loads. A definition whose list resolved
     * to nothing therefore gets an empty tag rather than a broken one.
     *
     * <p>{@link #applyToolTo} passes a loaded lookup instead, where a tag that was
     * never written is genuinely absent. That is why this asks rather than demands:
     * the answer is the same for a bootstrap lookup, which creates whatever it is
     * asked for.
     */
    private static Optional<HolderSet<Block>> tagFor(ItemDef def, String path, BlockList list,
                                                     HolderGetter<Block> lookup) {
        if (!declares(def, list)) {
            return Optional.empty();
        }
        TagKey<Block> tag = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(XiesMod.MOD_ID, tagPath(path, list)));
        Optional<HolderSet<Block>> blocks = lookup.get(tag).map(named -> named);
        if (blocks.isEmpty()) {
            LOGGER.warn("{} names {} for its {}, and no such tag is loaded; that rule is left out",
                def.qualifiedName(), tag.location(), list.property());
        }
        return blocks;
    }

    /**
     * Whether a definition names any block for this list. The same test the registrar
     * uses to decide whether to write the tag, so a rule never points at a file that
     * was never written.
     */
    public static boolean declares(ItemDef def, BlockList list) {
        return !def.props().getStringList(list.property()).isEmpty();
    }

    /**
     * A lookup that can name a tag before anything has defined it.
     *
     * <p>This is what {@code ToolMaterial.applyToolProperties} uses, and it only
     * works while the registries are still open — which the item property supplier
     * is, being called from the item registration event itself.
     * {@code MappedRegistry.createRegistrationLookup} validates the write and throws
     * once the registry is frozen, so an override cannot use it.
     */
    private static HolderGetter<Block> bootstrapBlockLookup() {
        return BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
    }
}
