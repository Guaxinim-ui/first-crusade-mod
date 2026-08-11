package com.example.examplemod.progression;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;

/**
 * A player's standing as a commander: what they have earned, what they have spent it on, and how
 * their raids have gone.
 *
 * <h2>A separate object inside the same profile</h2>
 *
 * It lives inside {@link PlayerProgressionProfile} rather than beside it, so it is saved by the same
 * writer, travels in the same sync packet and is read by the same screen — one profile, two tabs. It
 * is a separate <i>class</i> so that no command id can ever land in the Astartes {@code ranks} map:
 * the two vocabularies never share a container, which is what stops
 * {@link PlayerProgressionTree#exists} from being asked about a node it has never heard of.
 *
 * <h2>Cooldown as a deadline</h2>
 *
 * Same rule as the rest of the progression: {@link #cooldownReadyAt()} is the game time the next
 * raid may start, not a number somebody has to decrement. A player who logs out mid-cooldown comes
 * back to a cooldown that elapsed.
 */
public class PlayerCommanderProfile {

    private int xp;
    private int level;
    private int points;
    private int pointsEarned;
    private int pointsSpent;

    private final Map<String, Integer> ranks = new HashMap<>();

    private int startedRaids;
    private int successfulRaids;
    private int failedRaids;
    private int totalTroopsCalled;

    private long cooldownReadyAt;

    /** So the one-off Astra Veteran grant cannot be collected twice by relogging. */
    private boolean veteranPointGranted;

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

    public static int xpForLevel(int level) {
        return PlayerCommanderBalance.XP_FIRST_LEVEL
                + Math.max(0, level) * PlayerCommanderBalance.XP_PER_LEVEL_GROWTH;
    }

    public int xpForNextLevel() {
        return xpForLevel(this.level);
    }

    /** Adds experience and pays out any levels it bought. Returns how many levels were gained. */
    public int addXp(int amount) {
        if (amount <= 0) {
            return 0;
        }

        this.xp += amount;
        int gained = 0;

        while (this.level < PlayerCommanderBalance.MAX_LEVEL && this.xp >= xpForNextLevel()) {
            this.xp -= xpForNextLevel();
            this.level++;
            gained++;
            grantPoints(PlayerCommanderBalance.POINTS_PER_LEVEL);
        }

        if (this.level >= PlayerCommanderBalance.MAX_LEVEL) {
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

    /** Refuses rather than going negative. Command Points are never allowed below zero. */
    public boolean spendPoints(int amount) {
        if (amount < 0 || this.points < amount) {
            return false;
        }
        this.points -= amount;
        this.pointsSpent += amount;
        return true;
    }

    // ==================================================================== nodes

    public boolean has(String nodeId) {
        return this.ranks.getOrDefault(nodeId, 0) > 0;
    }

    public void take(String nodeId) {
        this.ranks.put(nodeId, 1);
    }

    public Map<String, Integer> ranks() {
        return Map.copyOf(this.ranks);
    }

    // ==================================================================== raid record

    public int startedRaids() {
        return this.startedRaids;
    }

    public int successfulRaids() {
        return this.successfulRaids;
    }

    public int failedRaids() {
        return this.failedRaids;
    }

    public int totalTroopsCalled() {
        return this.totalTroopsCalled;
    }

    public void countRaidStarted() {
        this.startedRaids++;
    }

    public void countRaidWon() {
        this.successfulRaids++;
    }

    public void countRaidFailed() {
        this.failedRaids++;
    }

    public void countTroopsCalled(int troops) {
        this.totalTroopsCalled += Math.max(0, troops);
    }

    // ==================================================================== cooldown and grants

    public long cooldownReadyAt() {
        return this.cooldownReadyAt;
    }

    public void setCooldownReadyAt(long gameTime) {
        this.cooldownReadyAt = gameTime;
    }

    public boolean veteranPointGranted() {
        return this.veteranPointGranted;
    }

    public void markVeteranPointGranted() {
        this.veteranPointGranted = true;
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("Xp", this.xp);
        tag.putInt("Level", this.level);
        tag.putInt("Points", this.points);
        tag.putInt("PointsEarned", this.pointsEarned);
        tag.putInt("PointsSpent", this.pointsSpent);

        CompoundTag rankTag = new CompoundTag();
        this.ranks.forEach(rankTag::putInt);
        tag.put("CommanderRanks", rankTag);

        tag.putInt("StartedRaids", this.startedRaids);
        tag.putInt("SuccessfulRaids", this.successfulRaids);
        tag.putInt("FailedRaids", this.failedRaids);
        tag.putInt("TroopsCalled", this.totalTroopsCalled);
        tag.putLong("CooldownReadyAt", this.cooldownReadyAt);
        tag.putBoolean("VeteranPointGranted", this.veteranPointGranted);

        return tag;
    }

    /**
     * Reads a commander profile.
     *
     * <p>An absent or empty tag — every profile saved before the command tree existed — produces a
     * commander at level zero with nothing bought, which is exactly right: an old save starts its
     * command career from nothing rather than being handed one.
     */
    public static PlayerCommanderProfile load(CompoundTag tag) {
        PlayerCommanderProfile profile = new PlayerCommanderProfile();

        profile.xp = tag.getInt("Xp");
        profile.level = tag.getInt("Level");
        profile.points = tag.getInt("Points");
        profile.pointsEarned = tag.getInt("PointsEarned");
        profile.pointsSpent = tag.getInt("PointsSpent");

        CompoundTag rankTag = tag.getCompound("CommanderRanks");
        for (String key : rankTag.getAllKeys()) {
            // A node dropped between versions must not resurrect as a permission nobody can see.
            if (PlayerCommanderTree.exists(key)) {
                profile.ranks.put(key, rankTag.getInt(key));
            }
        }

        profile.startedRaids = tag.getInt("StartedRaids");
        profile.successfulRaids = tag.getInt("SuccessfulRaids");
        profile.failedRaids = tag.getInt("FailedRaids");
        profile.totalTroopsCalled = tag.getInt("TroopsCalled");
        profile.cooldownReadyAt = tag.getLong("CooldownReadyAt");
        profile.veteranPointGranted = tag.getBoolean("VeteranPointGranted");

        return profile;
    }

    /** Wipes the command career back to nothing. Used by {@code /fccommand reset}. */
    public void reset() {
        this.xp = 0;
        this.level = 0;
        this.points = 0;
        this.pointsEarned = 0;
        this.pointsSpent = 0;
        this.ranks.clear();
        this.startedRaids = 0;
        this.successfulRaids = 0;
        this.failedRaids = 0;
        this.totalTroopsCalled = 0;
        this.cooldownReadyAt = 0L;
        this.veteranPointGranted = false;
        this.ranks.put(PlayerCommanderTree.ROOT_ID, 1);
    }
}
