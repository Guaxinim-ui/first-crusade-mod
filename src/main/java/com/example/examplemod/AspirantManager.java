package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The Astartes aspirant pipeline. Only chosen children (see {@link ImperialPopulationManager}) grow
 * into aspirants; as adults they receive the gene-seed organ implants one stage at a time at the
 * Core's Apothecarion (each implant costs Emperor's Gene Seed). Once fully implanted the aspirant is
 * made a {@link SpaceMarineEntity} Neophyte, who must still be blooded in battle before ascending to
 * a full Battle-Brother. Ticked from the Command Core (every ~200 ticks).
 */
public final class AspirantManager {
    public static final int IMPLANT_STAGES = 3;             // the key gene-seed organs
    private static final int IMPLANT_INTERVAL_TICKS = 1200; // ~1 min of surgery between organs
    private static final int GENE_SEED_PER_IMPLANT = 1;
    private static final int SEARCH_RADIUS = 96;
    private static final int TICK_STRIDE = 200;             // how often the Core calls this

    private AspirantManager() {
    }

    public static void tickAspirants(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core) {
        BlockPos corePos = core.getBlockPos();

        List<ImperialCitizenEntity> aspirants = serverLevel.getEntitiesOfClass(
                ImperialCitizenEntity.class,
                searchBox(corePos),
                citizen -> citizen.isAlive()
                        && citizen.isAspirant()
                        && !citizen.isChild()
                        && citizen.isAssignedToCommandCore(corePos));

        for (ImperialCitizenEntity aspirant : aspirants) {
            if (aspirant.getImplantStage() >= IMPLANT_STAGES) {
                makeNeophyte(serverLevel, core, aspirant);
                continue;
            }

            if (aspirant.getImplantCooldown() > 0) {
                aspirant.setImplantCooldown(aspirant.getImplantCooldown() - TICK_STRIDE);
                continue;
            }

            // Each organ needs a unit of harvested gene-seed; without it, the aspirant waits.
            if (core.getEmperorGeneSeed() < GENE_SEED_PER_IMPLANT) {
                continue;
            }

            core.consumeEmperorGeneSeed(GENE_SEED_PER_IMPLANT);
            aspirant.setImplantStage(aspirant.getImplantStage() + 1);
            aspirant.setImplantCooldown(IMPLANT_INTERVAL_TICKS);

            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    corePos,
                    Component.translatable("msg.firstcrusade.bcast.implant", aspirant.getImplantStage(), IMPLANT_STAGES));
        }
    }

    // A fully implanted aspirant is made a Neophyte and must now prove itself in battle.
    private static void makeNeophyte(ServerLevel serverLevel, ImperialCommandCoreBlockEntity core, ImperialCitizenEntity aspirant) {
        SpaceMarineEntity marine = FCRegistry.SPACE_MARINE.get().create(serverLevel);
        if (marine == null) {
            return;
        }

        marine.moveTo(aspirant.getX(), aspirant.getY(), aspirant.getZ(), aspirant.getYRot(), aspirant.getXRot());
        marine.assignToCommandCore(core.getBlockPos());
        marine.beginAsNeophyte();

        aspirant.discard();
        serverLevel.addFreshEntity(marine);
        core.registerAscendedMarine();

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                core.getBlockPos(),
                Component.translatable("msg.firstcrusade.bcast.neophyte_made"));
    }

    private static AABB searchBox(BlockPos corePos) {
        return new AABB(
                corePos.getX() - SEARCH_RADIUS, corePos.getY() - 32, corePos.getZ() - SEARCH_RADIUS,
                corePos.getX() + SEARCH_RADIUS, corePos.getY() + 64, corePos.getZ() + SEARCH_RADIUS);
    }
}
