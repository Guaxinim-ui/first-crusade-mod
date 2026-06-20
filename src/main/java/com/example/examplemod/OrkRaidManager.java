package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

public class OrkRaidManager {
    private static final String RAID_MOB_TAG = "FirstCrusadeRaidMob";
    private static final String RAID_CORE_POS_TAG = "FirstCrusadeRaidCorePos";

    private OrkRaidManager() {
    }

    public static void spawnRaid(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, boolean forced, int raidCount) {
        int cityLevel = commandCore.getCityLevel();
        int boyCount = getRaidBoyCount(cityLevel, raidCount);
        int nobCount = getRaidNobCount(cityLevel, raidCount);

        for (int i = 0; i < boyCount; i++) {
            Mob orkBoy = ExampleMod.ORK_BOY.get().create(serverLevel);

            if (orkBoy != null) {
                spawnRaidMob(serverLevel, commandCore, orkBoy, cityLevel, i);
            }
        }

        for (int i = 0; i < nobCount; i++) {
            Mob orkNob = ExampleMod.ORK_NOB.get().create(serverLevel);

            if (orkNob != null) {
                spawnRaidMob(serverLevel, commandCore, orkNob, cityLevel, boyCount + i);
            }
        }

        if (forced) {
            notifyNearbyPlayers(serverLevel, commandCore.getBlockPos(), Component.translatable("msg.firstcrusade.bcast.raid_forced"));
        } else {
            notifyNearbyPlayers(serverLevel, commandCore.getBlockPos(), Component.translatable("msg.firstcrusade.bcast.raid_incoming"));
        }

        notifyNearbyPlayers(
                serverLevel,
                commandCore.getBlockPos(),
                Component.translatable("msg.firstcrusade.bcast.raid_strength", boyCount, nobCount)
        );
    }

    private static void spawnRaidMob(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, Mob mob, int cityLevel, int index) {
        BlockPos corePos = commandCore.getBlockPos();
        BlockPos spawnPos = findRaidSpawnPosition(serverLevel, corePos, cityLevel, index);

        markAsRaidMob(mob, corePos);

        mob.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        LivingEntity target = findBestRaidTarget(serverLevel, corePos, spawnPos);

        if (target != null) {
            mob.setTarget(target);
            mob.getNavigation().moveTo(target, getRaidMoveSpeed(cityLevel));
        } else {
            mob.getNavigation().moveTo(
                    corePos.getX() + 0.5D,
                    corePos.getY(),
                    corePos.getZ() + 0.5D,
                    getRaidMoveSpeed(cityLevel)
            );
        }

        mob.setPersistenceRequired();
        serverLevel.addFreshEntity(mob);
    }

    public static void updateRaiders(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();
        int cityLevel = commandCore.getCityLevel();
        int radius = 160;

        AABB searchBox = new AABB(
                corePos.getX() - radius,
                corePos.getY() - 64,
                corePos.getZ() - radius,
                corePos.getX() + radius,
                corePos.getY() + 96,
                corePos.getZ() + radius
        );

        List<Mob> raiders = serverLevel.getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> mob.isAlive()
                        && isOrkRaidMob(mob)
                        && isRaidMobForCore(mob, corePos)
        );

        for (Mob raider : raiders) {
            LivingEntity currentTarget = raider.getTarget();

            boolean needsNewTarget = currentTarget == null
                    || !currentTarget.isAlive()
                    || raider.tickCount % 100 == 0;

            if (needsNewTarget) {
                LivingEntity newTarget = findBestRaidTarget(serverLevel, corePos, raider.blockPosition());

                if (newTarget != null) {
                    raider.setTarget(newTarget);
                    raider.getNavigation().moveTo(newTarget, getRaidMoveSpeed(cityLevel));
                    continue;
                }
            }

            if (raider.getTarget() == null) {
                raider.getNavigation().moveTo(
                        corePos.getX() + 0.5D,
                        corePos.getY(),
                        corePos.getZ() + 0.5D,
                        getRaidMoveSpeed(cityLevel)
                );
            }
        }
    }

    public static int countRaidersInsideRadius(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore, int radius) {
        BlockPos corePos = commandCore.getBlockPos();

        AABB searchBox = new AABB(
                corePos.getX() - radius,
                corePos.getY() - 64,
                corePos.getZ() - radius,
                corePos.getX() + radius,
                corePos.getY() + 96,
                corePos.getZ() + radius
        );

        List<Mob> raiders = serverLevel.getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> mob.isAlive()
                        && isOrkRaidMob(mob)
                        && isRaidMobForCore(mob, corePos)
        );

        return raiders.size();
    }

    private static LivingEntity findBestRaidTarget(ServerLevel serverLevel, BlockPos corePos, BlockPos searchOrigin) {
        AABB searchBox = new AABB(
                corePos.getX() - 96,
                corePos.getY() - 64,
                corePos.getZ() - 96,
                corePos.getX() + 96,
                corePos.getY() + 96,
                corePos.getZ() + 96
        );

        List<SpaceMarineEntity> spaceMarines = serverLevel.getEntitiesOfClass(
                SpaceMarineEntity.class,
                searchBox,
                spaceMarine -> spaceMarine.isAlive() && spaceMarine.isAssignedToCommandCore(corePos)
        );

        if (!spaceMarines.isEmpty()) {
            return spaceMarines.stream()
                    .min(Comparator.comparingDouble(spaceMarine -> spaceMarine.distanceToSqr(
                            searchOrigin.getX() + 0.5D,
                            searchOrigin.getY(),
                            searchOrigin.getZ() + 0.5D
                    )))
                    .orElse(null);
        }

        List<GuardsmanEntity> guardsmen = serverLevel.getEntitiesOfClass(
                GuardsmanEntity.class,
                searchBox,
                guardsman -> guardsman.isAlive() && guardsman.isAssignedToCommandCore(corePos)
        );

        if (!guardsmen.isEmpty()) {
            return guardsmen.stream()
                    .min(Comparator.comparingDouble(guardsman -> guardsman.distanceToSqr(
                            searchOrigin.getX() + 0.5D,
                            searchOrigin.getY(),
                            searchOrigin.getZ() + 0.5D
                    )))
                    .orElse(null);
        }

        List<Player> players = serverLevel.getEntitiesOfClass(
                Player.class,
                searchBox,
                player -> player.isAlive() && !player.isSpectator()
        );

        if (!players.isEmpty()) {
            return players.stream()
                    .min(Comparator.comparingDouble(player -> player.distanceToSqr(
                            searchOrigin.getX() + 0.5D,
                            searchOrigin.getY(),
                            searchOrigin.getZ() + 0.5D
                    )))
                    .orElse(null);
        }

        return null;
    }

    private static void markAsRaidMob(Mob mob, BlockPos corePos) {
        CompoundTag persistentData = mob.getPersistentData();

        persistentData.putBoolean(RAID_MOB_TAG, true);
        persistentData.putLong(RAID_CORE_POS_TAG, corePos.asLong());
    }

    private static boolean isOrkRaidMob(Mob mob) {
        if (mob.getType() != ExampleMod.ORK_BOY.get() && mob.getType() != ExampleMod.ORK_NOB.get()) {
            return false;
        }

        return mob.getPersistentData().getBoolean(RAID_MOB_TAG);
    }

    private static boolean isRaidMobForCore(Mob mob, BlockPos corePos) {
        return mob.getPersistentData().getLong(RAID_CORE_POS_TAG) == corePos.asLong();
    }

    private static BlockPos findRaidSpawnPosition(ServerLevel serverLevel, BlockPos corePos, int cityLevel, int index) {
        int radius = getRaidSpawnRadius(cityLevel);

        double angle = serverLevel.random.nextDouble() * Math.PI * 2.0D;
        angle += index * 0.65D;

        int x = corePos.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = corePos.getZ() + (int) Math.round(Math.sin(angle) * radius);

        BlockPos basePos = new BlockPos(x, corePos.getY(), z);

        return serverLevel.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                basePos
        );
    }

    public static void notifyNearbyPlayers(ServerLevel serverLevel, BlockPos corePos, String message) {
        notifyNearbyPlayers(serverLevel, corePos, Component.literal(message));
    }

    public static void notifyNearbyPlayers(ServerLevel serverLevel, BlockPos corePos, Component message) {
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

    private static int getRaidBoyCount(int cityLevel, int raidCount) {
        int baseAmount = switch (cityLevel) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 8;
            case 4 -> 12;
            case 5 -> 18;
            default -> 3;
        };

        return baseAmount + Math.min(raidCount, 10);
    }

    private static int getRaidNobCount(int cityLevel, int raidCount) {
        int baseAmount = switch (cityLevel) {
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 1;
            case 4 -> 2;
            case 5 -> 3;
            default -> 0;
        };

        return baseAmount + Math.min(raidCount / 3, 3);
    }

    private static int getRaidSpawnRadius(int cityLevel) {
        return switch (cityLevel) {
            case 1 -> 28;
            case 2 -> 36;
            case 3 -> 48;
            case 4 -> 64;
            case 5 -> 80;
            default -> 28;
        };
    }

    private static double getRaidMoveSpeed(int cityLevel) {
        return switch (cityLevel) {
            case 1 -> 1.0D;
            case 2 -> 1.05D;
            case 3 -> 1.1D;
            case 4 -> 1.15D;
            case 5 -> 1.2D;
            default -> 1.0D;
        };
    }
}