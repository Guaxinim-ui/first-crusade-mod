package com.example.examplemod.hive;

import javax.annotation.Nullable;

import com.example.examplemod.hive.city.HiveTier;
import com.example.examplemod.hive.city.HiveWorld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Hive's vertical circulation (spec §18).
 *
 * <h2>The gap this fills</h2>
 *
 * {@link com.example.examplemod.hive.city.HiveCityLayout} has stacked the city's five levels since
 * FASE 10 — Underhive, Manufactorum, Hab, Administratum, Spire, sixty-four blocks apart in a
 * dimension five hundred and seventy-six tall. A search of the whole {@code hive/} package for
 * {@code elevator|lift|shaft} turns up one comment and one cargo module. There has never been a way
 * to get from one level to the next, which makes the tallest thing in the mod five slabs a player
 * can look at and not walk through.
 *
 * <h2>A transition, not a moving platform</h2>
 *
 * A real lift — a platform that rises sixty-four blocks carrying a player — is an entity ticking for
 * several seconds per trip, with collision against a shaft nobody has built yet. The mod's own
 * performance rules rule that out, and it would buy nothing: what the player wants is to be on the
 * next level. So this is a door that happens to point upwards.
 *
 * <h2>Levels, not offsets</h2>
 *
 * The destination is the next {@link HiveTier}, never "sixty-four blocks from here". A lift placed
 * on a mezzanine ten blocks above the Hab floor still delivers to the Administratum floor rather
 * than to a spot ten blocks into its underside. The tier ladder is the single source of that
 * arithmetic.
 *
 * <h2>Every refusal says which check failed</h2>
 *
 * There are four ways this can decline and each one names itself, because "I clicked it and nothing
 * happened" is what gets reported as a bug. Not in the Hive; no level that way; the far end is
 * solid; and — the one worth having — the shaft is blocked, which is a thing a player can go and fix.
 */
public class HiveTransitLiftBlock extends Block {

    /**
     * How far from the destination level's floor a safe landing is accepted, in blocks.
     *
     * <p>Half a level. Wider and a lift could deliver a rider nearer the level it came from than the
     * one it was going to; narrower and a district whose floor sits a few blocks proud of its
     * nominal Y would read as permanently blocked.
     */
    private static final int LANDING_WINDOW = HiveWorld.LEVEL_HEIGHT / 2;

    public HiveTransitLiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            // The client is told SUCCESS so the arm swings; every decision below is the server's.
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        if (!level.dimension().equals(HiveWorld.LEVEL)) {
            return refuse(serverPlayer, "not_in_hive");
        }

        // Crouch to descend. The same block both ways, because a hive lift that needed a separate
        // "down" block would mean every landing needs two of them and half of them face a wall.
        boolean descending = player.isShiftKeyDown();

        HiveTier from = HiveTier.of(pos.getY());
        HiveTier to = descending ? from.below() : from.above();

        if (to == null) {
            return refuse(serverPlayer, descending ? "no_level_below" : "no_level_above");
        }

        BlockPos landing = findLanding(serverLevel, pos, to);

        if (landing == null) {
            return refuseTo(serverPlayer, "shaft_blocked", to);
        }

        serverPlayer.teleportTo(serverLevel,
                landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                serverPlayer.getYRot(), serverPlayer.getXRot());

        // Reset the fall so a descent does not land as a sixty-four block drop. The lift carried
        // them; it did not throw them.
        serverPlayer.resetFallDistance();

        serverLevel.playSound(null, landing, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS,
                0.8F, 0.7F);
        serverLevel.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS,
                0.6F, 0.7F);

        serverPlayer.displayClientMessage(
                Component.translatable("msg.firstcrusade.hive.lift.arrived", to.displayName())
                        .withStyle(ChatFormatting.GRAY), true);

        return InteractionResult.CONSUME;
    }

    /**
     * A place to stand on the destination level, at the same X/Z as the lift.
     *
     * <p>Searches outward from the level's own floor rather than upward from it, so a landing pad a
     * couple of blocks below nominal is found just as readily as one above. Requires two blocks of
     * clear space with something solid underneath — the same test the game applies to any spawn, and
     * enough to keep the lift from posting a rider inside a wall or over a sixty-block drop.
     *
     * <p>Public so {@code /fchive city lift} can ask the question without a player: riding a lift
     * needs somebody to click it, which is the one thing a headless test cannot do. The command
     * calls <b>this</b> method rather than reimplementing the search, because a test that reached
     * the answer down a private path of its own would be testing a path no game ever takes.
     *
     * @return the position to stand on, or null when the far end has no room
     */
    @Nullable
    public static BlockPos findLanding(ServerLevel level, BlockPos pos, HiveTier to) {
        for (int offset = 0; offset <= LANDING_WINDOW; offset++) {
            for (int sign : offset == 0 ? new int[] {1} : new int[] {1, -1}) {
                int y = to.baseY() + offset * sign;

                if (y < level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 1) {
                    continue;
                }

                BlockPos candidate = new BlockPos(pos.getX(), y, pos.getZ());

                if (isStandable(level, candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos at) {
        return level.getBlockState(at.below()).isSolidRender(level, at.below())
                && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
    }

    private static InteractionResult refuse(ServerPlayer player, String reason) {
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.hive.lift." + reason)
                        .withStyle(ChatFormatting.RED), true);

        return InteractionResult.CONSUME;
    }

    private static InteractionResult refuseTo(ServerPlayer player, String reason, HiveTier to) {
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.hive.lift." + reason, to.displayName())
                        .withStyle(ChatFormatting.RED), true);

        return InteractionResult.CONSUME;
    }
}
