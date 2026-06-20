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

        player.teleportTo(destination, x + 0.5D, landing.getY(), z + 0.5D, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("msg.firstcrusade.spaceport.arrived"), true);
    }

    // A 3x3 stone pad with clear headroom and a return Spaceport on its corner.
    private void buildLandingPad(ServerLevel level, BlockPos landing) {
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(landing.offset(dx, -1, dz), floor, 3);
                level.setBlock(landing.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(landing.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }

        BlockPos returnPort = landing.offset(1, 0, 1);
        if (!level.getBlockState(returnPort).is(ExampleMod.SPACEPORT.get())) {
            level.setBlock(returnPort, ExampleMod.SPACEPORT.get().defaultBlockState(), 3);
        }
    }
}
