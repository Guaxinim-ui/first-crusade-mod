package com.example.examplemod.animal;

import java.util.EnumSet;
import java.util.Map;

import com.example.examplemod.flora.FCFlora;
import com.example.examplemod.flora.FloraTags;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Grazing: the animal lowers its head where it stands, and the vegetation there is cropped.
 *
 * <h2>It crops, it does not clear</h2>
 *
 * The obvious implementation is the vanilla one — {@code EatBlockGoal} deletes the tall grass and
 * turns the grass block underneath into dirt. Copied here it would be a slow-motion disaster: the
 * mod's steppe is dressed by worldgen once and never regrows, so a herd left alone would strip the
 * biome to bare ground over a few in-game days, and nothing in the mod would ever put it back. That
 * is the same shape of mistake as the Ork pod — a system consuming the world with no ceiling on it,
 * where the damage only becomes visible long after the cause.
 *
 * <p>So a Grox <b>crops</b>: a two-block plant becomes its one-block counterpart, and a one-block
 * plant is only nosed at. Ground cover never falls to zero, the terrain reads as pasture rather than
 * as meadow, and the change is visible — which is the point of an animal that eats.
 *
 * <p>The consequence for gameplay is deliberate: a paddock of Grox looks grazed down, and land they
 * have not reached keeps its tall grass. Nothing is destroyed, so no repair system is owed.
 *
 * <h2>Cost</h2>
 *
 * One block lookup at the animal's own position, behind a random gate — no search of any kind. The
 * goal is the cheapest thing in the animal's list.
 */
public class GrazeGoal extends Goal {

    /** How long a mouthful takes, in ticks. Matches the vanilla eat animation length. */
    private static final int GRAZE_TICKS = 40;

    /** The tick the crop actually happens on, counting down — the head is down by then. */
    private static final int BITE_AT = 4;

    /**
     * Tall vegetation and what it is left as after a bite.
     *
     * <p>Written out rather than derived from the {@code tall_} name prefix: the reeds break the
     * pattern ({@code tall_marsh_reed} crops to {@code marsh_grass}, not to a "marsh_reed" that does
     * not exist), and a rule with an exception in it is worse than a list.
     *
     * <p><b>Invariant for whoever adds the next pair:</b> the short form must accept the same ground
     * tag as the tall one. All five here do (checked in {@code FCFlora}). A pair that did not would
     * produce a plant that fails {@code canSurvive} on the very next block update — the bite would
     * appear to delete the grass outright, which is the one behaviour this class exists to avoid.
     */
    private static final Map<Block, Block> CROPPED = Map.of(
            FCFlora.TALL_IMPERIAL_GRASS.get(), FCFlora.IMPERIAL_GRASS.get(),
            FCFlora.TALL_ASH_GRASS.get(), FCFlora.ASH_GRASS.get(),
            FCFlora.TALL_SQUIG_GRASS.get(), FCFlora.SQUIG_GRASS.get(),
            FCFlora.TALL_MIRE_REED.get(), FCFlora.MIRE_REED.get(),
            FCFlora.TALL_MARSH_REED.get(), FCFlora.MARSH_GRASS.get());

    private final FCAnimalEntity animal;
    private final Level level;

    /** One in this many ticks starts a mouthful. Calves eat far more often, as calves do. */
    private final int adultOdds;
    private final int calfOdds;

    private int grazeTicks;

    public GrazeGoal(FCAnimalEntity animal, int adultOdds, int calfOdds) {
        this.animal = animal;
        this.level = animal.level();
        this.adultOdds = adultOdds;
        this.calfOdds = calfOdds;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!FCAnimalConfig.ANIMAL_ECOLOGY_ENABLED.get() || this.animal.isAlarmed()) {
            return false;
        }

        if (this.animal.getRandom().nextInt(this.animal.isBaby() ? this.calfOdds : this.adultOdds) != 0) {
            return false;
        }

        return isGrass(this.level.getBlockState(this.animal.blockPosition()));
    }

    @Override
    public void start() {
        this.grazeTicks = adjustedTickDelay(GRAZE_TICKS);
        this.animal.getNavigation().stop();

        if (this.animal instanceof GeoEntity geo) {
            geo.triggerAnim("graze", "graze");
        }
    }

    @Override
    public void stop() {
        this.grazeTicks = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.grazeTicks > 0 && !this.animal.isAlarmed();
    }

    @Override
    public void tick() {
        this.grazeTicks = Math.max(0, this.grazeTicks - 1);

        if (this.grazeTicks != adjustedTickDelay(BITE_AT)) {
            return;
        }

        BlockPos pos = this.animal.blockPosition();
        if (!isGrass(this.level.getBlockState(pos))) {
            return;
        }

        // The same gamerule the vanilla animals respect: an operator who turned off mob griefing
        // asked for animals that do not change blocks, and cropping is a block change.
        if (this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            crop(pos);
        }

        this.animal.ate();
    }

    /** True for anything the mod calls grass — the tag is the vocabulary, not a block list. */
    private static boolean isGrass(BlockState state) {
        return state.is(FloraTags.FLORA_GRASS);
    }

    /**
     * Cuts a two-block plant down to its short form. A one-block plant survives the bite untouched.
     *
     * <p>Order matters. Writing the short plant into the lower half first makes the upper half
     * remove itself through {@code DoublePlantBlock.updateShape} — the same path that runs when a
     * player breaks the bottom of a sunflower — so the leftover clear is only a safety net.
     */
    private void crop(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        Block cropped = CROPPED.get(state.getBlock());
        if (cropped == null) {
            return;
        }

        BlockPos lower = pos;
        if (state.hasProperty(DoublePlantBlock.HALF)
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            lower = pos.below();
        }

        this.level.setBlock(lower, cropped.defaultBlockState(), Block.UPDATE_ALL);

        BlockPos upper = lower.above();
        BlockState above = this.level.getBlockState(upper);
        if (above.hasProperty(DoublePlantBlock.HALF)
                && above.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            this.level.removeBlock(upper, false);
        }
    }
}
