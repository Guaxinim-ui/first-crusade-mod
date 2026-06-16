package com.example.examplemod;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

public class SpaceMarineUpgradeManager {
    private static final String LEGACY_SPACE_MARINE_TAG = "FirstCrusadeSpaceMarine";

    private SpaceMarineUpgradeManager() {
    }

    public static GuardsmanEntity findBestAutomaticCandidate(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
    BlockPos corePos = commandCore.getBlockPos();
    int radius = 160;

    AABB searchBox = new AABB(
            corePos.getX() - radius,
            corePos.getY() - 64,
            corePos.getZ() - radius,
            corePos.getX() + radius,
            corePos.getY() + 96,
            corePos.getZ() + radius
    );

    List<GuardsmanEntity> candidates = serverLevel.getEntitiesOfClass(
            GuardsmanEntity.class,
            searchBox,
            guardsman -> guardsman.isAlive()
                    && isEligibleForSpaceMarineUpgrade(guardsman)
    );

    if (candidates.isEmpty()) {
        return null;
    }

    return candidates.stream()
            .max((first, second) -> {
                int rankCompare = Integer.compare(getRankPriority(first), getRankPriority(second));

                if (rankCompare != 0) {
                    return rankCompare;
                }

                double firstDistance = first.distanceToSqr(
                        corePos.getX() + 0.5D,
                        corePos.getY(),
                        corePos.getZ() + 0.5D
                );

                double secondDistance = second.distanceToSqr(
                        corePos.getX() + 0.5D,
                        corePos.getY(),
                        corePos.getZ() + 0.5D
                );

                return Double.compare(secondDistance, firstDistance);
            })
            .orElse(null);
}

    public static GuardsmanEntity findNearestUpgradeableGuardsman(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, BlockPos originPos) {
        BlockPos corePos = commandCore.getBlockPos();
        int radius = 160;

        AABB searchBox = new AABB(
                corePos.getX() - radius,
                corePos.getY() - 64,
                corePos.getZ() - radius,
                corePos.getX() + radius,
                corePos.getY() + 96,
                corePos.getZ() + radius
        );

        List<GuardsmanEntity> guardsmen = serverLevel.getEntitiesOfClass(
                GuardsmanEntity.class,
                searchBox,
                guardsman -> guardsman.isAlive()
                        && guardsman.isAssignedToCommandCore(corePos)
                        && isEligibleForSpaceMarineUpgrade(guardsman)
        );

        if (guardsmen.isEmpty()) {
            return null;
        }

        return guardsmen.stream()
                .min(Comparator.comparingDouble(guardsman -> guardsman.distanceToSqr(
                        originPos.getX() + 0.5D,
                        originPos.getY(),
                        originPos.getZ() + 0.5D
                )))
                .orElse(null);
    }

    public static GuardsmanEntity findGuardsmanByUUID(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, UUID uuid) {
    if (uuid == null) {
        return null;
    }

    BlockPos corePos = commandCore.getBlockPos();
    int radius = 160;

    AABB searchBox = new AABB(
            corePos.getX() - radius,
            corePos.getY() - 64,
            corePos.getZ() - radius,
            corePos.getX() + radius,
            corePos.getY() + 96,
            corePos.getZ() + radius
    );

    List<GuardsmanEntity> guardsmen = serverLevel.getEntitiesOfClass(
            GuardsmanEntity.class,
            searchBox,
            guardsman -> guardsman.isAlive()
                    && guardsman.getUUID().equals(uuid)
    );

    if (guardsmen.isEmpty()) {
        return null;
    }

    return guardsmen.get(0);
}

    public static void commandCandidateToCore(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, GuardsmanEntity guardsman) {
        BlockPos corePos = commandCore.getBlockPos();
        BlockPos promotionPos = getPromotionPosition(corePos);

        guardsman.assignToCommandCore(corePos);

        if (serverLevel.getBlockState(promotionPos.below()).isAir()) {
            serverLevel.setBlock(
                    promotionPos.below(),
                    net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),
                    3
            );
        }

        if (!serverLevel.getBlockState(promotionPos).isAir()) {
            serverLevel.setBlock(
                    promotionPos,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    3
            );
        }

        if (!serverLevel.getBlockState(promotionPos.above()).isAir()) {
            serverLevel.setBlock(
                    promotionPos.above(),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    3
            );
        }

        guardsman.assignGuardPost(promotionPos);

        guardsman.getNavigation().moveTo(
                promotionPos.getX() + 0.5D,
                promotionPos.getY(),
                promotionPos.getZ() + 0.5D,
                1.15D
        );
    }

    public static boolean isNearPromotionPosition(ImperialCommandCoreBlockEntity commandCore, GuardsmanEntity guardsman) {
        BlockPos promotionPos = getPromotionPosition(commandCore.getBlockPos());

        return guardsman.distanceToSqr(
                promotionPos.getX() + 0.5D,
                promotionPos.getY(),
                promotionPos.getZ() + 0.5D
        ) <= 16.0D;
    }

    public static void upgradeToSpaceMarine(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, GuardsmanEntity guardsman) {
        if (guardsman == null || !guardsman.isAlive()) {
            return;
        }

        SpaceMarineEntity spaceMarine = ExampleMod.SPACE_MARINE.get().create(serverLevel);

        if (spaceMarine == null) {
            return;
        }

        BlockPos corePos = commandCore.getBlockPos();
        BlockPos promotionPos = getPromotionPosition(corePos);

        double x = guardsman.getX();
        double y = guardsman.getY();
        double z = guardsman.getZ();
        float yRot = guardsman.getYRot();
        float xRot = guardsman.getXRot();

        spaceMarine.moveTo(x, y, z, yRot, xRot);
        spaceMarine.assignToCommandCore(corePos);
        spaceMarine.assignGuardPost(promotionPos);
        spaceMarine.equipAsSpaceMarine();
        spaceMarine.setHealth(spaceMarine.getMaxHealth());
        spaceMarine.setCustomName(Component.literal("Space Marine Initiate"));
        spaceMarine.setCustomNameVisible(true);
        spaceMarine.setPersistenceRequired();

        guardsman.discard();

        serverLevel.addFreshEntity(spaceMarine);

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                corePos,
                "A veteran Guardsman has ascended into a true Space Marine Initiate."
        );
    }

    public static boolean isSpaceMarine(GuardsmanEntity guardsman) {
        return guardsman.getPersistentData().getBoolean(LEGACY_SPACE_MARINE_TAG);
    }

    public static void refreshSpaceMarineState(GuardsmanEntity guardsman) {
        if (!isSpaceMarine(guardsman)) {
            return;
        }

        setAttributeBaseValue(guardsman, Attributes.MAX_HEALTH, 80.0D);
        setAttributeBaseValue(guardsman, Attributes.ARMOR, 18.0D);
        setAttributeBaseValue(guardsman, Attributes.ARMOR_TOUGHNESS, 8.0D);
        setAttributeBaseValue(guardsman, Attributes.ATTACK_DAMAGE, 10.0D);
        setAttributeBaseValue(guardsman, Attributes.MOVEMENT_SPEED, 0.32D);
        setAttributeBaseValue(guardsman, Attributes.FOLLOW_RANGE, 64.0D);

        guardsman.setCustomName(Component.literal("Legacy Space Marine Initiate"));
        guardsman.setCustomNameVisible(true);
        guardsman.setPersistenceRequired();
    }

    public static boolean isEligibleForSpaceMarineUpgrade(GuardsmanEntity guardsman) {
        if (isSpaceMarine(guardsman)) {
            return false;
        }

        return getRankPriority(guardsman) >= 3;
    }

    public static int countSpaceMarines(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();
        int radius = 160;

        AABB searchBox = new AABB(
                corePos.getX() - radius,
                corePos.getY() - 64,
                corePos.getZ() - radius,
                corePos.getX() + radius,
                corePos.getY() + 96,
                corePos.getZ() + radius
        );

        List<SpaceMarineEntity> spaceMarines = serverLevel.getEntitiesOfClass(
                SpaceMarineEntity.class,
                searchBox,
                spaceMarine -> spaceMarine.isAlive()
                        && spaceMarine.isAssignedToCommandCore(corePos)
        );

        return spaceMarines.size();
    }

    private static int getRankPriority(GuardsmanEntity guardsman) {
        GuardsmanRank rank = guardsman.getGuardsmanRank();

        return switch (rank) {
            case RECRUIT -> 1;
            case GUARDSMAN -> 2;
            case VETERAN -> 3;
            case SERGEANT -> 4;
            case LIEUTENANT -> 5;
            case CAPTAIN -> 6;
            default -> 0;
        };
    }

    private static BlockPos getPromotionPosition(BlockPos corePos) {
        return corePos.offset(0, 0, -3);
    }

    private static void setAttributeBaseValue(GuardsmanEntity guardsman, Attribute attribute, double value) {
        AttributeInstance attributeInstance = guardsman.getAttribute(attribute);

        if (attributeInstance != null) {
            attributeInstance.setBaseValue(value);
        }
    }
}