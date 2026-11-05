package mod.xie.xm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Optional;

/**
 * The {@code ripening} archetype: a solid block that ages through its stages where it
 * stands and then becomes another block.
 *
 * <p>Fresh cheese is the first. A bucket of cheese pours out {@code freshCheese},
 * which turns into {@code cheeseBlock} about a day later, and a row of them along a
 * cellar wall shows how far along each one is. It is the pacing that keeps an infinite
 * source — cows never run out of milk — from being an instant one, in the way crops
 * are paced.
 *
 * <p>It is not a {@link XiePlantBlock}, and cannot extend one. A plant is vanilla's
 * {@code VegetationBlock} — walk-through, needs soil, pops off when that soil goes —
 * and every rule it adds is a plant's: light to grow, farmland to grow faster, bone
 * meal. A ripening block wants none of those. It shares the age property through
 * {@link XieAges}, and {@code growthRate} means the same thing here as on a plant: a
 * percentage of vanilla's default speed, 100 being one stage per 25 random-tick rolls.
 *
 * <p>Bone meal does nothing to it because it does not implement
 * {@code BonemealableBlock}, which is the only thing bone meal asks. Nothing hurries
 * cheese.
 *
 * <p>Breaking it before it ripens yields whatever its {@code drops} say, which for
 * fresh cheese is nothing.
 */
public class XieRipeningBlock extends Block {

    /**
     * Configuration parsed from a block definition.
     *
     * @param maxAge     highest stage before it becomes {@code ripensInto}
     * @param growthRate percentage; 100 is the default speed, higher is faster
     * @param ripensInto namespaced id of the block it becomes, from {@code growsInto};
     *                   empty leaves a block that ages and then stays as it is
     */
    public record Config(int maxAge, int growthRate, Optional<String> ripensInto) {

        public Config {
            maxAge = XieAges.clamp(maxAge);
            growthRate = Math.max(1, growthRate);
        }
    }

    private final Config config;
    private final IntegerProperty ageProperty;

    /**
     * Resolved the first time the block ripens rather than here, because blocks are
     * created while the registry is still filling. See {@link XieBecomes}, which
     * resolves {@code becomesWhenDestroyed} the same way for the same reason.
     */
    private volatile Optional<BlockState> ripened;

    /** The age property is built before this constructor's body runs; see {@link XieAges}. */
    public XieRipeningBlock(BlockBehaviour.Properties properties, Config config) {
        super(XieAges.prime(properties, config.maxAge()));
        XieAges.clear();

        this.config = config;
        this.ageProperty = XieAges.property(config.maxAge());
        registerDefaultState(this.stateDefinition.any().setValue(this.ageProperty, 0));
    }

    public Config config() {
        return config;
    }

    public int age(BlockState state) {
        return state.getValue(ageProperty);
    }

    public boolean isRipe(BlockState state) {
        return age(state) >= config.maxAge();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(XieAges.pending());
    }

    /** Once ripe with nothing to become, there is nothing left for it to do. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !isRipe(state) || config.ripensInto().isPresent();
    }

    /**
     * One roll per random tick, as a plant's: advance a stage, or, already at the last
     * one, become the ripened block. Taking a roll of its own for that last step means
     * the final stage is seen, as a ripe sapling is before it turns into a tree.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int denominator = Math.max(1, XiePlantBlock.GROWTH_DENOMINATOR * 100 / config.growthRate());
        if (random.nextInt(denominator) != 0) {
            return;
        }
        if (!isRipe(state)) {
            level.setBlock(pos, state.setValue(ageProperty, age(state) + 1), Block.UPDATE_CLIENTS);
            return;
        }
        ripenedState().ifPresent(into -> level.setBlock(pos, into, Block.UPDATE_ALL));
    }

    private Optional<BlockState> ripenedState() {
        Optional<BlockState> found = ripened;
        if (found == null) {
            ripened = found = config.ripensInto()
                .map(Identifier::tryParse)
                .flatMap(BuiltInRegistries.BLOCK::getOptional)
                .map(Block::defaultBlockState);
        }
        return found;
    }
}
