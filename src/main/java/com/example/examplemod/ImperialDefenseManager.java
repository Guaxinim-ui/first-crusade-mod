package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class ImperialDefenseManager {
    private ImperialDefenseManager() {
    }

    /**
     * Calls every defender back to the Core.
     *
     * <h2>It no longer builds a ring to stand them on</h2>
     *
     * Rally used to compute sixteen fixed posts, lay a stone-brick floor under each and carve the
     * air above it, then pin a soldier there. That was the city builder's idea of a defence line —
     * and it meant a base wrote blocks into the world every time the player pressed a button.
     * A simplified base places nothing: the defenders are simply moved to the Core and left loose,
     * which is how they stand the rest of the time as well.
     */
    public static int rallyDefenders(ServerLevel serverLevel, ImperialCommandCoreBlockEntity commandCore) {
        BlockPos corePos = commandCore.getBlockPos();

        List<GuardsmanEntity> guardsmen = findAssignedGuardsmen(serverLevel, commandCore);
        List<SpaceMarineEntity> spaceMarines = findAssignedSpaceMarines(serverLevel, commandCore);

        int affected = 0;
        int index = 0;

        for (GuardsmanEntity guardsman : guardsmen) {
            guardsman.clearGuardPost();
            moveMobToPost(guardsman, rallySpot(serverLevel, corePos, index));

            affected++;
            index++;
        }

        for (SpaceMarineEntity spaceMarine : spaceMarines) {
            moveMobToPost(spaceMarine, rallySpot(serverLevel, corePos, index));

            affected++;
            index++;
        }

        return affected;
    }

    /**
     * A spot on a small ring around the Core, on whatever ground is already there.
     *
     * <p>Read off the heightmap rather than assumed: the point is to gather the garrison, not to
     * flatten the ground it gathers on.
     */
    private static BlockPos rallySpot(ServerLevel serverLevel, BlockPos corePos, int index) {
        double angle = (Math.PI * 2.0D / 8.0D) * (index % 8);
        int radius = 4 + (index / 8) * 3;

        BlockPos around = corePos.offset(
                (int) Math.round(Math.cos(angle) * radius), 0,
                (int) Math.round(Math.sin(angle) * radius));

        return serverLevel.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
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

    // findDefensePost / prepareDefensePost / clearIfNeeded went with the ring they built.
    // Nothing in a simplified base writes blocks after its founding pad.

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