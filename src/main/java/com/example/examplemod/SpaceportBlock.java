package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.examplemod.planet.FCPlanets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Spaceport: the door between an ordinary Minecraft world and the Crusade.
 *
 * <p><b>Right-click</b> travels. From the overworld it launches to the currently selected planet;
 * from a planet it brings the traveller home. <b>Sneak + right-click</b> picks a different
 * destination, walking {@link FCPlanets#ALL} in travel order and naming the choice.
 *
 * <p>The selection is deliberately per-player rather than per-block. A Spaceport is a piece of
 * Imperial infrastructure a player may well build a dozen of; making each one remember its own
 * heading would mean a block entity on every pad and a player who has to walk back to the right one
 * to change their mind.
 *
 * <p>Every landing builds a small pad with a return Spaceport, so a round trip never strands or
 * drops anyone — the planets are generated worlds with no guarantee of friendly ground.
 */
public class SpaceportBlock extends Block {
    public SpaceportBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                cycleDestination(serverPlayer);
            } else {
                travel(serverPlayer);
            }
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Where each player is currently headed. Not persisted on purpose: a heading is a intention for
     * the next few seconds, and a player who reconnects and finds their ship aimed somewhere they
     * chose three sessions ago has been surprised, not helped. It falls back to
     * {@link FCPlanets#DEFAULT}.
     */
    private static final Map<UUID, ResourceKey<Level>> HEADING = new HashMap<>();

    private static ResourceKey<Level> headingOf(ServerPlayer player) {
        return HEADING.getOrDefault(player.getUUID(), FCPlanets.DEFAULT);
    }

    private void cycleDestination(ServerPlayer player) {
        ResourceKey<Level> next = FCPlanets.next(headingOf(player));
        HEADING.put(player.getUUID(), next);
        player.displayClientMessage(Component.translatable(
                "msg.firstcrusade.spaceport.heading",
                Component.translatable(FCPlanets.nameKey(next))), true);
    }

    private void travel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // Leaving a planet always means going home. Only the outbound leg has a choice to make,
        // which is why the heading is only consulted here.
        boolean onPlanet = FCPlanets.isCrusadeWorld(player.level().dimension());
        ResourceKey<Level> destinationKey = onPlanet ? Level.OVERWORLD : headingOf(player);
        ServerLevel destination = server.getLevel(destinationKey);

        if (destination == null) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.spaceport.no_dest"), true);
            return;
        }

        int x = player.blockPosition().getX();
        int z = player.blockPosition().getZ();
        BlockPos landing = findDryLanding(destination, x, z);

        buildLandingPad(destination, landing);

        // The first time anyone reaches a planet, populate it with settlements around the landing.
        if (FCPlanets.isCrusadeWorld(destinationKey)) {
            WorldSettlementSeeder.seedPlanet(destination, landing);
        }

        player.teleportTo(destination, x + 0.5D, landing.getY(), z + 0.5D, player.getYRot(), player.getXRot());

        Component where = onPlanet
                ? Component.translatable("msg.firstcrusade.spaceport.home")
                : Component.translatable(FCPlanets.nameKey(destinationKey));
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.spaceport.arrived_at", where), true);
    }

    // Finds a dry surface spot near (x,z) so the pad and Spaceport land on solid ground, not under a
    // lava sea. Spirals outward; falls back to the raw surface if nowhere dry is found nearby.
    private BlockPos findDryLanding(ServerLevel level, int x, int z) {
        for (int r = 0; r <= 48; r += 4) {
            int steps = r == 0 ? 1 : 8;
            for (int a = 0; a < steps; a++) {
                double ang = 2.0D * Math.PI * a / steps;
                int px = x + (int) Math.round(Math.cos(ang) * r);
                int pz = z + (int) Math.round(Math.sin(ang) * r);
                BlockPos surface = WorldGenPlacement.groundPlacement(level, px, pz);

                if (isDryLanding(level, surface)) {
                    return surface;
                }
            }
        }
        return WorldGenPlacement.groundPlacement(level, x, z);
    }

    private boolean isDryLanding(ServerLevel level, BlockPos surface) {
        if (!level.getFluidState(surface).isEmpty() || !level.getBlockState(surface).isAir()) {
            return false;
        }
        BlockPos ground = surface.below();
        return level.getBlockState(ground).getFluidState().isEmpty() && !level.getBlockState(ground).isAir();
    }

    private static final int PAD_RADIUS = 3;

    // A bright, open landing pad: clears any trees/terrain in the way, lays a stone platform with
    // lit corners and a return Spaceport, so the traveller never lands stranded in a dark pocket.
    private void buildLandingPad(ServerLevel level, BlockPos landing) {
        // Strip vegetation and give real headroom so you don't arrive boxed in.
        WorldGenPlacement.clearVegetation(level, landing, PAD_RADIUS + 1, 6);

        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState edge = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

        for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx++) {
            for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz++) {
                boolean rim = Math.abs(dx) == PAD_RADIUS || Math.abs(dz) == PAD_RADIUS;
                level.setBlock(landing.offset(dx, -1, dz), rim ? edge : floor, 3);

                // Clear standing room above the pad.
                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlock(landing.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // Lanterns on the four corners.
        int[][] corners = {{-PAD_RADIUS, -PAD_RADIUS}, {-PAD_RADIUS, PAD_RADIUS}, {PAD_RADIUS, -PAD_RADIUS}, {PAD_RADIUS, PAD_RADIUS}};
        for (int[] c : corners) {
            level.setBlock(landing.offset(c[0], 0, c[1]), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(landing.offset(c[0], 1, c[1]), Blocks.LANTERN.defaultBlockState(), 3);
        }

        // Return Spaceport beside the centre.
        BlockPos returnPort = landing.offset(2, 0, 0);
        if (!level.getBlockState(returnPort).is(FCRegistry.SPACEPORT.get())) {
            level.setBlock(returnPort, FCRegistry.SPACEPORT.get().defaultBlockState(), 3);
        }
    }
}
