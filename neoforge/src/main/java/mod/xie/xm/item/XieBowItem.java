package mod.xie.xm.item;

import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The {@code bow} archetype, and the only place in this port where a Xie item needs
 * a class of its own.
 *
 * <p>Every other item archetype dissolved into data components — a {@code sword} is
 * five components and no code at all. A bow cannot be, because {@code ammo},
 * {@code ammoless}, {@code ammoConsumed} and {@code semiautomatic} are decisions
 * taken while the bow is being drawn and fired, and a component describes an item
 * rather than acting for it.
 *
 * <p>What is <em>not</em> here is the interesting part. {@code XieItemBow} carried
 * three properties this class never mentions in its firing code, and a projectile
 * class of its own to hold two of them:
 *
 * <ul>
 *   <li><strong>{@code power}, {@code punch} and {@code flame} are extra
 *       enchantment levels</strong>, and 2013 said so outright —
 *       {@code basePower + EnchantmentHelper.getEnchantmentLevel(power, itemstack)}.
 *       So they are applied as extra enchantment levels, on a copy of the bow that
 *       the arrow keeps and nobody ever sees. See {@link #empowered}.</li>
 *   <li><strong>{@code XieEntityArrow} is {@code AbstractArrow}.</strong> Its whole
 *       reason for existing was to remember the item it was fired from and hand that
 *       back on pickup, so a spent redstone crossbow bolt could be recovered.
 *       {@code AbstractArrow.pickupItemStack} is that field, serialised under
 *       {@code item} and returned by {@code getPickupItem}, and
 *       {@code ProjectileWeaponItem.createProjectile} already fills it in from
 *       whatever was drawn — including ammunition that is not an arrow at all. So
 *       the redstone comes back with no code here, and there is no custom
 *       projectile.</li>
 * </ul>
 *
 * <p>{@code boneBow} declares neither behaviour nor ammunition, so it takes every
 * one of these branches' other side and is a vanilla bow with a different durability
 * and enchantability — which is exactly what {@code weapons/readme.txt} describes.
 */
public class XieBowItem extends BowItem {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The floor {@code XieItemBow.fire} clamped a semiautomatic shot's force up to:
     * {@code force = rand.nextFloat(); if (force < 0.9F) force = 0.9F;}. The scale is
     * unchanged from 2013 — a 1.6 arrow flew at {@code force * 2.0F * 1.5F} and a
     * modern one at {@code pow * 3.0F} — so the number carries over untouched.
     */
    private static final float SNAP_MIN_FORCE = 0.9f;

    /** {@code BowItem.releaseUsing}'s own floor, below which a bow does not fire. */
    private static final float MIN_FORCE = 0.1f;

    /** What {@code getPowerForTime} returns for a fully drawn bow, and a crit. */
    private static final float FULL_DRAW = 1.0f;

    /** {@code shootFromRotation}'s velocity per unit of draw, from {@code BowItem}. */
    private static final float VELOCITY_PER_FORCE = 3.0f;

    /** {@code BowItem}'s own spread. */
    private static final float UNCERTAINTY = 1.0f;

    /**
     * What a {@code bow} definition declares beyond {@code uses} and
     * {@code enchantability}, which are components and live in
     * {@code XieToolProperties} with the sword's.
     *
     * @param ammo          {@code ammo}, as namespaced item ids, resolved on first
     *                      shot; empty means {@code #minecraft:arrows}
     * @param ammoless      {@code ammoless} — fires whether or not you have any
     * @param ammoConsumed  {@code ammoConsumed} — the shot is unrecoverable
     * @param semiautomatic {@code semiautomatic} — fires on click without charging
     * @param power         {@code power}, as levels of Power added to the bow's own
     * @param punch         {@code punch}, as levels of Punch added to the bow's own
     * @param flame         whether {@code flame} was declared at all; see
     *                      {@link #empowered} for why it is not a duration
     */
    public record Config(List<String> ammo, boolean ammoless, boolean ammoConsumed,
                         boolean semiautomatic, int power, int punch, boolean flame) {

        public Config {
            ammo = List.copyOf(ammo);
        }
    }

    private final Config config;

    /**
     * {@code ammo} resolved to live items, on first use rather than in the
     * constructor: a bow is created while the item registry is still filling, and
     * nothing guarantees its ammunition registers before it does.
     */
    private volatile @Nullable List<Item> ammunition;

    public XieBowItem(Item.Properties properties, Config config) {
        super(properties);
        this.config = config;
    }

    public Config config() {
        return config;
    }

    // --------------------------------------------------------------- ammunition

    /**
     * What this bow will fire.
     *
     * <p>{@code XieItemBow} held a list of {@code Item}s and searched the inventory
     * for any of them; the modern shape is a predicate, and vanilla asks it for both
     * the held-projectile check and the inventory sweep. A bow declaring no
     * {@code ammo} keeps vanilla's {@code #minecraft:arrows}, which is the modern
     * spelling of the constructor's {@code ammunition.add(Item.arrow)} — it covers
     * tipped and spectral arrows too, which did not exist to be listed in 2013.
     */
    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        List<Item> declared = ammunition();
        return declared.isEmpty() ? ARROW_ONLY : stack -> declared.contains(stack.getItem());
    }

    /**
     * What a creative player fires when carrying none of it.
     *
     * <p>NeoForge asks the weapon rather than assuming an arrow, which is the hook a
     * bow firing redstone needs — {@code Player.getProjectile} would otherwise hand a
     * redstone crossbow an arrow that its own predicate rejects.
     */
    @Override
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack weapon) {
        return defaultAmmo();
    }

    /** {@code XieItemBow.getAmmo}'s fallback: the first thing on the list. */
    private ItemStack defaultAmmo() {
        List<Item> declared = ammunition();
        return declared.isEmpty()
            ? Items.ARROW.getDefaultInstance()
            : declared.get(0).getDefaultInstance();
    }

    private List<Item> ammunition() {
        List<Item> found = ammunition;
        if (found == null) {
            List<Item> items = new ArrayList<>(config.ammo().size());
            for (String id : config.ammo()) {
                Optional<Item> item = Optional.ofNullable(Identifier.tryParse(id))
                    .flatMap(BuiltInRegistries.ITEM::getOptional);
                if (item.isEmpty()) {
                    LOGGER.warn("Bow {} takes \"{}\" as ammunition, and no item answers to it",
                        BuiltInRegistries.ITEM.getKey(this), id);
                } else {
                    items.add(item.get());
                }
            }
            ammunition = found = List.copyOf(items);
        }
        return found;
    }

    // ------------------------------------------------------------------- firing

    /**
     * Right-click.
     *
     * <p>{@code semiautomatic} is the property whose name says the opposite of what
     * it does, and {@code weapons/readme.txt} calls it "fully-automatic": a
     * semiautomatic bow fires <em>here</em>, at once, and never charges. Holding the
     * button down repeats this every few ticks, which is the automatic fire the
     * readme describes. Everything else starts a draw, as a bow has since Alpha.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!config.semiautomatic()) {
            if (!config.ammoless()) {
                return super.use(level, player, hand);
            }
            // A bow that needs no ammunition can never be the empty-quiver case
            // super.use refuses on, so it goes straight to drawing.
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }

        ItemStack weapon = player.getItemInHand(hand);
        return fire(weapon, level, player, hand, snapForce(level.getRandom()))
            ? InteractionResult.SUCCESS
            : InteractionResult.FAIL;
    }

    /**
     * Letting go.
     *
     * <p>A semiautomatic bow already fired and has nothing left to do, which is
     * {@code XieItemBow.onPlayerStoppedUsing}'s one line. An ordinary one is
     * vanilla's, unless it is {@code ammoless} and so needs an ammunition stack
     * vanilla would not have found.
     */
    @Override
    public boolean releaseUsing(ItemStack weapon, Level level, LivingEntity entity, int remainingTime) {
        if (config.semiautomatic()) {
            return false;
        }
        if (!config.ammoless()) {
            return super.releaseUsing(weapon, level, entity, remainingTime);
        }
        if (!(entity instanceof Player player)) {
            return false;
        }

        float force = getPowerForTime(getUseDuration(weapon, entity) - remainingTime);
        return force >= MIN_FORCE && fire(weapon, level, player, player.getUsedItemHand(), force);
    }

    /**
     * One shot, which is {@code BowItem.releaseUsing}'s body with the ammunition
     * decided by {@link #ammoFor} and the force by the caller.
     *
     * @param force the draw, from 0 to 1: {@code getPowerForTime} for a charged bow
     *              and {@link #snapForce} for a semiautomatic one
     * @return whether anything was fired
     */
    private boolean fire(ItemStack weapon, Level level, Player player,
                         InteractionHand hand, float force) {
        ItemStack ammo = ammoFor(player, weapon);
        if (ammo.isEmpty()) {
            return false;
        }

        // draw() consumes from the inventory and honours Infinity and Multishot;
        // a free shot bypasses it entirely, exactly as freeFire did in 2013.
        List<ItemStack> shots = config.ammoless() ? List.of(freeShot(ammo)) : draw(weapon, ammo, player);
        if (shots.isEmpty()) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            shoot(serverLevel, player, hand, weapon, shots,
                force * VELOCITY_PER_FORCE, UNCERTAINTY, force == FULL_DRAW, null);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f,
            1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + force * 0.5f);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    /**
     * The stack this shot draws from, or empty when there is nothing to fire.
     *
     * <p>{@code Player.getProjectile} is the inventory sweep, and it already asks
     * {@link #getAllSupportedProjectiles}. An {@code ammoless} bow that finds nothing
     * falls back to the first thing it would have taken, which is
     * {@code XieItemBow.getAmmo} answering {@code ammunition.get(0)} for an empty
     * inventory.
     */
    private ItemStack ammoFor(Player player, ItemStack weapon) {
        ItemStack found = player.getProjectile(weapon);
        return !found.isEmpty() || !config.ammoless() ? found : defaultAmmo();
    }

    /**
     * A shot that cost nothing, and so leaves nothing to pick up.
     *
     * <p>2013 set {@code canBePickedUp = 0} on every free arrow, whether it was free
     * from {@code ammoless}, creative or Infinity. {@code INTANGIBLE_PROJECTILE} is
     * the modern spelling: {@code AbstractArrow}'s constructor reads it and takes
     * {@code Pickup.CREATIVE_ONLY}, which is what vanilla itself does with an
     * Infinity arrow. The one difference is that a creative player may pick the arrow
     * back up, and a creative player was never going to run out of them.
     */
    private static ItemStack freeShot(ItemStack ammo) {
        ItemStack shot = ammo.copyWithCount(1);
        shot.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        return shot;
    }

    /**
     * A semiautomatic bow's force: random, but never less than nine tenths of a full
     * draw. It is why the redstone crossbow hits about as hard as a charged bow
     * without ever being charged.
     */
    private static float snapForce(RandomSource random) {
        return Math.max(random.nextFloat(), SNAP_MIN_FORCE);
    }

    // -------------------------------------------------------------- the arrow

    /**
     * The projectile, which is vanilla's own.
     *
     * <p>Two things happen here and neither needs a class. The arrow is built by
     * {@code ProjectileWeaponItem} from the drawn stack, so it already remembers
     * whatever it was fired from and gives that back on pickup — the whole of what
     * {@code XieEntityArrow} was for. And {@code ammoConsumed} takes that away again.
     *
     * <p><strong>{@code ammoConsumed} does not mean "ammunition is consumed".</strong>
     * Ammunition is consumed whenever the shot was not free, with or without it; what
     * the property added was {@code entityarrow.canBePickedUp = 0}, so the shot cannot
     * be recovered from where it lands. It means the ammunition is gone for good,
     * which is what a redstone crossbow firing redstone needs to be worth building.
     */
    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon,
                                          ItemStack ammo, boolean isCrit) {
        Projectile projectile =
            super.createProjectile(level, shooter, empowered(level, weapon), ammo, isCrit);

        if (config.ammoConsumed() && projectile instanceof AbstractArrow arrow) {
            // After construction on purpose: AbstractArrow.setOwner promotes
            // DISALLOWED to ALLOWED for a player, and the constructor calls it.
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
        return projectile;
    }

    /**
     * The bow the arrow believes it was fired from: this one, plus whatever levels of
     * Power, Punch and Flame the definition declares.
     *
     * <p><strong>This is what the 2013 code says, read literally.</strong> Every one
     * of the three lines is the declared number <em>added to</em> an enchantment
     * level rather than substituted for it — {@code basePower +
     * EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemstack)} —
     * so a bow declaring {@code power: 1} and carrying Power II fires as Power III.
     * Handing the arrow a bow with the levels already added reproduces that exactly,
     * including the arithmetic, which nothing else does: applying a bonus separately
     * on top of the enchantment's own gets {@code (base + level) * 0.5 + 1.0} where
     * 2013 got {@code (base + level) * 0.5 + 0.5}.
     *
     * <p>And the arithmetic is vanilla's. Power adds {@code 1.0 + 0.5} per level above
     * the first, which is 2013's {@code power * 0.5 + 0.5} written as a data-driven
     * enchantment. Punch is the only route there is — {@code AbstractArrow} has a
     * {@code setBaseDamage} but no knockback setter at all, because
     * {@code doKnockback} asks {@code EnchantmentHelper.modifyKnockback} about the
     * weapon and nothing else. And Flame ignites for {@code duration: 100.0} seconds,
     * which is the number the next paragraph is about.
     *
     * <p><strong>{@code flame} was always a hundred seconds or nothing.</strong> The
     * line in {@code XieItemBow.fire} is {@code int flame = baseFlame +
     * getEnchantmentLevel(flame, itemstack) > 0 ? 100 : 0}, and both {@code +} and
     * {@code >} bind tighter than {@code ?:} — so the declared value only ever decided
     * <em>whether</em> the arrow burned, never for how long. {@code redstoneBow}'s
     * {@code "flame": 1} is not one second, and never was. The truncation is kept
     * rather than corrected because it lands on vanilla's own number: an arrow burning
     * for a hundred seconds outlives any flight, so what both versions actually mean is
     * "this arrow is on fire", and vanilla's Flame says the same thing with the same
     * 100. Reading it as a duration would make the redstone crossbow's arrows go out
     * in flight and stop setting anything alight, which is neither what the code did
     * nor what the readme promises.
     *
     * <p><strong>Nothing here is on the player's bow.</strong> {@code XieToolProperties.forSword}
     * declines the enchantment route for {@code flame} on {@code blazeSword}, because an
     * enchantment puts a glint and a tooltip line on the item and lets an anvil move
     * it somewhere else. None of that applies to a stack that exists for the length of
     * a constructor call: the copy goes into {@code AbstractArrow.firedFromWeapon},
     * which is what {@code getWeaponItem} answers with and what all three effects are
     * read from — {@code modifyDamage} at the hit, {@code modifyKnockback} in
     * {@code doKnockback}, and {@code applyOnProjectileSpawned} the moment the arrow
     * exists. The player's own bow is never touched, and {@code shoot} damages that
     * one rather than this.
     */
    private ItemStack empowered(Level level, ItemStack weapon) {
        if (config.power() <= 0 && config.punch() <= 0 && !config.flame()) {
            return weapon;
        }
        Optional<? extends HolderLookup<Enchantment>> lookup =
            level.registryAccess().lookup(Registries.ENCHANTMENT);
        if (lookup.isEmpty()) {
            return weapon;
        }

        ItemStack copy = weapon.copy();
        ItemEnchantments.Mutable levels = new ItemEnchantments.Mutable(
            copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));

        apply(levels, lookup.get(), Enchantments.POWER, config.power(), true);
        apply(levels, lookup.get(), Enchantments.PUNCH, config.punch(), true);
        // Flame is a flag rather than a count, so one level is the whole of it and a
        // bow already carrying it gains nothing. Its effect is level-independent.
        apply(levels, lookup.get(), Enchantments.FLAME, config.flame() ? 1 : 0, false);

        copy.set(DataComponents.ENCHANTMENTS, levels.toImmutable());
        return copy;
    }

    /**
     * @param cumulative whether the declared level adds to one the bow already
     *                   carries, as {@code power} and {@code punch} did, or merely
     *                   raises it to at least this much, as a flag does
     */
    private static void apply(ItemEnchantments.Mutable levels, HolderLookup<Enchantment> lookup,
                              ResourceKey<Enchantment> enchantment, int extra, boolean cumulative) {
        if (extra <= 0) {
            return;
        }
        Optional<? extends Holder<Enchantment>> holder = lookup.get(enchantment);
        if (holder.isEmpty()) {
            LOGGER.warn("No enchantment answers to {}; that part of a bow is left out",
                enchantment.identifier());
            return;
        }
        if (cumulative) {
            levels.set(holder.get(), levels.getLevel(holder.get()) + extra);
        } else {
            levels.upgrade(holder.get(), extra);
        }
    }
}
