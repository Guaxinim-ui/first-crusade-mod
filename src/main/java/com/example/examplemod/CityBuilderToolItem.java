package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Ferramenta de Construção — the MineColonies-style build tool that "comes out of" an Imperial
 * Command Core. It remembers which Core issued it and which structure is selected:
 *  - <b>right-click a block</b>: place the selected structure there (the Core pays and validates);
 *  - <b>sneak + right-click</b>: cycle to the next structure.
 *
 * A translucent ghost of the footprint is drawn client-side (see BuilderToolGhostRenderer). All
 * placement logic lives on the Core ({@link ImperialCommandCoreBlockEntity#placeStructureWithTool}),
 * so the tool is a thin controller; the NBT it carries is also read by the ghost renderer.
 */
public class CityBuilderToolItem extends Item {
    private static final String TAG_CORE = "CorePos";
    private static final String TAG_STRUCTURE = "StructureIndex";

    public CityBuilderToolItem(Properties properties) {
        super(properties);
    }

    // ---- NBT accessors (shared with the ghost renderer) ----

    public static void bindToCore(ItemStack stack, BlockPos corePos) {
        stack.getOrCreateTag().putLong(TAG_CORE, corePos.asLong());
    }

    @Nullable
    public static BlockPos getBoundCore(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_CORE)) {
            return null;
        }
        return BlockPos.of(tag.getLong(TAG_CORE));
    }

    public static CityStructureType getSelectedStructure(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return CityStructureType.byIndex(tag == null ? 0 : tag.getInt(TAG_STRUCTURE));
    }

    private static void cycleStructure(ItemStack stack, Player player) {
        CityStructureType next = getSelectedStructure(stack).next();
        stack.getOrCreateTag().putInt(TAG_STRUCTURE, next.ordinal());
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.builder.selected",
                        Component.translatable(next.getNameKey())), true);
    }

    // ---- interactions ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();

        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) {
            // Client swings; the server run does the real work.
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            cycleStructure(stack, player);
            return InteractionResult.CONSUME;
        }

        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        placeSelected(stack, player, (ServerLevel) level, target);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Right-clicking air while sneaking still cycles, so you can change structure anywhere.
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                cycleStructure(stack, player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    private void placeSelected(ItemStack stack, Player player, ServerLevel level, BlockPos target) {
        BlockPos corePos = getBoundCore(stack);

        if (corePos == null) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.no_core"), true);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(corePos);

        if (!(blockEntity instanceof ImperialCommandCoreBlockEntity core)) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.core_gone"), true);
            return;
        }

        core.placeStructureWithTool(player, getSelectedStructure(stack), target);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CityStructureType selected = getSelectedStructure(stack);

        tooltip.add(Component.translatable("msg.firstcrusade.builder.tooltip.selected",
                Component.translatable(selected.getNameKey())).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("msg.firstcrusade.builder.tooltip.cost",
                selected.getIronCost(), selected.getScrapCost(), selected.getCoalCost())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("msg.firstcrusade.builder.tooltip.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
