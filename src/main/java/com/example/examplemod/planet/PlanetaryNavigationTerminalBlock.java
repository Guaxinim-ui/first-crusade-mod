package com.example.examplemod.planet;

import com.example.examplemod.FCRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * Planetary Navigation Terminal — the console that opens Imperial Planetary Navigation.
 *
 * <h2>Separate from the Spaceport, on purpose</h2>
 *
 * The {@link com.example.examplemod.SpaceportBlock} is the launch pad and still works exactly as it
 * did: right-click travels, sneak-click cycles. This is the console beside it, and splitting the two
 * is what lets a full destination browser exist without taking away the one-click launch that
 * players already have muscle memory for. It also gives the fiction its shape — the pad is where you
 * stand, the terminal is where you choose.
 *
 * <h2>The server decides everything</h2>
 *
 * A right-click sends nothing to the client on its own. The server checks the terminal is usable and
 * only then opens the menu, writing the destination list into it ({@link PlanetNavigationMenu}). If
 * the pad is broken or held by the enemy, the player gets the reason and no screen.
 */
public class PlanetaryNavigationTerminalBlock extends HorizontalDirectionalBlock {

    /** A console: waist-high, so it reads as something you stand at rather than a full block. */
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 13.0D, 15.0D);

    public PlanetaryNavigationTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        PlanetTravelManager.Blocker blocker = PlanetTravelManager.checkTerminal(serverPlayer, pos);

        // A terminal that cannot be used says why and does not open. Opening a screen that only
        // contains an error is worse than the message: it looks like the feature is broken rather
        // than like the pad needs work.
        if (blocker != PlanetTravelManager.Blocker.NONE
                && blocker != PlanetTravelManager.Blocker.IN_TRANSIT) {
            serverPlayer.displayClientMessage(blocker.message(), true);
            serverPlayer.playNotifySound(PlanetSounds.LOCKED.get(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.CONSUME;
        }

        if (blocker == PlanetTravelManager.Blocker.IN_TRANSIT) {
            serverPlayer.displayClientMessage(blocker.message(), true);
            return InteractionResult.CONSUME;
        }

        serverPlayer.playNotifySound(PlanetSounds.TERMINAL_OPEN.get(),
                SoundSource.BLOCKS, 0.7F, 1.0F);

        NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("screen.firstcrusade.planet_navigation");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player owner) {
                PlanetUnlockData data = PlanetUnlockData.get(serverPlayer.serverLevel());
                PlanetUnlockData.ReturnPoint point = data.returnPoint(serverPlayer.getUUID());

                return new PlanetNavigationMenu(containerId, inventory, pos,
                        PlanetNavigationMenu.buildEntries(serverPlayer),
                        PlanetTravelManager.checkTerminal(serverPlayer, pos),
                        point != null,
                        point == null ? null : PlanetRegistry.get(point.planetId()).orElse(null),
                        FCPlanets.isCrusadeWorld(serverPlayer.level().dimension()));
            }
        }, buffer -> PlanetNavigationMenu.writeSnapshot(serverPlayer, pos, buffer));

        return InteractionResult.CONSUME;
    }
}
