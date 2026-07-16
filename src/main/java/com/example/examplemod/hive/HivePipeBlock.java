package com.example.examplemod.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Six-way connectable industrial pipe (used by large_hive_pipe and pipe_junction).
 *
 * Deliberately dumb and cheap: pure blockstate connections (NORTH/SOUTH/EAST/WEST/UP/DOWN
 * booleans, same pattern as the vanilla chorus plant), no BlockEntity, no ticking, no fluid
 * logic. Vanilla {@link PipeBlock} already provides the cached voxel shapes per connection
 * combination, so thousands of these can decorate the Hive at zero runtime cost.
 *
 * Connects to: any other HivePipeBlock, plus every block in the firstcrusade:pipe_connectable
 * block tag (valves, machine casings, vents, future machines).
 */
public class HivePipeBlock extends PipeBlock {

    public HivePipeBlock(float apothem, BlockBehaviour.Properties properties) {
        super(apothem, properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, Boolean.FALSE)
                .setValue(EAST, Boolean.FALSE)
                .setValue(SOUTH, Boolean.FALSE)
                .setValue(WEST, Boolean.FALSE)
                .setValue(UP, Boolean.FALSE)
                .setValue(DOWN, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    protected boolean connectsTo(BlockState neighborState) {
        return neighborState.getBlock() instanceof HivePipeBlock
                || neighborState.is(HiveTags.PIPE_CONNECTABLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(neighbor));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(neighborState));
    }
}
