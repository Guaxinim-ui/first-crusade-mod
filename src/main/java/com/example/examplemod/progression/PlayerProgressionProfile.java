package com.example.examplemod.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * One player's whole progression: what they have bought, what they have become, what they are
 * currently on the operating table for, and how far into the Blood Trial they are.
 *
 * <h2>Plain state, no behaviour</h2>
 *
 * This class stores and serialises; it does not decide. Whether a node <i>may</i> be bought is
 * {@link PlayerProgressionRequirements}, and what a rank <i>does</i> is
 * {@link PlayerProgressionManager}. Keeping it dumb is what lets the same object be written to disk,
 * copied into a packet and read by the client screen without dragging the server's rules along.
 *
 * <h2>Cooldowns are deadlines, not counters</h2>
 *
 * Every cooldown is stored as the game time at which the ability comes back, not as a number that
 * has to be decremented. A counter that ticks needs someone to tick it — and would keep ticking while
 * the player is logged out, or stop while the chunk is unloaded. A deadline compared against
 * {@code level.getGameTime()} is correct without anyone doing anything.
 */
public class PlayerProgressionProfile {

    private int version = PlayerProgressionBalance.DATA_VERSION;

    private int xp;
    private int level;
    private int points;
    private int pointsEarned;
    private int pointsSpent;

    private final Map<String, Integer> ranks = new HashMap<>();
    private final Set<String> implants = new LinkedHashSet<>();

    private PlayerEvolutionStage stage = PlayerEvolutionStage.ASTRA_RECRUIT;

    @Nullable
    private String surgeryNodeId;
    private long surgeryStartedAt;
    private long surgeryEndsAt;

    @Nullable
    private BlockPos boundCore;

    private long prayerReadyAt;
    private long rollReadyAt;
    private long acidReadyAt;
    private long stasisReadyAt;
    private long larramanReadyAt;

    private int trialOrkKills;
    private int trialEliteKills;
    private int trialWarbossKills;
    private int trialRaids;
    private boolean trialComplete;

    /**
     * The player's other career: Imperial Command.
     *
     * <p>It is a field here, not a second SavedData, so it is written by the same save, sent by the
     * same packet and read by the same screen. It is a separate class so that a command node id can
     * never end up in {@link #ranks} — the two trees share a profile but never a container.
     */
    private PlayerCommanderProfile commander = new PlayerCommanderProfile();

    /**
     * The Ork half of this player, kept here for the same reason the Commander profile is: one
     * SavedData, one sync packet, one writer. It is a separate object with its own ranks map — Ork
     * node ids must never land in the Astartes {@code ranks} map, or one typo lets a Boy buy a
     * Multi-lung. An Imperial player carries an empty one and pays a handful of bytes for it.
     */
    private com.example.examplemod.progression.ork.PlayerOrkProgressionProfile ork =
            new com.example.examplemod.progression.ork.PlayerOrkProgressionProfile();

    public com.example.examplemod.progression.ork.PlayerOrkProgressionProfile ork() {
        return this.ork;
    }

    public PlayerCommanderProfile commander() {
        return this.commander;
    }

    // ==================================================================== experience and points

    public int xp() {
        return this.xp;
    }

    public int level() {
        return this.level;
    }

    public int points() {
        return this.points;
    }

    public int pointsEarned() {
        return this.pointsEarned;
    }

    public int pointsSpent() {
        return this.pointsSpent;
    }

    /** XP needed to go from {@code level} to the next one. Grows linearly; never zero. */
    public static int xpForLevel(int level) {
        return PlayerProgressionBalance.XP_FIRST_LEVEL
                + Math.max(0, level) * PlayerProgressionBalance.XP_PER_LEVEL_GROWTH;
    }

    public int xpForNextLevel() {
        return xpForLevel(this.level);
    }

    /**
     * Adds experience and pays out any levels it bought.
     *
     * @return how many levels were gained, so the caller can tell the player
     */
    public int addXp(int amount) {
        if (amount <= 0) {
            return 0;
        }

        this.xp += amount;
        int gained = 0;

        while (this.level < PlayerProgressionBalance.MAX_LEVEL && this.xp >= xpForNextLevel()) {
            this.xp -= xpForNextLevel();
            this.level++;
            gained++;
            grantPoints(PlayerProgressionBalance.POINTS_PER_LEVEL);
        }

        if (this.level >= PlayerProgressionBalance.MAX_LEVEL) {
            this.xp = Math.min(this.xp, xpForNextLevel());
        }

        return gained;
    }

    public void grantPoints(int amount) {
        if (amount <= 0) {
            return;
        }
        this.points += amount;
        this.pointsEarned += amount;
    }

    /** Spends doctrine. Refuses rather than going negative — points are never allowed below zero. */
    public boolean spendPoints(int amount) {
        if (amount < 0 || this.points < amount) {
            return false;
        }
        this.points -= amount;
        this.pointsSpent += amount;
        return true;
    }

    // ==================================================================== ranks

    public int rank(String nodeId) {
        return this.ranks.getOrDefault(nodeId, 0);
    }

    public boolean hasRank(String nodeId) {
        return rank(nodeId) > 0;
    }

    public void setRank(String nodeId, int rank) {
        if (rank <= 0) {
            this.ranks.remove(nodeId);
        } else {
            this.ranks.put(nodeId, rank);
        }
    }

    public Map<String, Integer> ranks() {
        return Map.copyOf(this.ranks);
    }

    // ==================================================================== implants and stage

    public boolean hasImplant(String nodeId) {
        return this.implants.contains(nodeId);
    }

    public Set<String> implants() {
        return Set.copyOf(this.implants);
    }

    public int implantCount() {
        return this.implants.size();
    }

    public void addImplant(String nodeId) {
        this.implants.add(nodeId);
    }

    public PlayerEvolutionStage stage() {
        return this.stage;
    }

    public void setStage(PlayerEvolutionStage stage) {
        this.stage = stage;
    }

    // ==================================================================== surgery

    @Nullable
    public String surgeryNodeId() {
        return this.surgeryNodeId;
    }

    public boolean isInSurgery() {
        return this.surgeryNodeId != null;
    }

    public long surgeryStartedAt() {
        return this.surgeryStartedAt;
    }

    public long surgeryEndsAt() {
        return this.surgeryEndsAt;
    }

    public void beginSurgery(String nodeId, long now, int ticks) {
        this.surgeryNodeId = nodeId;
        this.surgeryStartedAt = now;
        this.surgeryEndsAt = now + ticks;
    }

    public void clearSurgery() {
        this.surgeryNodeId = null;
        this.surgeryStartedAt = 0L;
        this.surgeryEndsAt = 0L;
    }

    /** 0..1 through the current operation, or 0 when nobody is on the table. */
    public float surgeryProgress(long now) {
        if (!isInSurgery() || this.surgeryEndsAt <= this.surgeryStartedAt) {
            return 0.0F;
        }

        float span = this.surgeryEndsAt - this.surgeryStartedAt;
        return Math.max(0.0F, Math.min(1.0F, (now - this.surgeryStartedAt) / span));
    }

    @Nullable
    public BlockPos boundCore() {
        return this.boundCore;
    }

    public void setBoundCore(@Nullable BlockPos pos) {
        this.boundCore = pos;
    }

    // ==================================================================== cooldowns

    public long prayerReadyAt() {
        return this.prayerReadyAt;
    }

    public void setPrayerReadyAt(long time) {
        this.prayerReadyAt = time;
    }

    public long rollReadyAt() {
        return this.rollReadyAt;
    }

    public void setRollReadyAt(long time) {
        this.rollReadyAt = time;
    }

    public long acidReadyAt() {
        return this.acidReadyAt;
    }

    public void setAcidReadyAt(long time) {
        this.acidReadyAt = time;
    }

    public long stasisReadyAt() {
        return this.stasisReadyAt;
    }

    public void setStasisReadyAt(long time) {
        this.stasisReadyAt = time;
    }

    public long larramanReadyAt() {
        return this.larramanReadyAt;
    }

    public void setLarramanReadyAt(long time) {
        this.larramanReadyAt = time;
    }

    // ==================================================================== blood trial

    public int trialOrkKills() {
        return this.trialOrkKills;
    }

    public int trialEliteKills() {
        return this.trialEliteKills;
    }

    public int trialWarbossKills() {
        return this.trialWarbossKills;
    }

    public int trialRaids() {
        return this.trialRaids;
    }

    public boolean trialComplete() {
        return this.trialComplete;
    }

    public void setTrialComplete(boolean complete) {
        this.trialComplete = complete;
    }

    /** Only counted once the player is a Neophyte — the caller is the one that checks the stage. */
    public void countTrialKill(boolean elite, boolean warboss) {
        this.trialOrkKills++;
        if (elite) {
            this.trialEliteKills++;
        }
        if (warboss) {
            this.trialWarbossKills++;
        }
    }

    public void countTrialRaid() {
        this.trialRaids++;
    }

    /** Either the full butcher's bill, or two completed Imperial raids. */
    public boolean trialRequirementsMet() {
        boolean byBlood = this.trialOrkKills >= PlayerProgressionBalance.TRIAL_ORK_KILLS
                && this.trialEliteKills >= PlayerProgressionBalance.TRIAL_ELITE_KILLS
                && this.trialWarbossKills >= PlayerProgressionBalance.TRIAL_WARBOSS_KILLS;

        return byBlood || this.trialRaids >= PlayerProgressionBalance.TRIAL_RAIDS_ALTERNATIVE;
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", this.version);
        tag.putInt("Xp", this.xp);
        tag.putInt("Level", this.level);
        tag.putInt("Points", this.points);
        tag.putInt("PointsEarned", this.pointsEarned);
        tag.putInt("PointsSpent", this.pointsSpent);
        tag.putString("Stage", this.stage.name());

        CompoundTag rankTag = new CompoundTag();
        this.ranks.forEach(rankTag::putInt);
        tag.put("Ranks", rankTag);

        ListTag implantTag = new ListTag();
        for (String implant : this.implants) {
            implantTag.add(StringTag.valueOf(implant));
        }
        tag.put("Implants", implantTag);

        if (this.surgeryNodeId != null) {
            tag.putString("SurgeryNode", this.surgeryNodeId);
            tag.putLong("SurgeryStart", this.surgeryStartedAt);
            tag.putLong("SurgeryEnd", this.surgeryEndsAt);
        }

        if (this.boundCore != null) {
            tag.putLong("BoundCore", this.boundCore.asLong());
        }

        tag.putLong("PrayerReady", this.prayerReadyAt);
        tag.putLong("RollReady", this.rollReadyAt);
        tag.putLong("AcidReady", this.acidReadyAt);
        tag.putLong("StasisReady", this.stasisReadyAt);
        tag.putLong("LarramanReady", this.larramanReadyAt);

        tag.putInt("TrialOrks", this.trialOrkKills);
        tag.putInt("TrialElites", this.trialEliteKills);
        tag.putInt("TrialWarbosses", this.trialWarbossKills);
        tag.putInt("TrialRaids", this.trialRaids);
        tag.putBoolean("TrialComplete", this.trialComplete);

        tag.put("Commander", this.commander.save());
        tag.put("Ork", this.ork.save());

        return tag;
    }

    public static PlayerProgressionProfile load(CompoundTag tag) {
        PlayerProgressionProfile profile = new PlayerProgressionProfile();

        profile.version = tag.contains("Version") ? tag.getInt("Version") : 1;
        profile.xp = tag.getInt("Xp");
        profile.level = tag.getInt("Level");
        profile.points = tag.getInt("Points");
        profile.pointsEarned = tag.getInt("PointsEarned");
        profile.pointsSpent = tag.getInt("PointsSpent");
        profile.stage = PlayerEvolutionStage.byName(tag.getString("Stage"));

        CompoundTag rankTag = tag.getCompound("Ranks");
        for (String key : rankTag.getAllKeys()) {
            // A node dropped from the tree between versions must not resurrect as a phantom rank.
            if (PlayerProgressionTree.exists(key)) {
                profile.ranks.put(key, rankTag.getInt(key));
            }
        }

        ListTag implantTag = tag.getList("Implants", Tag.TAG_STRING);
        for (int i = 0; i < implantTag.size(); i++) {
            String id = implantTag.getString(i);
            if (PlayerProgressionTree.exists(id)) {
                profile.implants.add(id);
            }
        }

        if (tag.contains("SurgeryNode")) {
            String node = tag.getString("SurgeryNode");
            if (PlayerProgressionTree.exists(node)) {
                profile.surgeryNodeId = node;
                profile.surgeryStartedAt = tag.getLong("SurgeryStart");
                profile.surgeryEndsAt = tag.getLong("SurgeryEnd");
            }
        }

        if (tag.contains("BoundCore")) {
            profile.boundCore = BlockPos.of(tag.getLong("BoundCore"));
        }

        profile.prayerReadyAt = tag.getLong("PrayerReady");
        profile.rollReadyAt = tag.getLong("RollReady");
        profile.acidReadyAt = tag.getLong("AcidReady");
        profile.stasisReadyAt = tag.getLong("StasisReady");
        profile.larramanReadyAt = tag.getLong("LarramanReady");

        profile.trialOrkKills = tag.getInt("TrialOrks");
        profile.trialEliteKills = tag.getInt("TrialElites");
        profile.trialWarbossKills = tag.getInt("TrialWarbosses");
        profile.trialRaids = tag.getInt("TrialRaids");
        profile.trialComplete = tag.getBoolean("TrialComplete");

        // Version 1 saves have no Commander tag at all. getCompound returns an empty one, which
        // loads as a commander at level zero — the migration is that there is nothing to migrate.
        profile.commander = PlayerCommanderProfile.load(tag.getCompound("Commander"));

        // Absent on every save written before the WAAAGH tree existed. An empty tag loads as a
        // Boy with nothing, which is where an Ork starts anyway.
        profile.ork = com.example.examplemod.progression.ork.PlayerOrkProgressionProfile
                .load(tag.getCompound("Ork"));

        return profile;
    }

    /** Wipes everything back to a fresh recruit. Used by {@code /fcprogress reset}. */
    public void reset() {
        this.xp = 0;
        this.level = 0;
        this.points = 0;
        this.pointsEarned = 0;
        this.pointsSpent = 0;
        this.ranks.clear();
        this.implants.clear();
        this.stage = PlayerEvolutionStage.ASTRA_RECRUIT;
        clearSurgery();
        this.boundCore = null;
        this.prayerReadyAt = 0L;
        this.rollReadyAt = 0L;
        this.acidReadyAt = 0L;
        this.stasisReadyAt = 0L;
        this.larramanReadyAt = 0L;
        this.trialOrkKills = 0;
        this.trialEliteKills = 0;
        this.trialWarbossKills = 0;
        this.trialRaids = 0;
        this.trialComplete = false;
        this.ranks.put(PlayerProgressionTree.ROOT_ID, 1);
        this.commander.reset();
    }

    /** Kills that happened before Neophyte do not count, so the counters start clean there. */
    public void resetTrialCounters() {
        this.trialOrkKills = 0;
        this.trialEliteKills = 0;
        this.trialWarbossKills = 0;
        this.trialRaids = 0;
        this.trialComplete = false;
    }

    /** Everything the anti-farm guard needs, kept out of NBT: it is worthless across a restart. */
    public final transient Map<String, Integer> recentKills = new HashMap<>();
    public transient long recentKillsWindowStart;
    public transient long lastDamagedAt;
    public transient int memoryStacks;
    public transient long memoryExpiresAt;
    public transient final Set<Integer> summonedEntityIds = new HashSet<>();
}
