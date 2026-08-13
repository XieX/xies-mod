package mod.xie.xm.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * {@code spikey}: standing in this block hurts.
 *
 * <p>{@code XieBlock.onEntityCollidedWithBlock} is two lines —
 * {@code if (isSpikey && !world.isRemote) entity.attackEntityFrom(DamageSource.generic, 1)}
 * — and, like {@code becomesWhenDestroyed} before it, those two lines were copied into
 * every one of the original's block classes: {@code XieBlockLeaves},
 * {@code XieBlockLog}, {@code XieBlockPlant} and the rest each carry their own
 * {@code isSpikey} field and their own identical override. {@code setIsSpikey} is on
 * {@code IXieBlock}, so it really was every archetype. Hence one implementation here
 * and a one-line override in each of the port's block classes, which is what
 * {@link XieBecomes} already does for the same reason.
 *
 * <p>Only {@code blazeTree} in {@code _plox_update/nether_crops/flame_tree.xie}
 * declares it: a canopy of burning leaves you cannot climb through for free. The
 * archetypes with no class of their own cannot carry it — {@code stem} is vanilla's
 * {@code StemBlock} — and a definition declaring it there is reported by
 * {@link mod.xie.xm.reg.XieUnreadProperties} rather than silently dropped.
 *
 * <p>{@code CactusBlock} is the vanilla block that does exactly this, and it is the
 * shape copied: no rate limiting of its own, because a damaged entity is invulnerable
 * for the next few ticks anyway, and no check of {@code isPrecise}, which is a hint
 * about how deeply the entity overlaps that cactus also ignores. 1.6's own method fired
 * on every tick of contact too.
 */
final class XieSpikes {

    /** {@code attackEntityFrom(DamageSource.generic, 1)}. */
    private static final float DAMAGE = 1f;

    private XieSpikes() { }

    /**
     * Hurts whatever is inside the block, if the definition asked for it.
     *
     * <p>{@code hurtServer} rather than {@code hurt} is the {@code !world.isRemote}
     * guard the original had, said the way {@code SweetBerryBushBlock} says it.
     */
    static void hurt(boolean spikey, Level level, Entity entity) {
        if (spikey && level instanceof ServerLevel server) {
            entity.hurtServer(server, level.damageSources().generic(), DAMAGE);
        }
    }
}
