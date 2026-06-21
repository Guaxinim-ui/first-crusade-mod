package com.example.examplemod;

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
 * The Spaceport: right-click to travel between worlds. From the home world it launches the traveller
 * to the planet {@link ExampleMod#PLANET_SECUNDUS}; from the planet it brings them home. Each landing
 * builds a small pad with a return Spaceport, so a round trip never strands or drops anyone.
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
            travel(serverPlayer);
        }

        return InteractionResult.CONSUME;
    }

    private void travel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ResourceKey<Level> destinationKey =
                player.level().dimension() == ExampleMod.PLANET_SECUNDUS ? Level.OVERWORLD : ExampleMod.PLANET_SECUNDUS;
        ServerLevel destination = server.getLevel(destinationKey);

        if (destination == null) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.spaceport.no_dest"), true);
            return;
        }

        int x = player.blockPosition().getX();
        int z = player.blockPosition().getZ();
        BlockPos landing = WorldGenPlacement.groundPlacement(destination, x, z);

        buildLandingPad(destination, landing);

        // The first time anyone reaches the planet, populate it with settlements around the landing.
        if (destinationKey == ExampleMod.PLANET_SECUNDUS) {
            WorldSettlementSeeder.seedPlanet(destination, landing);
        }

        player.teleportTo(destination, x + 0.5D, landing.getY(), z + 0.5D, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("msg.firstcrusade.spaceport.arrived"), true);
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
        if (!level.getBlockState(returnPort).is(ExampleMod.SPACEPORT.get())) {
            level.setBlock(returnPort, ExampleMod.SPACEPORT.get().defaultBlockState(), 3);
        }
    }
}
