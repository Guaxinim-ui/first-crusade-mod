package com.example.examplemod;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client -> server request from the Ork city panel ({@link OrkCampScreen}). For now it carries a
 * single build action (raise a Loot Pit); the server re-validates distance and loot in
 * {@link OrkCampBlockEntity#buildLootPit}.
 */
public class OrkCampActionPacket {
    public enum Action {
        BUILD_LOOT_PIT,

        /**
         * An Imperial player declares a raid on this camp.
         *
         * <p>The packet carries the camp and nothing else. How many soldiers answer, which base
         * they come from, how far out they land, whether it is even allowed and what the reward is
         * are all decided by {@link com.example.examplemod.assault.ImperialAssaultManager} on the
         * server — the client's only contribution is "this player clicked that block".
         */
        START_IMPERIAL_RAID,

        /**
         * An Ork player pours the teeth in his pockets into his own tally.
         *
         * <p>The packet carries the camp and nothing else — not how many teeth, which is the whole
         * point. A client that could name the amount could name any amount; the server counts what
         * is actually in the inventory and converts that.
         */
        STASH_TEEF,

        /**
         * The Ork half of §24: the camp doing on command what it otherwise only does on its clock.
         *
         * <p>Like every other action here, the packet carries the camp and nothing else — not a
         * cost, not a count, not a target. The camp owns all of those and re-checks each one.
         */
        RECRUIT_BOY,
        PROMOTE_NOB,
        CYCLE_TARGET,
        ORDER_WAAAGH
    }

    private final BlockPos campPos;
    private final Action action;

    public OrkCampActionPacket(BlockPos campPos, Action action) {
        this.campPos = campPos;
        this.action = action;
    }

    public static void encode(OrkCampActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.campPos);
        buffer.writeEnum(packet.action);
    }

    public static OrkCampActionPacket decode(FriendlyByteBuf buffer) {
        return new OrkCampActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    public static void handle(OrkCampActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            ServerLevel serverLevel = player.serverLevel();

            double distance = player.distanceToSqr(
                    packet.campPos.getX() + 0.5D, packet.campPos.getY() + 0.5D, packet.campPos.getZ() + 0.5D);
            if (distance > 64.0D) {
                player.displayClientMessage(Component.translatable("msg.firstcrusade.ork.too_far"), true);
                return;
            }

            BlockEntity blockEntity = serverLevel.getBlockEntity(packet.campPos);
            if (blockEntity instanceof OrkCampBlockEntity camp) {
                switch (packet.action) {
                    case BUILD_LOOT_PIT -> camp.buildLootPit(player);
                    case START_IMPERIAL_RAID -> startRaid(player, packet.campPos);
                    case STASH_TEEF -> stashTeef(player);

                    // Commanding the camp is an Ork's business, and the check is here rather than
                    // in each method so there is one place it can be wrong. The screen hides these
                    // buttons from everyone else, but hiding is a courtesy and this is the rule.
                    case RECRUIT_BOY -> ifOrk(player, () -> camp.recruitBoy(player));
                    case PROMOTE_NOB -> ifOrk(player, () -> camp.promoteNob(player));
                    case CYCLE_TARGET -> ifOrk(player, () -> camp.cycleTarget(player));
                    case ORDER_WAAAGH -> ifOrk(player, () -> camp.orderWaaagh(player));
                }
            }
        });

        context.setPacketHandled(true);
    }

    /**
     * Asks the assault manager to open a raid, and shows the player whatever it says.
     *
     * <p>Every rule — faction, range, a raid already running here, this player already leading one,
     * the cooldown — lives in the manager. Nothing is duplicated here, so there is exactly one
     * place a rule can be wrong.
     */
    /**
     * Turns Ork Teeth in the inventory into personal Teef.
     *
     * <p>Counted and removed item by item on the server, so the two halves of the trade can never
     * disagree: the teeth are gone before the Teef exist, and a full inventory or a failed removal
     * costs the player nothing. This is the only way Teef enters the game outside a command.
     *
     * <p>The strategic {@code StrategicResourceType.TEEF} is untouched. That is the Ork AI's war
     * chest and shares nothing with a player's pocket but the word.
     */
    /**
     * Runs a camp-command action only for a player who actually fights for the WAAAGH!.
     *
     * <p>The same refusal an Ork-only action already gave, in one place, so a fifth command added
     * later cannot quietly ship without the check.
     */
    private static void ifOrk(ServerPlayer player, Runnable action) {
        if (!com.example.examplemod.progression.ork.PlayerOrkProgressionRequirements.isOrk(player)) {
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.ork.not_ork"), true);
            return;
        }

        action.run();
    }

    private static void stashTeef(net.minecraft.server.level.ServerPlayer player) {
        if (!com.example.examplemod.progression.ork.PlayerOrkProgressionRequirements.isOrk(player)) {
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.ork.not_ork"), true);
            return;
        }

        net.minecraft.world.item.Item tooth = FCRegistry.ORK_TEETH.get();
        int teeth = 0;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(tooth)) {
                teeth += stack.getCount();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }

        if (teeth <= 0) {
            player.displayClientMessage(
                    Component.translatable("msg.firstcrusade.ork.no_teeth"), true);
            return;
        }

        com.example.examplemod.progression.ork.PlayerOrkProgressionProfile ork =
                com.example.examplemod.progression.PlayerProgressionManager.profile(player).ork();

        // TEEF IZ MONEY, KUNNIN BUT BRUTAL and the Bad Moons all pay here, through the one scaler
        // every Teef award in the mod goes through. Writing the bonus at this call site would have
        // been a bonus the Core-destroyed award silently did not have.
        int gained = com.example.examplemod.progression.ork.PlayerOrkRewardModifiers.scaleTeef(ork,
                teeth * com.example.examplemod.progression.ork
                        .PlayerOrkProgressionBalance.TEEF_PER_ORK_TOOTH);

        ork.addTeef(gained);

        com.example.examplemod.progression.PlayerProgressionManager
                .data(player.serverLevel()).markChanged();
        com.example.examplemod.progression.PlayerProgressionNetwork.sync(player);

        player.displayClientMessage(Component.translatable(
                "msg.firstcrusade.ork.teef_stashed", gained, ork.teef()), true);
    }

    private static void startRaid(ServerPlayer player, BlockPos campPos) {
        Component problem = com.example.examplemod.assault.ImperialAssaultManager
                .startRaid(player, campPos);

        if (!problem.getString().isEmpty()) {
            player.displayClientMessage(problem, true);
        }
    }
}
