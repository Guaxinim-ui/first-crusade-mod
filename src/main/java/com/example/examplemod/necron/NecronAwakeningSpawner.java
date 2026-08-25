package com.example.examplemod.necron;

import java.util.List;

import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.planet.PlanetWarState;
import com.example.examplemod.planet.FCPlanets;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What the tomb sends, and when.
 *
 * <h2>The stages were always a spawn table</h2>
 *
 * {@code PlanetWarState.NecronStage} has read SILENT -> SCARABS -> WARRIORS -> TOMB_DEFENCES ->
 * OVERLORD since the campaign layer was written, and until now crossing one only wrote a log line.
 * The names are not decoration: each one says what comes out of the ground at that point. This class
 * is the table those names were always describing.
 *
 * <h2>Near the player, capped, and never in daylight ambush range</h2>
 *
 * Spawns are placed in a ring 20-32 blocks out — far enough that things do not appear in someone's
 * face, close enough that they are found rather than wandered past. The cap counts what is already
 * alive nearby rather than what has ever been spawned, so a player who fights their way clear gets
 * a pause, and one who runs does not get followed by an ever-growing tail.
 */
public final class NecronAwakeningSpawner {

    private NecronAwakeningSpawner() {
    }

    /** Ticks between attempts. 600 = 30s, matching the campaign's own unhurried cadence. */
    private static final int INTERVAL = 600;

    /** Living Necrons within {@link #RADIUS} above which nothing more is sent. */
    private static final int NEARBY_CAP = 14;

    private static final int RADIUS = 48;
    private static final int RING_MIN = 20;
    private static final int RING_MAX = 32;

    /**
     * One attempt per player on the tomb world.
     *
     * <p>Driven from the player tick rather than the strategic pass because it needs somewhere to
     * put things, and "somewhere" means near a player. The strategic pass runs for planets with
     * nobody on them, where spawning would be pure waste.
     */
    public static void tick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        if (!level.dimension().equals(FCPlanets.NECRON_TOMB_WORLD)) {
            return;
        }

        if (player.tickCount % INTERVAL != 0 || player.isCreative() || player.isSpectator()) {
            return;
        }

        PlanetWarState state = CampaignData.get(level)
                .existingState(FCPlanets.NECRON_TOMB_WORLD.location());

        if (state == null) {
            return;
        }

        PlanetWarState.NecronStage stage = state.necronStage();

        if (stage == PlanetWarState.NecronStage.SILENT) {
            return;
        }

        if (countNearby(level, player) >= NEARBY_CAP) {
            return;
        }

        sendFor(level, player, stage);
    }

    /**
     * The table itself.
     *
     * <p>Each stage adds to what came before rather than replacing it — a tomb at OVERLORD still
     * has scarabs boiling around the feet of its Warriors, which is the picture the reference plate
     * shows and would be lost if the stages swapped one unit for another.
     */
    private static void sendFor(ServerLevel level, ServerPlayer player, PlanetWarState.NecronStage stage) {
        switch (stage) {
            case SCARABS -> spawn(level, player, FCNecrons.NECRON_SCARAB.get(), 3 + level.random.nextInt(3));

            case WARRIORS -> {
                spawn(level, player, FCNecrons.NECRON_SCARAB.get(), 2);
                spawn(level, player, FCNecrons.NECRON_WARRIOR.get(), 2);
            }

            case TOMB_DEFENCES -> {
                spawn(level, player, FCNecrons.NECRON_SCARAB.get(), 3);
                spawn(level, player, FCNecrons.NECRON_WARRIOR.get(), 3 + level.random.nextInt(2));
            }

            // The top of the clock sends the tomb's whole garrison — and NOT the Overlord.
            //
            // He used to be spawned here, in a ring around whoever happened to be standing on the
            // planet, guarded by a 128-block check for another one. That was the best available
            // answer while the tomb was only a number: there was nowhere for him to be.
            //
            // Now there is. `site_necron_tomb` builds a shaft and a sealed chamber and puts him on
            // the throne with a Warrior guard, so the Overlord is a PLACE THE PLAYER GOES TO rather
            // than a thing that walks up behind them. Keeping both would have meant two Overlords on
            // one planet the moment the chamber sat in an unloaded chunk, because the old check can
            // only see loaded entities.
            case OVERLORD -> {
                spawn(level, player, FCNecrons.NECRON_SCARAB.get(), 3);
                spawn(level, player, FCNecrons.NECRON_WARRIOR.get(), 5);
            }

            default -> {
            }
        }
    }

    private static int countNearby(ServerLevel level, ServerPlayer player) {
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(RADIUS),
                mob -> mob.isAlive() && NecronTargets.isNecron(mob));

        return nearby.size();
    }

    private static void spawn(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos at = ringPos(level, player);

            if (at == null) {
                continue;
            }

            Mob mob = type.create(level);

            if (mob == null) {
                continue;
            }

            mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);

            // Persistent: these are an event, not ambient spawns, and a Warrior that despawns while
            // the player is fighting it would read as it having teleported away.
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
    }

    private static BlockPos ringPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = RING_MIN + level.random.nextDouble() * (RING_MAX - RING_MIN);

            int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);

            // Only where the world is actually loaded — otherwise this forces chunk loads in a ring
            // around a walking player, which is the most expensive way to spawn anything.
            if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight() + 1, z))) {
                continue;
            }

            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));

            if (level.noCollision(new net.minecraft.world.phys.AABB(ground).inflate(0.4D, 0.0D, 0.4D)
                    .expandTowards(0.0D, 2.0D, 0.0D))) {
                return ground;
            }
        }

        return null;
    }
}
