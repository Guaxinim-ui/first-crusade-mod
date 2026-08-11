package com.example.examplemod.assault;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One player-started raid, in full.
 *
 * <h2>Plain state, no behaviour</h2>
 *
 * Same division as the rest of the mod's data classes: this stores and serialises, and
 * {@link ImperialAssaultManager} decides. Keeping it dumb is what lets the whole raid be written to
 * disk and read back without dragging the server's rules through the save.
 *
 * <h2>Everything that must survive a restart is here</h2>
 *
 * A raid holds real soldiers away from their base. If the server stops mid-assault, the only way
 * those soldiers get home is for this record to have written down who they are and where they came
 * from — which is why the troop list, the origin Core and the reward flag are all persisted rather
 * than kept in memory.
 */
public class ImperialAssaultRecord {

    private final UUID raidId;
    private final ResourceKey<Level> dimension;
    private final BlockPos campPos;
    private final UUID initiator;

    @Nullable
    private BlockPos originCore;

    private final long startedAtGameTime;

    private ImperialAssaultPhase phase = ImperialAssaultPhase.STARTING;

    private final List<ExpeditionTroopData> troops = new ArrayList<>();
    private final Set<UUID> participants = new LinkedHashSet<>();

    private int initialDefenders;

    /** Overwritten from the commander's profile the moment troops are actually called. */
    private int approachDistance =
            com.example.examplemod.progression.PlayerCommanderBalance.APPROACH_DISTANCE;

    private boolean rewardGranted;
    private boolean arrivalBuffApplied;

    /** Game time the initiator was last seen where they should be; 0 means "present right now". */
    private long absentSince;

    public ImperialAssaultRecord(UUID raidId, ResourceKey<Level> dimension, BlockPos campPos,
                                 UUID initiator, long startedAtGameTime) {
        this.raidId = raidId;
        this.dimension = dimension;
        this.campPos = campPos.immutable();
        this.initiator = initiator;
        this.startedAtGameTime = startedAtGameTime;
        this.participants.add(initiator);
    }

    // ==================================================================== identity

    public UUID raidId() {
        return this.raidId;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public BlockPos campPos() {
        return this.campPos;
    }

    public UUID initiator() {
        return this.initiator;
    }

    public long startedAtGameTime() {
        return this.startedAtGameTime;
    }

    // ==================================================================== state

    public ImperialAssaultPhase phase() {
        return this.phase;
    }

    public void setPhase(ImperialAssaultPhase phase) {
        this.phase = phase;
    }

    @Nullable
    public BlockPos originCore() {
        return this.originCore;
    }

    public void setOriginCore(@Nullable BlockPos originCore) {
        this.originCore = originCore == null ? null : originCore.immutable();
    }

    public List<ExpeditionTroopData> troops() {
        return this.troops;
    }

    public void addTroop(ExpeditionTroopData troop) {
        this.troops.add(troop);
    }

    public Set<UUID> participants() {
        return this.participants;
    }

    public void addParticipant(UUID playerId) {
        this.participants.add(playerId);
    }

    public int initialDefenders() {
        return this.initialDefenders;
    }

    public void setInitialDefenders(int initialDefenders) {
        this.initialDefenders = initialDefenders;
    }

    public int approachDistance() {
        return this.approachDistance;
    }

    public void setApproachDistance(int approachDistance) {
        this.approachDistance = approachDistance;
    }

    /** True the first time it is asked, false ever after — the reward cannot be paid twice. */
    public boolean claimReward() {
        if (this.rewardGranted) {
            return false;
        }
        this.rewardGranted = true;
        return true;
    }

    public boolean rewardGranted() {
        return this.rewardGranted;
    }

    public boolean arrivalBuffApplied() {
        return this.arrivalBuffApplied;
    }

    public void markArrivalBuffApplied() {
        this.arrivalBuffApplied = true;
    }

    public long absentSince() {
        return this.absentSince;
    }

    public void setAbsentSince(long gameTime) {
        this.absentSince = gameTime;
    }

    /** Every soldier that came home or died is off the books; an empty list ends the return phase. */
    public void clearTroops() {
        this.troops.clear();
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("RaidId", this.raidId);
        tag.putString("Dimension", this.dimension.location().toString());
        tag.putLong("CampPos", this.campPos.asLong());
        tag.putUUID("Initiator", this.initiator);
        tag.putLong("StartedAt", this.startedAtGameTime);
        tag.putString("Phase", this.phase.name());
        tag.putInt("InitialDefenders", this.initialDefenders);
        tag.putInt("ApproachDistance", this.approachDistance);
        tag.putBoolean("RewardGranted", this.rewardGranted);
        tag.putBoolean("ArrivalBuffApplied", this.arrivalBuffApplied);
        tag.putLong("AbsentSince", this.absentSince);

        if (this.originCore != null) {
            tag.putLong("OriginCore", this.originCore.asLong());
        }

        ListTag troopList = new ListTag();
        for (ExpeditionTroopData troop : this.troops) {
            troopList.add(troop.save());
        }
        tag.put("Troops", troopList);

        ListTag participantList = new ListTag();
        for (UUID participant : this.participants) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", participant);
            participantList.add(entry);
        }
        tag.put("Participants", participantList);

        return tag;
    }

    @Nullable
    public static ImperialAssaultRecord load(CompoundTag tag) {
        if (!tag.hasUUID("RaidId") || !tag.hasUUID("Initiator")) {
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimensionId == null) {
            // A raid in a dimension this save no longer knows cannot be resumed or cleaned up
            // safely; dropping it is better than resurrecting it pointing at nowhere.
            return null;
        }

        ImperialAssaultRecord record = new ImperialAssaultRecord(
                tag.getUUID("RaidId"),
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId),
                BlockPos.of(tag.getLong("CampPos")),
                tag.getUUID("Initiator"),
                tag.getLong("StartedAt"));

        try {
            record.phase = ImperialAssaultPhase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException unknownPhase) {
            // An unreadable phase means the raid cannot be continued honestly — send everyone home.
            record.phase = ImperialAssaultPhase.RETURNING;
        }

        record.initialDefenders = tag.getInt("InitialDefenders");
        record.approachDistance = Math.max(1, tag.getInt("ApproachDistance"));
        record.rewardGranted = tag.getBoolean("RewardGranted");
        record.arrivalBuffApplied = tag.getBoolean("ArrivalBuffApplied");
        record.absentSince = tag.getLong("AbsentSince");

        if (tag.contains("OriginCore")) {
            record.originCore = BlockPos.of(tag.getLong("OriginCore"));
        }

        ListTag troopList = tag.getList("Troops", Tag.TAG_COMPOUND);
        for (int i = 0; i < troopList.size(); i++) {
            ExpeditionTroopData troop = ExpeditionTroopData.load(troopList.getCompound(i));
            if (troop != null) {
                record.troops.add(troop);
            }
        }

        ListTag participantList = tag.getList("Participants", Tag.TAG_COMPOUND);
        for (int i = 0; i < participantList.size(); i++) {
            CompoundTag entry = participantList.getCompound(i);
            if (entry.hasUUID("Id")) {
                record.participants.add(entry.getUUID("Id"));
            }
        }

        record.participants.add(record.initiator);

        return record;
    }
}
