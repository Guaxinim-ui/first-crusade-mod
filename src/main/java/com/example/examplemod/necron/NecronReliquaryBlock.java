package com.example.examplemod.necron;

import com.example.examplemod.planet.PlanetTravelManager;
import com.example.examplemod.planet.PlanetUnlockRequirement;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The thing in the middle of the ruin: a plinth with a Necron artefact still on it.
 *
 * <h2>Why this block exists instead of a chest</h2>
 *
 * The tomb world's unlock has always read "Recover a Necron artefact" and nothing in the mod ever
 * granted it — {@code TRIGGER_CAMPAIGN} was documented as "nothing fires this yet". This is the
 * thing that fires it. A chest with a loot table would have worked mechanically and would have said
 * "someone left supplies here"; a plinth that goes dark when you take what is on it says the right
 * thing, and it is what makes the ruin worth walking into.
 *
 * <h2>Taken by hand, and only once</h2>
 *
 * Right-click rather than break, so the artefact cannot be collected by an explosion or a machine,
 * and the block is replaced with its emptied state rather than removed — the ruin keeps its centre,
 * and a player returning to it can see they have already been here. That also makes the unlock
 * idempotent without storing anything: an empty plinth grants nothing because there is nothing on
 * it.
 */
public class NecronReliquaryBlock extends Block {

    public NecronReliquaryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack artefact = new ItemStack(FCNecrons.NECRON_ARTEFACT.get());

        if (!serverPlayer.getInventory().add(artefact)) {
            serverPlayer.drop(artefact, false);
        }

        // The unlock is granted here rather than by holding the item, so a player who loses the
        // artefact afterwards does not lose the world they already earned. Recovering it is the
        // achievement; carrying it around is not.
        PlanetTravelManager.grant(serverPlayer, PlanetUnlockRequirement.TRIGGER_NECRON_ARTEFACT);

        serverPlayer.sendSystemMessage(Component
                .translatable("msg.firstcrusade.necron.artefact_taken")
                .withStyle(ChatFormatting.DARK_GREEN));

        // The plinth goes dark. Deepslate rather than air: the ruin keeps its shape.
        level.setBlock(pos, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);

        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.get(), SoundSource.BLOCKS,
                0.9F, 0.7F);

        // The tomb notices. Scarabs that were dormant around the ruin now have a reason.
        level.getEntitiesOfClass(NecronScarabEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(24.0D))
                .forEach(scarab -> scarab.setTarget(serverPlayer));

        return InteractionResult.CONSUME;
    }
}
