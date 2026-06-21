package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ImperialPopulationManager {
    private static final int CITIZEN_GROWTH_INTERVAL_TICKS = 1200;
    private static final int FOOD_PER_NEW_CITIZEN = 4;
    private static final int BED_SEARCH_RADIUS = 36;
    private static final int PARENTS_REQUIRED = 2;
    // A bedless outpost can still hold a handful of people roughing it; beds raise the ceiling above this.
    private static final int MIN_CAPACITY_WITHOUT_BEDS = 3;
    // Roughly 1 in N children is chosen as a Space Marine aspirant.
    private static final int ASPIRANT_CHANCE = 4;

    private ImperialPopulationManager() {
    }

    public static void tickCitizenGrowth(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        if (serverLevel.getGameTime() % CITIZEN_GROWTH_INTERVAL_TICKS != 0) {
            return;
        }

        // A discontented populace stops producing children until conditions improve.
        if (!ImperialCityMoraleManager.allowsGrowth(commandCore.getCityMorale())) {
            return;
        }

        int currentCitizens = countAssignedCitizens(serverLevel, commandCore);

        // Beds cap the population: every house (and so every wall expansion) raises the ceiling.
        int beds = countBeds(serverLevel, commandCore.getBlockPos());
        int capacity = Math.min(getCitizenCapacity(commandCore), Math.max(beds, MIN_CAPACITY_WITHOUT_BEDS));

        if (currentCitizens >= capacity) {
            return;
        }

        // Below two citizens a settlement attracts settlers to bootstrap itself; beyond that, new
        // citizens are children born of adults who are home asleep for the night.
        boolean bootstrapping = currentCitizens < PARENTS_REQUIRED;
        if (!bootstrapping && countRestingAdults(serverLevel, commandCore) < PARENTS_REQUIRED) {
            return;
        }

        BlockPos spawnPos = findFreeCitizenSpawnPosition(serverLevel, commandCore.getBlockPos());

        if (spawnPos == null) {
            return;
        }

        ImperialCitizenEntity citizen = ExampleMod.IMPERIAL_CITIZEN.get().create(serverLevel);

        if (citizen == null) {
            return;
        }

        citizen.assignToCommandCore(commandCore.getBlockPos());
        if (!bootstrapping) {
            citizen.setChild(ImperialCitizenEntity.CHILDHOOD_TICKS);

            // Some of the children are chosen as Astartes aspirants — but only an advanced city
            // (level 3+, harvesting gene-seed) can ever implant and ascend them.
            if (commandCore.getCityLevel() >= 3 && serverLevel.random.nextInt(ASPIRANT_CHANCE) == 0) {
                citizen.markAsAspirant();
            }
        }
        citizen.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        serverLevel.addFreshEntity(citizen);

        // A new mouth to feed: consume some stored Food if available (never blocks growth, so a
        // fledgling settlement without a Farm can still grow its first workers).
        commandCore.consumeFood(FOOD_PER_NEW_CITIZEN);

        commandCore.setChanged();
    }

    // Counts the city's beds (once per bed, by the foot block) within reach of the Core. Beds are
    // placed by the village builder and by any house the player adds.
    public static int countBeds(ServerLevel serverLevel, BlockPos corePos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int beds = 0;

        for (int x = -BED_SEARCH_RADIUS; x <= BED_SEARCH_RADIUS; x++) {
            for (int z = -BED_SEARCH_RADIUS; z <= BED_SEARCH_RADIUS; z++) {
                for (int y = -6; y <= 8; y++) {
                    cursor.set(corePos.getX() + x, corePos.getY() + y, corePos.getZ() + z);
                    BlockState state = serverLevel.getBlockState(cursor);

                    if (state.is(BlockTags.BEDS)
                            && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
                        beds++;
                    }
                }
            }
        }

        return beds;
    }

    // Adults currently home asleep — the potential parents for a new birth.
    public static int countRestingAdults(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> resting = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && !citizen.isChild()
                        && citizen.isRestingAtHome()
        );

        return resting.size();
    }

    public static boolean trainNearestCitizenAsGuardsman(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        ImperialCitizenEntity citizen = findNearestTrainableCitizen(serverLevel, commandCore, player);

        if (citizen == null) {
            player.displayClientMessage(Component.literal("No trainable Imperial Citizen found near the Command Core."), true);
            return false;
        }

        if (!commandCore.tryPayRecruitCost()) {
            player.displayClientMessage(Component.literal(
                    "Not enough Iron to train a " + commandCore.getCityType().getTroopName()
                            + " (needs " + commandCore.getCityType().getRecruitIronCost() + " Iron)."), true);
            return false;
        }

        GuardsmanEntity guardsman = ExampleMod.GUARDSMAN.get().create(serverLevel);

        if (guardsman == null) {
            player.displayClientMessage(Component.literal("Failed to create Guardsman."), true);
            return false;
        }

        guardsman.moveTo(
                citizen.getX(),
                citizen.getY(),
                citizen.getZ(),
                citizen.getYRot(),
                citizen.getXRot()
        );

        // Same identity as auto-recruits: random chapter + city rank/regiment (Fase C).
        guardsman.assignToCommandCore(commandCore.getBlockPos());
        guardsman.assignRandomChapter();
        guardsman.initializeFromCity(commandCore.getStartingGuardsmanRank(), commandCore.getCityType());

        citizen.discard();
        serverLevel.addFreshEntity(guardsman);

        player.displayClientMessage(Component.literal(
                commandCore.getCityType().getTroopName() + " trained from Imperial Citizen."), false);
        return true;
    }

    public static int countAssignedCitizens(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive() && citizen.isAssignedToCommandCore(corePos)
        );

        return citizens.size();
    }

    public static int countUnemployedCitizens(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.isUnemployed()
        );

        return citizens.size();
    }

    public static int countCitizensWithJob(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, ImperialCitizenJob job) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.hasJob(job)
        );

        return citizens.size();
    }

    /**
     * Single-pass tally of a city's assigned citizens: total, unemployed and a per-job breakdown.
     * Replaces ~10 separate {@code getEntitiesOfClass} scans (one per job + assigned + unemployed)
     * with one, which matters because the Core refreshes these stats a few times per second.
     */
    public static CitizenCensus censusAssignedCitizens(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive() && citizen.isAssignedToCommandCore(corePos)
        );

        CitizenCensus census = new CitizenCensus();
        census.assigned = citizens.size();

        for (ImperialCitizenEntity citizen : citizens) {
            if (citizen.isUnemployed()) {
                census.unemployed++;
            }

            ImperialCitizenJob job = citizen.getJob();

            if (job != null) {
                census.perJob[job.ordinal()]++;
            }
        }

        return census;
    }

    /** Result of {@link #censusAssignedCitizens}: counts gathered in a single citizen scan. */
    public static final class CitizenCensus {
        private final int[] perJob = new int[ImperialCitizenJob.values().length];
        private int assigned;
        private int unemployed;

        public int assigned() {
            return this.assigned;
        }

        public int unemployed() {
            return this.unemployed;
        }

        public int withJob(ImperialCitizenJob job) {
            return this.perJob[job.ordinal()];
        }
    }

    public static int getCitizenCapacity(ImperialCommandCoreBlockEntity commandCore) {
        int base = switch (commandCore.getCityLevel()) {
            case 1 -> 3;
            case 2 -> 6;
            case 3 -> 10;
            case 4 -> 15;
            default -> 25;
        };

        // City type scales population (e.g. Hive cities are far more crowded).
        int scaled = (int) Math.round(base * commandCore.getCityType().getPopulationFactor());

        return Math.max(1, scaled);
    }

    public static ImperialCitizenEntity findNearestTrainableCitizen(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Player player) {
        BlockPos corePos = commandCore.getBlockPos();

        List<ImperialCitizenEntity> citizens = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                createSearchBox(corePos, 96),
                citizen -> citizen.isAlive()
                        && citizen.isAssignedToCommandCore(corePos)
                        && citizen.isReadyForTraining()
        );

        ImperialCitizenEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ImperialCitizenEntity citizen : citizens) {
            double distance = citizen.distanceToSqr(player);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = citizen;
            }
        }

        return nearest;
    }

    private static BlockPos findFreeCitizenSpawnPosition(ServerLevel serverLevel, BlockPos corePos) {
        for (int radius = 4; radius <= 24; radius += 4) {
            for (int xOffset = -radius; xOffset <= radius; xOffset += 2) {
                for (int zOffset = -radius; zOffset <= radius; zOffset += 2) {
                    if (Math.abs(xOffset) != radius && Math.abs(zOffset) != radius) {
                        continue;
                    }

                    BlockPos searchPos = corePos.offset(xOffset, 0, zOffset);
                    BlockPos surfacePos = serverLevel.getHeightmapPos(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            searchPos
                    );

                    if (isSafeCitizenSpawnPosition(serverLevel, surfacePos)) {
                        return surfacePos;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isSafeCitizenSpawnPosition(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.isEmptyBlock(pos)) {
            return false;
        }

        if (!serverLevel.isEmptyBlock(pos.above())) {
            return false;
        }

        if (serverLevel.isEmptyBlock(pos.below())) {
            return false;
        }

        return true;
    }

    private static AABB createSearchBox(BlockPos center, int radius) {
        return new AABB(
                center.getX() - radius,
                center.getY() - 32,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + 64,
                center.getZ() + radius
        );
    }
}