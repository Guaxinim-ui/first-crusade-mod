package com.example.examplemod.campaign.wartable;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.example.examplemod.FirstCrusadeNetwork;
import com.example.examplemod.ImperialCommandCoreBlockEntity;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.campaign.CampaignConfig;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.CampaignFronts;
import com.example.examplemod.campaign.CampaignLog;
import com.example.examplemod.campaign.StrategicLocation;
import com.example.examplemod.campaign.force.DeploymentManager;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.WarFaction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Client → server: a commander's order.
 *
 * <h2>The client asserts nothing</h2>
 *
 * The packet carries three things — where the table is, which order, which sector — and every one of
 * them is re-derived or re-checked here. The screen does not send a cost, a strength, a faction or a
 * front; it could not be believed about any of them. What it sends is a request, and this class
 * answers it or refuses it.
 *
 * <h2>Six checks, in cheapest-first order</h2>
 *
 * <ol>
 *   <li>The table is still there and the player is still at it.</li>
 *   <li>The named sector exists.</li>
 *   <li>Its front exists and is a real front.</li>
 *   <li>The order suits the sector's owner — no defending ground the Orks hold.</li>
 *   <li>The Crusade is not already at its deployment cap on that planet.</li>
 *   <li>A Command Core on that front can pay the War Support.</li>
 * </ol>
 *
 * <p>Every refusal tells the player which one failed. A greyed-out button with no reason is how a
 * working feature gets reported as broken.
 */
public class WarTableOrderPacket {

    private final BlockPos tablePos;
    private final WarTableOrder order;
    private final String sectorId;

    public WarTableOrderPacket(BlockPos tablePos, WarTableOrder order, String sectorId) {
        this.tablePos = tablePos;
        this.order = order;
        this.sectorId = sectorId == null ? "" : sectorId;
    }

    public static void encode(WarTableOrderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.tablePos);
        buffer.writeEnum(packet.order);
        buffer.writeUtf(packet.sectorId, 160);
    }

    public static WarTableOrderPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        WarTableOrder order = buffer.readEnum(WarTableOrder.class);
        String sectorId = buffer.readUtf(160);

        return new WarTableOrderPacket(pos, order, sectorId);
    }

    public static void handle(WarTableOrderPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            Component refusal = resolve(player, packet);

            if (refusal != null) {
                player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            }

            // The table is redrawn either way: a refused order still has to show the player the
            // state it was refused against, and an accepted one has to show the deployment it made.
            FirstCrusadeNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new WarTableSnapshotPacket(WarTableSnapshot.capture(player, packet.tablePos)));
        });

        context.setPacketHandled(true);
    }

    /**
     * Runs every check and, if they all pass, raises the deployment.
     *
     * @return the reason it was refused, or null when the order was given
     */
    @Nullable
    private static Component resolve(ServerPlayer player, WarTableOrderPacket packet) {
        if (!CampaignConfig.enabled()) {
            return Component.translatable("msg.firstcrusade.order.disabled");
        }

        if (!WarTableBlock.canUse(player, packet.tablePos)) {
            return Component.translatable("msg.firstcrusade.order.no_table");
        }

        ServerLevel level = player.serverLevel();
        CampaignData campaign = CampaignData.get(level);

        StrategicSector sector = campaign.sector(packet.sectorId);

        if (sector == null) {
            return Component.translatable("msg.firstcrusade.order.no_sector");
        }

        CampaignFront front = CampaignFronts.byDimension(sector.dimension()).orElse(null);

        if (front == null) {
            return Component.translatable("msg.firstcrusade.order.no_front");
        }

        WarTableOrder order = packet.order;

        if (order.needsEnemyTarget() && !sector.owner().isEnemyOfImperium()) {
            return Component.translatable("msg.firstcrusade.order.not_enemy",
                    sector.type().displayName());
        }

        if (order.needsFriendlyTarget() && sector.owner() != WarFaction.IMPERIUM) {
            return Component.translatable("msg.firstcrusade.order.not_ours",
                    sector.type().displayName());
        }

        if (campaign.countActiveDeployments(front.id(), WarFaction.IMPERIUM)
                >= CampaignConfig.maxDeploymentsPerSide()) {
            return Component.translatable("msg.firstcrusade.order.too_many");
        }

        // The Core is both the payer and the place the force sets out from, so a front with no
        // Imperial base cannot be commanded into — which is the honest rule: there is nobody there
        // to give the order to.
        ServerLevel frontLevel = player.getServer().getLevel(front.dimension());
        BlockPos corePos = payingCore(frontLevel, sector.pos(), order.warSupportCost());

        if (corePos == null) {
            return Component.translatable("msg.firstcrusade.order.no_support",
                    order.warSupportCost());
        }

        DeploymentManager.raise(campaign, front, WarFaction.IMPERIUM,
                new StrategicLocation(front.dimension(), corePos), sector.id(),
                order.strength(), level.getGameTime(), true);

        campaign.stateOf(front).recordEvent(
                "Ordem: " + order.name() + " em " + sector.type().name(), level.getGameTime());

        CampaignLog.war("{} order {} on {} by {}",
                front.path(), order.name(), sector.type().name(), player.getGameProfile().getName());

        player.displayClientMessage(Component.translatable("msg.firstcrusade.order.given",
                order.displayName(), sector.type().displayName()).withStyle(ChatFormatting.GOLD), true);

        return null;
    }

    /**
     * Finds a loaded Command Core on the front that can pay, and takes the War Support from it.
     *
     * <p>Charged here rather than by the caller so the payment and the choice of payer cannot come
     * apart: there is no window in which a Core was picked and then a different one billed.
     *
     * @return where the paying Core is, or null when nothing on the front could pay
     */
    @Nullable
    private static BlockPos payingCore(@Nullable ServerLevel level, BlockPos near, int cost) {
        if (level == null) {
            return null;
        }

        ImperialCommandCoreBlockEntity best = null;
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (long packed : WorldWarMapData.get(level).getCities(level)) {
            BlockPos pos = BlockPos.of(packed);

            if (!level.isLoaded(pos)) {
                continue;
            }

            double distance = pos.distSqr(near);

            if (distance >= bestDistance) {
                continue;
            }

            if (level.getBlockEntity(pos) instanceof ImperialCommandCoreBlockEntity core
                    && core.getImperialWarSupportValue() >= cost) {
                bestDistance = distance;
                best = core;
                bestPos = pos;
            }
        }

        // Charged through spendWarSupport, which checks and deducts in one call: reading the value
        // above and subtracting it here would leave a window for the same pool to fund two orders.
        if (best == null || !best.spendWarSupport(cost)) {
            return null;
        }

        return bestPos;
    }
}
