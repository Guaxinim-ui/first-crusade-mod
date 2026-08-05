package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.examplemod.planet.FCPlanets;
import com.example.examplemod.planet.PlanetLanding;

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
        BlockPos landing = PlanetLanding.findDryLanding(destination, x, z);

        PlanetLanding.buildLandingPad(destination, landing);

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

    // O pouso (procurar chao seco, limpar, montar a plataforma e o porto de retorno) vive em
    // PlanetLanding, porque o terminal de navegacao precisa exatamente do mesmo comportamento.
    // Manter uma copia aqui significaria duas versoes que divergem ate uma delas largar alguem
    // dentro de um lago de lava.
}
