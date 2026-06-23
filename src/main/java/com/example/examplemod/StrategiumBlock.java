package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The "Mesa de Estratégia" (Strategium / War Table): the bench where the Imperium's Ages are
 * researched. Right-click with Iron Ingots or Scrap Metal to stock it; right-click empty-handed to
 * open the research interface. See {@link StrategiumBlockEntity}.
 */
public class StrategiumBlock extends BaseEntityBlock {
    public StrategiumBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StrategiumBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!(blockEntity instanceof StrategiumBlockEntity bench)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);

        if (held.is(Items.IRON_INGOT)) {
            bench.depositIron(player, held);
            return InteractionResult.SUCCESS;
        }

        if (held.is(ExampleMod.SCRAP_METAL.get())) {
            bench.depositScrap(player, held);
            return InteractionResult.SUCCESS;
        }

        bench.openInterface(player);
        return InteractionResult.SUCCESS;
    }
}
