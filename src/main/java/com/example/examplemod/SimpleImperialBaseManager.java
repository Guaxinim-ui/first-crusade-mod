package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The whole life of a simplified Imperial base: it is founded once, it keeps a small garrison, and
 * it does nothing else.
 *
 * <h2>What this is instead of</h2>
 *
 * {@link CityArchitect}, {@link StrategicConstructionBuilder}, {@link ImperialPatrolManager},
 * {@link ImperialWorkforceManager} and the Core's own autonomous governance between them placed
 * blocks every 20 ticks, mustered squads every 60, and re-sorted the garrison on every level-up.
 * None of that runs any more. Founding writes one 9x9 pad and stops; after that no {@code setBlock}
 * belonging to a base is ever issued again.
 *
 * <h2>Replenishment is a deadline, not a tick</h2>
 *
 * The base does not count down to its next recruit. It stores the game time at which it may next
 * look, and compares — so an unloaded chunk cannot fall behind, and a base at full strength costs a
 * single {@code long} comparison a minute.
 *
 * <h2>The counter is trusted; reality is checked only when it disagrees</h2>
 *
 * {@code recruitedGuardsmen} is the working number. A 32-block sweep for real soldiers happens only
 * when the counter claims the base is short — never on a schedule, never world-wide. That is the
 * difference between "the base is quiet" costing nothing and costing an entity scan per base.
 */
public final class SimpleImperialBaseManager {
    private SimpleImperialBaseManager() {
    }

    // ==================================================================== founding

    /**
     * Raises a brand-new base: a small pad of ground under the Core, a few props, four soldiers.
     *
     * <p>Called exactly once, by {@link WorldSettlementSeeder} for a world-generated base. There is
     * no second call and no incremental follow-up: what is written here is the base's whole
     * physical presence for the rest of the save.
     */
    public static void foundBase(ServerLevel level, ImperialCommandCoreBlockEntity core) {
        BlockPos centre = core.getBlockPos();

        buildFoundation(level, centre);
        placeProps(level, centre);

        WorldWarMapData.get(level).recordCity(centre);

        // The base is raised by a regiment, and the regiment is decided once, here, from the kind of
        // world it stands on. It governs who fills the garrison's slots — never how many, which
        // stays SimpleImperialBaseBalance's business.
        com.example.examplemod.crusade.ImperialCrusadeData data =
                com.example.examplemod.crusade.ImperialCrusadeData.get(level);
        data.roster(centre).setRegiment(
                com.example.examplemod.crusade.ImperialRegimentType.forCityType(core.getCityType()));
        data.setDirty();
    }

    /**
     * The 9x9 pad, laid one block below the Core and cleared two blocks above it.
     *
     * <p>Only air and replaceable growth are cleared: a base founded on somebody's roof does not eat
     * the roof. The pad itself never overwrites a block that is already solid ground — it fills the
     * gaps so the Core does not sit on a slope with a hole beside it.
     */
    private static void buildFoundation(ServerLevel level, BlockPos centre) {
        int half = SimpleImperialBaseBalance.FOUNDATION_HALF;
        int floorY = centre.getY() - 1;

        BlockState slab = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState trim = Blocks.DEEPSLATE_TILES.defaultBlockState();

        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos floor = new BlockPos(centre.getX() + dx, floorY, centre.getZ() + dz);

                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                BlockState state = edge || ((dx + dz) & 1) == 0 ? trim : slab;

                if (level.getBlockState(floor).isAir()
                        || level.getBlockState(floor).canBeReplaced()) {
                    level.setBlock(floor, state, 3);
                }

                // Two blocks of standing room, so a soldier is never spawned inside a bush.
                for (int dy = 0; dy < SimpleImperialBaseBalance.FOUNDATION_CLEAR_HEIGHT; dy++) {
                    BlockPos above = floor.above(dy + 1);

                    if (above.equals(centre)) {
                        continue;
                    }

                    BlockState current = level.getBlockState(above);
                    if (!current.isAir() && current.canBeReplaced()) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /** Four corner marks and a little clutter, so the pad reads as a camp rather than a floor. */
    private static void placeProps(ServerLevel level, BlockPos centre) {
        int half = SimpleImperialBaseBalance.FOUNDATION_HALF;

        int[][] corners = {{-half, -half}, {-half, half}, {half, -half}, {half, half}};

        for (int[] corner : corners) {
            BlockPos post = centre.offset(corner[0], 0, corner[1]);

            if (level.getBlockState(post).isAir()) {
                level.setBlock(post, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            }
            if (level.getBlockState(post.above()).isAir()) {
                level.setBlock(post.above(), Blocks.LANTERN.defaultBlockState(), 3);
            }
        }

        int[][] crates = {{-2, 3}, {3, -2}, {-3, -1}, {1, 3}};
        BlockState[] clutter = {
                Blocks.BARREL.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(),
                Blocks.BARREL.defaultBlockState(),
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
        };

        for (int i = 0; i < crates.length; i++) {
            BlockPos spot = centre.offset(crates[i][0], 0, crates[i][1]);
            if (level.getBlockState(spot).isAir()) {
                level.setBlock(spot, clutter[i], 3);
            }
        }
    }

    // ==================================================================== the periodic look

    /**
     * The base's only recurring work. Runs from the Core's slow tick; almost every call returns
     * after one comparison.
     */
    public static void tickBase(ServerLevel level, ImperialCommandCoreBlockEntity core) {
        // An old city migrates the first time it is looked at, then never again.
        if (!core.isSimplifiedBaseMigrated()) {
            migrateLegacyCity(level, core);
            core.markSimplifiedBaseMigrated();
        }

        long now = level.getGameTime();
        if (now < core.getGarrisonCheckReadyAt()) {
            return;
        }

        core.setGarrisonCheckReadyAt(now + SimpleImperialBaseBalance.REPLENISH_INTERVAL_TICKS);

        replenishGarrison(level, core);
    }

    /**
     * Raises at most one soldier, and only when both the counter and a look at the world agree the
     * base is short.
     *
     * <h2>Both, not either</h2>
     *
     * The counter is checked first because it is free. The sweep is then consulted as a second
     * opinion that can only ever say "there are <i>more</i> here than you thought" — measured, this
     * is the difference between a base that holds at ten and a base that quietly grew to twenty.
     * A soldier away from the ring (chasing an Ork, or out on a raid) is absent from the sweep but
     * is not a casualty, and treating it as one is what doubled the garrison.
     */
    private static void replenishGarrison(ServerLevel level, ImperialCommandCoreBlockEntity core) {
        int capacity = SimpleImperialBaseBalance.garrisonCapacity(core.getCityLevel());

        if (core.getRecruitedGuardsmen() >= capacity) {
            return;
        }

        // Only now does it become worth looking at the world.
        int actual = countGarrison(level, core.getBlockPos());
        core.raiseGarrisonCountTo(actual);

        if (core.getRecruitedGuardsmen() >= capacity) {
            return;
        }

        for (int i = 0; i < SimpleImperialBaseBalance.REPLENISH_PER_CHECK; i++) {
            core.raiseGarrisonSoldier(level);
        }
    }

    /** Living soldiers bound to this Core, within the small reconciliation radius. */
    public static int countGarrison(ServerLevel level, BlockPos corePos) {
        int radius = SimpleImperialBaseBalance.RECONCILE_SCAN_RADIUS;
        AABB box = box(corePos, radius);

        int guardsmen = level.getEntitiesOfClass(GuardsmanEntity.class, box,
                guardsman -> guardsman.isAlive() && guardsman.isAssignedToCommandCore(corePos)).size();

        int themed = level.getEntitiesOfClass(AbstractImperialTroopEntity.class, box,
                troop -> troop.isAlive() && troop.isAssignedToCommandCore(corePos)).size();

        return guardsmen + themed;
    }

    // ==================================================================== how a soldier stands about

    /**
     * Binds a soldier to its base: home here, wander this far, no guard post.
     *
     * <p>{@code restrictTo} is the vanilla leash the stroll goal already respects, which is why the
     * base needs no manager handing out waypoints. Any post the soldier used to hold is cleared —
     * that is what stops a migrated garrison from climbing back onto its old towers.
     */
    public static void bindToBase(PathfinderMob soldier, BlockPos corePos) {
        soldier.restrictTo(corePos, SimpleImperialBaseBalance.HOME_RADIUS);

        if (soldier instanceof GuardsmanEntity guardsman) {
            guardsman.assignToCommandCore(corePos);
            guardsman.clearGuardPost();
        } else if (soldier instanceof AbstractImperialTroopEntity troop) {
            troop.assignToCommandCore(corePos);
            troop.assignGuardPost(null);
        } else {
            return;
        }

        // This is the one chokepoint where a soldier becomes part of a base — the founding garrison,
        // a reinforcement, a migrated save and a raid survivor coming home all pass through here. So
        // it is where he is enlisted, and enlisting is idempotent: a veteran who marches home does
        // not come back a recruit.
        if (soldier.level() instanceof ServerLevel serverLevel) {
            com.example.examplemod.crusade.ImperialSoldierCareerManager.enlist(
                    serverLevel, corePos, soldier);
        }
    }

    /** Lifts the home leash while a soldier is away on a raid. */
    public static void releaseFromBase(PathfinderMob soldier) {
        soldier.clearRestriction();
    }

    // ==================================================================== legacy migration

    /**
     * Turns an old city into a simple base, once.
     *
     * <p>What it does <b>not</b> do is as important as what it does: no wall, house, road or tower
     * is removed. An existing save keeps every block it had — those buildings simply stop growing,
     * stop being surveyed and stop being staffed. Only the moving parts are stood down.
     */
    private static void migrateLegacyCity(ServerLevel level, ImperialCommandCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();

        cancelStrategicProjects(level, corePos);
        int standing = standDownGarrison(level, corePos);
        dismissBoundCitizens(level, corePos);

        // The one moment an exact recount is honest. An old city's tally counted citizens and
        // recruits in training as well as soldiers, and nothing is out on a raid yet, so this wide
        // sweep is the truth. Every later check may only raise the number (see replenishGarrison).
        core.setGarrisonCount(standing);
    }

    /** Drops every queued construction project belonging to this city. */
    private static void cancelStrategicProjects(ServerLevel level, BlockPos corePos) {
        StrategicWarAIData data = StrategicWarAIData.get(level);

        List<StrategicConstructionProject> doomed = new ArrayList<>();
        for (StrategicConstructionProject project : data.getProjects()) {
            if (project.getCorePos().equals(corePos)) {
                doomed.add(project);
            }
        }

        for (StrategicConstructionProject project : doomed) {
            data.removeProject(project);
        }
    }

    /**
     * Clears the guard posts of every soldier bound to this Core and gives them the home ring.
     *
     * @return how many living soldiers this Core actually has
     */
    private static int standDownGarrison(ServerLevel level, BlockPos corePos) {
        AABB box = box(corePos, SimpleImperialBaseBalance.RECONCILE_SCAN_RADIUS * 3);
        int standing = 0;

        for (GuardsmanEntity guardsman : level.getEntitiesOfClass(GuardsmanEntity.class, box,
                guardsman -> guardsman.isAlive() && guardsman.isAssignedToCommandCore(corePos))) {
            standing++;

            if (!com.example.examplemod.assault.ImperialExpeditionTags.isOnExpedition(guardsman)) {
                bindToBase(guardsman, corePos);
            }
        }

        for (AbstractImperialTroopEntity troop : level.getEntitiesOfClass(
                AbstractImperialTroopEntity.class, box,
                troop -> troop.isAlive() && troop.isAssignedToCommandCore(corePos))) {
            standing++;

            if (!com.example.examplemod.assault.ImperialExpeditionTags.isOnExpedition(troop)) {
                bindToBase(troop, corePos);
            }
        }

        return standing;
    }

    /**
     * Removes the workforce the old city created — and nothing else.
     *
     * <p>The filter is deliberately narrow. A citizen goes only if it is an
     * {@link ImperialCitizenEntity} assigned to <i>this exact</i> Core and it is not an aspirant
     * somewhere in the Space Marine pipeline. Vanilla villagers, named NPCs, Space Marines and the
     * Hive's own populace are not {@link ImperialCitizenEntity} bound to a Command Core, so none of
     * them can reach this list at all. An aspirant that is spared merely loses its job.
     */
    private static void dismissBoundCitizens(ServerLevel level, BlockPos corePos) {
        AABB box = box(corePos, SimpleImperialBaseBalance.RECONCILE_SCAN_RADIUS * 3);

        List<ImperialCitizenEntity> citizens = level.getEntitiesOfClass(
                ImperialCitizenEntity.class, box,
                citizen -> citizen.isAssignedToCommandCore(corePos));

        for (ImperialCitizenEntity citizen : citizens) {
            if (citizen.isAspirant()) {
                // Somebody halfway through gene-seed surgery keeps their body; they just stop working.
                citizen.clearJob();
                continue;
            }

            citizen.discard();
        }
    }

    // ==================================================================== helpers

    /** A safe patch of ground near the Core to stand a new soldier on. */
    public static BlockPos findStandingSpot(ServerLevel level, BlockPos corePos) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = level.random.nextInt(7) - 3;
            int dz = level.random.nextInt(7) - 3;

            BlockPos candidate = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    corePos.offset(dx, 0, dz));

            if (level.isEmptyBlock(candidate) && level.isEmptyBlock(candidate.above())
                    && !level.getBlockState(candidate.below()).isAir()) {
                return candidate;
            }
        }

        return corePos.above();
    }

    private static AABB box(BlockPos centre, int radius) {
        return new AABB(
                centre.getX() - radius, centre.getY() - 32, centre.getZ() - radius,
                centre.getX() + radius, centre.getY() + 48, centre.getZ() + radius);
    }
}
