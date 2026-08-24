package com.example.examplemod.campaign.force;

import javax.annotation.Nullable;

import com.example.examplemod.ImperialCommandCoreBlockEntity;
import com.example.examplemod.campaign.CampaignConfig;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignLog;
import com.example.examplemod.campaign.sector.SectorType;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.WarFaction;
import com.example.examplemod.entity.vehicle.ImperialBattleTankEntity;
import com.example.examplemod.registry.ModVehicleEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Armour, commissioned from the Crusade's own industry (§14-15).
 *
 * <h2>The seam this closes</h2>
 *
 * The Battle Tank existed and could only be obtained from a <b>spawn egg</b>. Meanwhile
 * {@link SectorType#VEHICLE_FACTORY} existed, sat in the Forge World's blueprint, and produced
 * plasteel that nothing spent. Two halves of a feature, each complete, with nothing between them.
 *
 * <h2>Holding the factory is the whole point</h2>
 *
 * The gate is not a resource total — it is <i>ownership of a place</i>. The Imperium can field armour
 * because it holds a vehicle factory somewhere in the Crusade, and if the Orks take that sector the
 * tanks stop, everywhere. That is the first thing in the mod where losing ground on one planet is
 * felt in your hands on another, which is what the whole campaign layer was built to make possible.
 *
 * <p>A <b>disputed</b> factory does not count. A plant with fighting in it is not shipping.
 */
public final class ArmourRequisition {

    private ArmourRequisition() {
    }

    /**
     * War Support for one tank.
     *
     * <p>Six times an ASSAULT order, which is the most expensive thing the War Table sells. A tank
     * should read as the largest commitment the city can make, not as another button to press.
     */
    private static final int WAR_SUPPORT_COST = 60;

    /** How close a tank has to be to count against this Core's allowance. */
    private static final int MUSTER_RADIUS = 64;

    /**
     * Tanks one city may keep fielded at once.
     *
     * <p>Two, and it is a cap rather than a cooldown on purpose: a cooldown lets a patient player
     * accumulate armour without limit, and an armoured column that never stops growing is the same
     * problem the Ork war parties already have a field cap for.
     */
    private static final int TANKS_PER_CITY = 2;

    /**
     * Tries to commission one Battle Tank at this Core.
     *
     * @return the reason it was refused, or null when a tank was built
     */
    @Nullable
    public static Component requisition(ServerPlayer player, ImperialCommandCoreBlockEntity core) {
        // Read from the block entity rather than taken as an argument: the Core's own position is
        // authoritative, and threading one through from the packet would be trusting the client for
        // a value the server already holds.
        BlockPos corePos = core.getBlockPos();

        if (!CampaignConfig.enabled()) {
            return Component.translatable("msg.firstcrusade.armour.campaign_off");
        }

        ServerLevel level = player.serverLevel();

        StrategicSector factory = workingFactory(CampaignData.get(level));

        if (factory == null) {
            return Component.translatable("msg.firstcrusade.armour.no_factory");
        }

        long fielded = level.getEntitiesOfClass(ImperialBattleTankEntity.class,
                new AABB(corePos).inflate(MUSTER_RADIUS),
                tank -> tank.isAlive()).size();

        if (fielded >= TANKS_PER_CITY) {
            return Component.translatable("msg.firstcrusade.armour.at_cap", TANKS_PER_CITY);
        }

        BlockPos spot = deliveryYard(level, corePos);

        if (spot == null) {
            return Component.translatable("msg.firstcrusade.armour.no_space");
        }

        // Checked and debited in one call, the same way the War Table's orders pay: reading the
        // value and subtracting it in separate steps leaves a window where one reserve funds two
        // tanks.
        if (!core.spendWarSupport(WAR_SUPPORT_COST)) {
            return Component.translatable("msg.firstcrusade.armour.no_support", WAR_SUPPORT_COST);
        }

        ImperialBattleTankEntity tank = ModVehicleEntities.IMPERIAL_BATTLE_TANK.get().create(level);

        if (tank == null) {
            return Component.translatable("msg.firstcrusade.armour.no_space");
        }

        tank.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        tank.setPersistenceRequired();
        level.addFreshEntity(tank);

        CampaignLog.war("armour requisitioned at {} by {} (factory {})",
                corePos, player.getGameProfile().getName(), factory.id());

        player.displayClientMessage(Component.translatable("msg.firstcrusade.armour.built",
                factory.type().displayName()), true);

        return null;
    }

    /**
     * A vehicle factory the Imperium holds and is not fighting over, anywhere in the Crusade.
     *
     * <p>Any front, not this one: the Forge World builds the tanks and ships them, which is exactly
     * what its lane in the supply network already says it does.
     */
    @Nullable
    private static StrategicSector workingFactory(CampaignData campaign) {
        for (StrategicSector sector : campaign.allSectors()) {
            if (sector.type() != SectorType.VEHICLE_FACTORY) {
                continue;
            }

            if (sector.owner() == WarFaction.IMPERIUM && !sector.isDisputed()) {
                return sector;
            }
        }

        return null;
    }

    /** Open ground beside the Core to set a tank down on. */
    @Nullable
    private static BlockPos deliveryYard(ServerLevel level, BlockPos corePos) {
        for (int radius = 4; radius <= 10; radius += 2) {
            for (int angle = 0; angle < 360; angle += 30) {
                int x = corePos.getX() + (int) Math.round(Math.cos(Math.toRadians(angle)) * radius);
                int z = corePos.getZ() + (int) Math.round(Math.sin(Math.toRadians(angle)) * radius);

                BlockPos ground = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));

                // A tank is two blocks tall and wide; a spot that only fits a soldier drops it into
                // a wall.
                if (level.noCollision(new AABB(ground).inflate(1.0D, 0.0D, 1.0D).expandTowards(0.0D, 2.0D, 0.0D))) {
                    return ground;
                }
            }
        }

        return null;
    }
}
