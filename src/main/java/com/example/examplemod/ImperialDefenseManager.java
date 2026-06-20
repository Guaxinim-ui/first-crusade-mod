package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class ImperialDefenseManager {
    private ImperialDefenseManager() {
    }

    public static int rallyDefenders(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<GuardsmanEntity> guardsmen = findAssignedGuardsmen(serverLevel, commandCore);
        List<SpaceMarineEntity> spaceMarines = findAssignedSpaceMarines(serverLevel, commandCore);

        int affected = 0;
        int index = 0;

        for (GuardsmanEntity guardsman : guardsmen) {
            BlockPos defensePost = findDefensePost(corePos, commandCore.getCityLevel(), index);

            prepareDefensePost(serverLevel, corePos, defensePost);
            guardsman.assignGuardPost(defensePost);
            moveMobToPost(guardsman, defensePost);

            affected++;
            index++;
        }

        for (SpaceMarineEntity spaceMarine : spaceMarines) {
            BlockPos defensePost = findDefensePost(corePos, commandCore.getCityLevel(), index);

            prepareDefensePost(serverLevel, corePos, defensePost);
            spaceMarine.assignGuardPost(defensePost);
            moveMobToPost(spaceMarine, defensePost);

            affected++;
            index++;
        }

        return affected;
    }

    public static int fortifyDefenders(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        List<GuardsmanEntity> guardsmen = findAssignedGuardsmen(serverLevel, commandCore);
        List<SpaceMarineEntity> spaceMarines = findAssignedSpaceMarines(serverLevel, commandCore);

        int affected = 0;

        for (GuardsmanEntity guardsman : guardsmen) {
            fortifyGuardsman(guardsman);
            affected++;
        }

        for (SpaceMarineEntity spaceMarine : spaceMarines) {
            fortifySpaceMarine(spaceMarine);
            affected++;
        }

        return affected;
    }

    public static int countDefenders(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        return findAssignedGuardsmen(serverLevel, commandCore).size()
                + findAssignedSpaceMarines(serverLevel, commandCore).size();
    }

    public static void notifyDefenseCommand(ServerLevel serverLevel, BlockPos corePos, String message) {
        notifyDefenseCommand(serverLevel, corePos, Component.literal(message));
    }

    public static void notifyDefenseCommand(ServerLevel serverLevel, BlockPos corePos, Component message) {
        double range = 128.0D;
        double rangeSquared = range * range;

        for (ServerPlayer serverPlayer : serverLevel.players()) {
            if (serverPlayer.distanceToSqr(
                    corePos.getX() + 0.5D,
                    corePos.getY() + 0.5D,
                    corePos.getZ() + 0.5D
            ) <= rangeSquared) {
                serverPlayer.displayClientMessage(message, false);
            }
        }
    }

    private static List<GuardsmanEntity> findAssignedGuardsmen(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
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

        return serverLevel.getEntitiesOfClass(
                GuardsmanEntity.class,
                searchBox,
                guardsman -> guardsman.isAlive()
                        && guardsman.isAssignedToCommandCore(corePos)
        );
    }

    private static List<SpaceMarineEntity> findAssignedSpaceMarines(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
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

        return serverLevel.getEntitiesOfClass(
                SpaceMarineEntity.class,
                searchBox,
                spaceMarine -> spaceMarine.isAlive()
                        && spaceMarine.isAssignedToCommandCore(corePos)
        );
    }

    private static BlockPos findDefensePost(BlockPos corePos, int cityLevel, int index) {
        int radius = switch (cityLevel) {
            case 1 -> 5;
            case 2 -> 8;
            case 3 -> 11;
            case 4 -> 15;
            case 5 -> 20;
            default -> 8;
        };

        int positionIndex = index % 16;

        return switch (positionIndex) {
            case 0 -> corePos.offset(0, 0, radius);
            case 1 -> corePos.offset(radius, 0, 0);
            case 2 -> corePos.offset(0, 0, -radius);
            case 3 -> corePos.offset(-radius, 0, 0);

            case 4 -> corePos.offset(radius, 0, radius);
            case 5 -> corePos.offset(-radius, 0, radius);
            case 6 -> corePos.offset(radius, 0, -radius);
            case 7 -> corePos.offset(-radius, 0, -radius);

            case 8 -> corePos.offset(radius / 2, 0, radius);
            case 9 -> corePos.offset(-radius / 2, 0, radius);
            case 10 -> corePos.offset(radius, 0, radius / 2);
            case 11 -> corePos.offset(radius, 0, -radius / 2);

            case 12 -> corePos.offset(radius / 2, 0, -radius);
            case 13 -> corePos.offset(-radius / 2, 0, -radius);
            case 14 -> corePos.offset(-radius, 0, radius / 2);
            case 15 -> corePos.offset(-radius, 0, -radius / 2);

            default -> corePos.offset(0, 0, radius);
        };
    }

    private static void prepareDefensePost(ServerLevel serverLevel, BlockPos corePos, BlockPos defensePost) {
        if (defensePost.equals(corePos)) {
            return;
        }

        BlockPos floor = defensePost.below();

        if (!floor.equals(corePos) && serverLevel.getBlockState(floor).isAir()) {
            serverLevel.setBlock(floor, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }

        clearIfNeeded(serverLevel, corePos, defensePost);
        clearIfNeeded(serverLevel, corePos, defensePost.above());
    }

    private static void clearIfNeeded(ServerLevel serverLevel, BlockPos corePos, BlockPos pos) {
        if (pos.equals(corePos)) {
            return;
        }

        if (!serverLevel.getBlockState(pos).isAir()) {
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void moveMobToPost(Mob mob, BlockPos defensePost) {
        double x = defensePost.getX() + 0.5D;
        double y = defensePost.getY();
        double z = defensePost.getZ() + 0.5D;

        double distance = mob.distanceToSqr(x, y, z);

        if (distance > 2500.0D) {
            mob.teleportTo(x, y, z);
            return;
        }

        mob.getNavigation().moveTo(x, y, z, 1.15D);
    }

    private static void fortifyGuardsman(GuardsmanEntity guardsman) {
        applyEffect(guardsman, MobEffects.DAMAGE_RESISTANCE, 2400, 1);
        applyEffect(guardsman, MobEffects.DAMAGE_BOOST, 2400, 0);
        applyEffect(guardsman, MobEffects.MOVEMENT_SPEED, 2400, 0);
        applyEffect(guardsman, MobEffects.REGENERATION, 300, 0);

        guardsman.heal(8.0F);
    }

    private static void fortifySpaceMarine(SpaceMarineEntity spaceMarine) {
        applyEffect(spaceMarine, MobEffects.DAMAGE_RESISTANCE, 3000, 1);
        applyEffect(spaceMarine, MobEffects.DAMAGE_BOOST, 3000, 1);
        applyEffect(spaceMarine, MobEffects.MOVEMENT_SPEED, 3000, 0);
        applyEffect(spaceMarine, MobEffects.REGENERATION, 400, 0);

        spaceMarine.heal(16.0F);
    }

    private static void applyEffect(LivingEntity entity, net.minecraft.world.effect.MobEffect effect, int duration, int amplifier) {
        entity.addEffect(new MobEffectInstance(
                effect,
                duration,
                amplifier,
                false,
                true,
                true
        ));
    }
}