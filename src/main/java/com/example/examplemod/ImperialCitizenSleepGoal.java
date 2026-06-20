package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.EnumSet;

/**
 * At night an Imperial Citizen heads home to sleep: it walks to the nearest bed in its city and
 * settles there until dawn. While resting it reports {@code isRestingAtHome()} — the population
 * manager only lets a city raise children from adults who are home for the night, so beds (and
 * therefore houses) gate how fast a settlement can grow.
 */
public class ImperialCitizenSleepGoal extends Goal {
    private static final int SEARCH_RADIUS = 28;
    private static final double REST_DISTANCE_SQR = 2.5D * 2.5D;

    private final ImperialCitizenEntity citizen;
    private BlockPos bedPos;

    public ImperialCitizenSleepGoal(ImperialCitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!isNight() || this.citizen.getCommandCorePos() == null) {
            return false;
        }

        if (this.bedPos == null) {
            // Throttle the block scan so a town full of citizens isn't searching every tick.
            if (this.citizen.tickCount % 40 != 0) {
                return false;
            }
            this.bedPos = findNearestBed();
        }

        return this.bedPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return isNight() && this.bedPos != null;
    }

    // Night runs ~13000–23000 of the 24000-tick day; checked directly to avoid weather/API quirks.
    private boolean isNight() {
        long time = this.citizen.level().getDayTime() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    @Override
    public void start() {
        moveToBed();
    }

    @Override
    public void tick() {
        if (this.bedPos == null) {
            return;
        }

        double distSqr = this.citizen.distanceToSqr(
                this.bedPos.getX() + 0.5D,
                this.bedPos.getY(),
                this.bedPos.getZ() + 0.5D
        );

        if (distSqr <= REST_DISTANCE_SQR) {
            this.citizen.getNavigation().stop();
            this.citizen.getLookControl().setLookAt(
                    this.bedPos.getX() + 0.5D,
                    this.bedPos.getY() + 0.5D,
                    this.bedPos.getZ() + 0.5D
            );
            this.citizen.setRestingAtHome(true);
        } else if (this.citizen.getNavigation().isDone()) {
            moveToBed();
        }
    }

    @Override
    public void stop() {
        this.citizen.setRestingAtHome(false);
        this.bedPos = null;
    }

    private void moveToBed() {
        this.citizen.getNavigation().moveTo(
                this.bedPos.getX() + 0.5D,
                this.bedPos.getY(),
                this.bedPos.getZ() + 0.5D,
                0.8D
        );
    }

    // Nearest bed (counted once, by its foot block) within the city around the Core.
    private BlockPos findNearestBed() {
        BlockPos core = this.citizen.getCommandCorePos();
        if (core == null) {
            return null;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                for (int y = -5; y <= 6; y++) {
                    cursor.set(core.getX() + x, core.getY() + y, core.getZ() + z);
                    BlockState state = this.citizen.level().getBlockState(cursor);

                    if (!state.is(BlockTags.BEDS)) {
                        continue;
                    }
                    if (state.getValue(BlockStateProperties.BED_PART) != BedPart.FOOT) {
                        continue;
                    }

                    double dist = this.citizen.distanceToSqr(cursor.getX() + 0.5D, cursor.getY(), cursor.getZ() + 0.5D);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = cursor.immutable();
                    }
                }
            }
        }

        return best;
    }
}
