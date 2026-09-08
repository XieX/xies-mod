package mod.xie.xm.block;

import mod.xie.xm.cook.StovePot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The stove's contents and its two timers.
 *
 * <p>The cooking rules live in {@link StovePot}, which knows nothing about
 * Minecraft and is unit-tested on its own. What is here is everything that needs a
 * world: how long the pot takes to come up to a meal, what keeps the fire in, and
 * how the block gets told to redraw.
 *
 * <p>Two timers:
 *
 * <ul>
 *   <li>{@code cookProgress} counts down to a servable pot. Adding an ingredient
 *       restarts it, so a cook who keeps throwing things in keeps pushing the meal
 *       back — but nothing is ever <em>refused</em> for being too soon. It gates
 *       the bowl, not the ingredient.</li>
 *   <li>{@code burnTime} is the fuel, and it burns the way a furnace's does:
 *       continuously, whether or not there is anything to cook. A pot left over a
 *       lit stove is being kept warm, which costs fuel and looks like it should.</li>
 * </ul>
 */
public class XieStoveBlockEntity extends BlockEntity {

    private static final String KEY_BASE = "Base";
    private static final String KEY_FOOD = "Food";
    private static final String KEY_FLAVOUR = "Flavour";
    private static final String KEY_COOK = "CookProgress";
    private static final String KEY_BURN = "BurnTime";

    /**
     * The registered type, bound by the registrar once the blocks exist.
     *
     * <p>A {@code BlockEntityType} names the blocks it is valid for, and a block
     * cannot be constructed without a type to hand out, so one of the two has to be
     * late. This is the one that can be: nothing constructs a stove block entity
     * until a world places one, long after registration has finished.
     */
    private static Supplier<BlockEntityType<XieStoveBlockEntity>> type = () -> null;

    public static void bindType(Supplier<BlockEntityType<XieStoveBlockEntity>> supplier) {
        type = supplier;
    }

    public static BlockEntityType<XieStoveBlockEntity> type() {
        return type.get();
    }

    private final StovePot pot;
    private final int cookTime;
    private final int fuelCapacity;

    private int cookProgress;
    private int burnTime;

    public XieStoveBlockEntity(BlockPos pos, BlockState state) {
        super(type(), pos, state);

        XieStoveBlock.Config config = XieStoveBlock.configOf(state);
        this.pot = new StovePot(config.foodCapacity(), config.flavourCapacity());
        this.cookTime = config.cookTime();
        this.fuelCapacity = config.fuelCapacity();
    }

    public StovePot pot() {
        return pot;
    }

    /** Whether the fire is in, which is what the {@code lit} blockstate shows. */
    public boolean isLit() {
        return burnTime > 0;
    }

    /**
     * Whether the pot has finished cooking and will fill a bowl.
     *
     * <p>An empty pot is trivially "cooked" and simply has nothing to give; the
     * distinction only matters once something is in it.
     */
    public boolean isCooked() {
        return cookProgress <= 0;
    }

    /** How far through the current cook, 0 to 1. */
    public float cookFraction() {
        if (cookTime <= 0 || cookProgress <= 0) {
            return 1.0f;
        }
        return 1.0f - ((float) cookProgress / cookTime);
    }

    /** Fuel remaining, in ticks. */
    public int burnTime() {
        return burnTime;
    }

    /**
     * Add fuel.
     *
     * <p>Capped so a player cannot empty a stack of coal into the pot in one click
     * and lose the rest of it. The cap is generous — a stove is meant to be lit and
     * left — but it is a cap, and a click that would overflow it is refused with the
     * item still in hand.
     */
    public boolean addFuel(int ticks) {
        if (ticks <= 0 || burnTime >= fuelCapacity) {
            return false;
        }
        burnTime = Math.min(fuelCapacity, burnTime + ticks);
        setChanged();
        return true;
    }

    /**
     * Put an ingredient in, and start the pot cooking again.
     *
     * <p>Never refused for timing. The original gated this — "wait until the pot
     * changes colour, indicating the next ingredient may be added" — and dropping
     * that is deliberate: a batch cooker that makes you wait between items is a
     * worse version of the same idea. What the timer gates now is the bowl.
     *
     * @param flavouring whether this is a flavouring rather than a food
     * @return whether the pot took it, which is a question of capacity alone
     */
    public boolean addIngredient(boolean flavouring) {
        boolean accepted = flavouring ? pot.addFlavour() : pot.addFood();
        if (!accepted) {
            return false;
        }
        cookProgress = cookTime;
        setChanged();
        return true;
    }

    /** Start the pot off with its liquid. */
    public boolean setBase(StovePot.Base base) {
        if (!pot.setBase(base)) {
            return false;
        }
        setChanged();
        return true;
    }

    /**
     * Ladle out the best meal the pot can afford.
     *
     * <p>Refused while the pot is still coming up to temperature. This is the whole
     * of what the cooking timer does, and the reason the block needs to show a
     * player when it is done.
     */
    public Optional<StovePot.Meal> extract() {
        if (!isCooked()) {
            return Optional.empty();
        }
        Optional<StovePot.Meal> meal = pot.extract();
        meal.ifPresent(ignored -> setChanged());
        return meal;
    }

    /**
     * What the pot looks like, as the {@code pot} blockstate value.
     *
     * <p>The cooked colour means "a bowl will fill", not merely "the timer ran out".
     * A pot of plain water has nothing to serve and shows raw, and so does one left
     * holding a single stranded flavouring — in both cases the player still has
     * something to add, and a pot that looked done would be lying about it.
     */
    public int appearance() {
        boolean servable = isCooked() && pot.canExtract();
        return switch (pot.base()) {
            case NONE -> XieStoveBlock.POT_EMPTY;
            case WATER -> servable ? XieStoveBlock.POT_WATER_READY : XieStoveBlock.POT_WATER_RAW;
        };
    }

    /**
     * Burn fuel down and cook.
     *
     * <p>Static because that is the shape {@code BlockEntityTicker} wants. Server
     * side only; the client is told about state changes through the blockstate.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, XieStoveBlockEntity stove) {
        boolean wasLit = stove.isLit();
        int wasAppearance = stove.appearance();

        if (stove.burnTime > 0) {
            stove.burnTime--;
            if (stove.cookProgress > 0) {
                stove.cookProgress--;
            }
            stove.setChanged();
        }

        // Only what a player can see is worth a block update: the fire going out,
        // and the pot finishing.
        if (wasLit != stove.isLit() || wasAppearance != stove.appearance()) {
            XieStoveBlock.updateAppearance(level, pos, state, stove);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        StovePot.Base base = parseBase(input.getStringOr(KEY_BASE, StovePot.Base.NONE.name()));
        pot.load(base, input.getIntOr(KEY_FOOD, 0), input.getIntOr(KEY_FLAVOUR, 0));

        cookProgress = Math.max(0, input.getIntOr(KEY_COOK, 0));
        burnTime = Math.max(0, input.getIntOr(KEY_BURN, 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        output.putString(KEY_BASE, pot.base().name());
        output.putInt(KEY_FOOD, pot.food());
        output.putInt(KEY_FLAVOUR, pot.flavour());
        output.putInt(KEY_COOK, cookProgress);
        output.putInt(KEY_BURN, burnTime);
    }

    /** A saved base that no longer parses leaves an empty pot rather than crashing. */
    private static StovePot.Base parseBase(String saved) {
        try {
            return StovePot.Base.valueOf(saved.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return StovePot.Base.NONE;
        }
    }
}
