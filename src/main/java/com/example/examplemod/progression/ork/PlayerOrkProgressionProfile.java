package com.example.examplemod.progression.ork;

import java.util.HashMap;
import java.util.Map;

import com.example.examplemod.OrkClan;

import net.minecraft.nbt.CompoundTag;

/**
 * Everything the game remembers about a player's career as an Ork.
 *
 * <h2>Three numbers that are not the same number</h2>
 *
 * <ul>
 *   <li><b>krumpScore</b> — reputation. Permanent, never spent, no levels. It is what the evolution
 *       gates read, and the only honest answer to "has this git actually done anything".</li>
 *   <li><b>teef</b> — currency. The one thing the tree costs. Kept strictly apart from
 *       {@code StrategicResourceType.TEEF}, which is the Ork AI's war chest and has nothing to do
 *       with a player's pocket.</li>
 *   <li><b>waaaghFury</b> — a mood, 0 to 100, that climbs in a fight and drains out of one. It is
 *       neither of the other two, and it is neither of the <i>other</i> two WAAAGHs in this mod:
 *       {@code WaaaghOverlordData.waaagh} is the global tide and {@code OrkCampBlockEntity.waaagh}
 *       is one camp's local mood. Three values, one word, three classes — which is the only thing
 *       keeping them apart.</li>
 * </ul>
 *
 * <h2>Its own ranks map</h2>
 *
 * Ork node ranks never go in the Astartes {@code ranks} map, exactly as
 * {@code PlayerCommanderProfile} keeps its own. Two vocabularies in one map is one typo away from a
 * Boy buying a Multi-lung.
 *
 * <h2>Fury is stored as a timestamp problem</h2>
 *
 * The decay is not ticked. {@link #fury(long)} works out what the value is <i>now</i> from when the
 * last blow landed, so a player standing around with Fury draining costs nothing at all, and a
 * player at zero costs less than that.
 */
public final class PlayerOrkProgressionProfile {

    private int version = PlayerOrkProgressionBalance.DATA_VERSION;

    // ---- reputation and currency ------------------------------------------------
    private int krumpScore;
    private int teef;
    private int totalTeefEarned;
    private int totalTeefSpent;

    // ---- what he has done -------------------------------------------------------
    private int validKills;
    private int eliteKills;
    private int imperialKills;
    private int imperialCoresDestroyed;
    private int majorVictories;

    // ---- what he is -------------------------------------------------------------
    private PlayerOrkEvolutionStage stage = PlayerOrkEvolutionStage.ORK_BOY;

    /** Null until he is a Nob and picks one. Permanent after that. */
    private OrkClan clan;

    private final Map<String, Integer> ranks = new HashMap<>();

    {
        // Every Ork holds the root from the moment he exists. The four first-tier nodes name it as
        // a parent, so a profile without it is a profile where nothing at all can be bought - which
        // is exactly what shipped. Set in an instance initialiser so it is true for a brand-new
        // profile and for one that is about to be loaded over.
        this.ranks.put(PlayerOrkProgressionTree.ROOT_ID, 1);
    }

    // ---- the mood ---------------------------------------------------------------
    private int furyAtLastCombat;
    private long lastCombatGameTime;
    private long lastFuryDamageTick;

    // ---- cooldowns --------------------------------------------------------------
    private long waaaghReadyAt;
    private long headbuttReadyAt;
    private long chargeReadyAt;

    /**
     * When NOT DEAD YET may catch him again.
     *
     * <p>Saved rather than held in a transient map: a passive that survives a lethal blow and then
     * resets itself on every relog is a passive with no cooldown at all.
     */
    private long lastStandReadyAt;

    /** When BOYZ, OVER 'ERE may be shouted again. */
    private long orderReadyAt;

    // ==================================================================== reputation

    public int krumpScore() {
        return this.krumpScore;
    }

    /** Krumpagem only ever goes up. There is no mechanic in the design that spends it. */
    public void addKrump(int amount) {
        if (amount > 0) {
            this.krumpScore += amount;
        }
    }

    public void setKrump(int amount) {
        this.krumpScore = Math.max(0, amount);
    }

    // ==================================================================== teef

    public int teef() {
        return this.teef;
    }

    public int totalTeefSpent() {
        return this.totalTeefSpent;
    }

    public void addTeef(int amount) {
        if (amount <= 0) {
            return;
        }

        this.teef += amount;
        this.totalTeefEarned += amount;
    }

    /**
     * Spends Teef, or refuses.
     *
     * <p>Returns false and changes nothing when the player cannot afford it — the balance is never
     * allowed to go negative, and the check lives here rather than at each call site so there is
     * exactly one place that can get it wrong.
     */
    public boolean spendTeef(int amount) {
        if (amount <= 0 || this.teef < amount) {
            return false;
        }

        this.teef -= amount;
        this.totalTeefSpent += amount;
        return true;
    }

    // ==================================================================== tallies

    public int validKills() {
        return this.validKills;
    }

    public int eliteKills() {
        return this.eliteKills;
    }

    public int imperialKills() {
        return this.imperialKills;
    }

    public int imperialCoresDestroyed() {
        return this.imperialCoresDestroyed;
    }

    public int majorVictories() {
        return this.majorVictories;
    }

    public void countKill(boolean elite, boolean imperial) {
        this.validKills++;
        if (elite) {
            this.eliteKills++;
        }
        if (imperial) {
            this.imperialKills++;
        }
    }

    public void countCoreDestroyed() {
        this.imperialCoresDestroyed++;
    }

    public void countMajorVictory() {
        this.majorVictories++;
    }

    // ==================================================================== body and clan

    public PlayerOrkEvolutionStage stage() {
        return this.stage;
    }

    public void setStage(PlayerOrkEvolutionStage stage) {
        this.stage = stage == null ? PlayerOrkEvolutionStage.ORK_BOY : stage;
    }

    public OrkClan clan() {
        return this.clan;
    }

    public boolean hasClan() {
        return this.clan != null;
    }

    /** Chosen once, at Nob. An admin command may still override it for testing. */
    public void setClan(OrkClan clan) {
        this.clan = clan;
    }

    // ==================================================================== the tree

    public int rank(String nodeId) {
        return this.ranks.getOrDefault(nodeId, 0);
    }

    public void setRank(String nodeId, int rank) {
        if (rank <= 0) {
            this.ranks.remove(nodeId);
        } else {
            this.ranks.put(nodeId, rank);
        }
    }

    public Map<String, Integer> ranks() {
        return this.ranks;
    }

    /** Total ranks across every node, free ones included. Debug only — see below. */
    public int totalRanks() {
        int total = 0;
        for (int rank : this.ranks.values()) {
            total += rank;
        }
        return total;
    }

    /**
     * Ranks the player actually paid for.
     *
     * <p>The evolution gates ask "how much training has he done", and {@link #totalRanks()} answered
     * with the root and every evolution included — five free ranks a player is handed for existing
     * and for growing. A Warboss gate asking for eighteen was really asking for thirteen. This
     * counts only nodes that cost Teef.
     */
    public int totalPurchasedRanks() {
        int total = 0;

        for (Map.Entry<String, Integer> entry : this.ranks.entrySet()) {
            PlayerOrkProgressionTree.Node node = PlayerOrkProgressionTree.node(entry.getKey());
            if (node == null || node.isEvolution()
                    || node.id().equals(PlayerOrkProgressionTree.ROOT_ID)) {
                continue;
            }
            total += entry.getValue();
        }

        return total;
    }

    /** How many distinct nodes he holds at least one rank in. */
    public int distinctNodes() {
        return this.ranks.size();
    }

    // ==================================================================== waaagh fury

    /**
     * Fury right now, worked out rather than stored.
     *
     * <p>The stored pair is "how much he had when he was last hit, and when that was". Everything
     * else is arithmetic, which is why an idle Ork — or a world with no Ork players at all — runs no
     * Fury code whatsoever.
     */
    public int fury(long gameTime) {
        if (this.furyAtLastCombat <= 0) {
            return 0;
        }

        long quiet = gameTime - this.lastCombatGameTime;
        if (quiet <= PlayerOrkProgressionBalance.FURY_CALM_TICKS) {
            return this.furyAtLastCombat;
        }

        long draining = quiet - PlayerOrkProgressionBalance.FURY_CALM_TICKS;
        int lost = (int) ((draining / 20L) * PlayerOrkProgressionBalance.FURY_DECAY_PER_SECOND);

        // DA GREENEST — the greenest git never fully calms down. Still pure arithmetic on the stored
        // pair, so the floor costs nothing: it is a different number in the same subtraction, not a
        // reason for anybody to start ticking.
        int floor = rank("da_greenest") > 0
                ? PlayerOrkProgressionBalance.DA_GREENEST_FURY_FLOOR : 0;

        return Math.max(floor, this.furyAtLastCombat - lost);
    }

    /** Adds Fury, rebasing the decay clock on this moment. */
    public void addFury(int amount, long gameTime) {
        int current = fury(gameTime);
        this.furyAtLastCombat = Math.min(PlayerOrkProgressionBalance.FURY_MAX,
                Math.max(0, current + amount));
        this.lastCombatGameTime = gameTime;
    }

    public void setFury(int value, long gameTime) {
        this.furyAtLastCombat = Math.max(0,
                Math.min(PlayerOrkProgressionBalance.FURY_MAX, value));
        this.lastCombatGameTime = gameTime;
    }

    /** True when enough time has passed to award Fury for dealt damage again. */
    public boolean mayAwardDamageFury(long gameTime) {
        return gameTime - this.lastFuryDamageTick
                >= PlayerOrkProgressionBalance.FURY_DAMAGE_COOLDOWN_TICKS;
    }

    public void markDamageFury(long gameTime) {
        this.lastFuryDamageTick = gameTime;
    }

    // ==================================================================== cooldowns

    public long waaaghReadyAt() {
        return this.waaaghReadyAt;
    }

    public void setWaaaghReadyAt(long time) {
        this.waaaghReadyAt = time;
    }

    public long headbuttReadyAt() {
        return this.headbuttReadyAt;
    }

    public void setHeadbuttReadyAt(long time) {
        this.headbuttReadyAt = time;
    }

    public long chargeReadyAt() {
        return this.chargeReadyAt;
    }

    public void setChargeReadyAt(long time) {
        this.chargeReadyAt = time;
    }

    public long lastStandReadyAt() {
        return this.lastStandReadyAt;
    }

    public void setLastStandReadyAt(long time) {
        this.lastStandReadyAt = time;
    }

    public long orderReadyAt() {
        return this.orderReadyAt;
    }

    public void setOrderReadyAt(long time) {
        this.orderReadyAt = time;
    }

    // ==================================================================== integrity

    /**
     * Makes the profile legal, whatever the disk said.
     *
     * <p>Run after every load. NBT is not a trusted source: a rank can name a node that no longer
     * exists, sit above a cap after a rebalance, or be negative because something wrote it wrong.
     * Repairing on read lets the rest of the code assume a sane profile instead of re-checking at
     * every use.
     */
    public void sanitize() {
        this.ranks.entrySet().removeIf(entry -> {
            PlayerOrkProgressionTree.Node node = PlayerOrkProgressionTree.node(entry.getKey());
            if (node == null) {
                // A node from another build. Dropped rather than kept, so it cannot satisfy a
                // prerequisite that no longer means anything.
                return true;
            }

            // An evolution is held or not held; there is no second rank of being a Nob.
            int cap = node.isEvolution() ? 1 : node.maxRank();
            int clamped = Math.max(0, Math.min(cap, entry.getValue()));

            if (clamped == 0) {
                return true;
            }

            entry.setValue(clamped);
            return false;
        });

        // Non-negotiable, and restored last so nothing above can have removed it.
        this.ranks.put(PlayerOrkProgressionTree.ROOT_ID, 1);

        this.krumpScore = Math.max(0, this.krumpScore);
        this.teef = Math.max(0, this.teef);
        this.totalTeefEarned = Math.max(0, this.totalTeefEarned);
        this.totalTeefSpent = Math.max(0, this.totalTeefSpent);
        this.validKills = Math.max(0, this.validKills);
        this.eliteKills = Math.max(0, this.eliteKills);
        this.imperialKills = Math.max(0, this.imperialKills);
        this.imperialCoresDestroyed = Math.max(0, this.imperialCoresDestroyed);
        this.majorVictories = Math.max(0, this.majorVictories);

        this.furyAtLastCombat = Math.max(0,
                Math.min(PlayerOrkProgressionBalance.FURY_MAX, this.furyAtLastCombat));

        if (this.stage == null) {
            this.stage = PlayerOrkEvolutionStage.ORK_BOY;
        }
    }

    /**
     * Back to a fresh Boy.
     *
     * <p>Assigns the fields directly rather than going through {@code spendTeef}, which was the old
     * reset command's mistake: spending the balance down to zero counted the whole lot as money the
     * player had spent, so a reset inflated the very total the Nob gate reads.
     */
    public void reset() {
        this.krumpScore = 0;
        this.teef = 0;
        this.totalTeefEarned = 0;
        this.totalTeefSpent = 0;

        this.validKills = 0;
        this.eliteKills = 0;
        this.imperialKills = 0;
        this.imperialCoresDestroyed = 0;
        this.majorVictories = 0;

        this.stage = PlayerOrkEvolutionStage.ORK_BOY;
        this.clan = null;

        this.ranks.clear();
        this.ranks.put(PlayerOrkProgressionTree.ROOT_ID, 1);

        this.furyAtLastCombat = 0;
        this.lastCombatGameTime = 0L;
        this.lastFuryDamageTick = 0L;

        this.waaaghReadyAt = 0L;
        this.headbuttReadyAt = 0L;
        this.chargeReadyAt = 0L;
        this.lastStandReadyAt = 0L;
        this.orderReadyAt = 0L;
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("Version", this.version);
        tag.putInt("Krump", this.krumpScore);
        tag.putInt("Teef", this.teef);
        tag.putInt("TeefEarned", this.totalTeefEarned);
        tag.putInt("TeefSpent", this.totalTeefSpent);

        tag.putInt("ValidKills", this.validKills);
        tag.putInt("EliteKills", this.eliteKills);
        tag.putInt("ImperialKills", this.imperialKills);
        tag.putInt("CoresDestroyed", this.imperialCoresDestroyed);
        tag.putInt("MajorVictories", this.majorVictories);

        tag.putString("Stage", this.stage.id());
        if (this.clan != null) {
            tag.putString("Clan", this.clan.name());
        }

        CompoundTag rankTag = new CompoundTag();
        this.ranks.forEach(rankTag::putInt);
        tag.put("OrkRanks", rankTag);

        tag.putInt("Fury", this.furyAtLastCombat);
        tag.putLong("LastCombat", this.lastCombatGameTime);
        tag.putLong("LastFuryDamage", this.lastFuryDamageTick);

        tag.putLong("WaaaghReady", this.waaaghReadyAt);
        tag.putLong("HeadbuttReady", this.headbuttReadyAt);
        tag.putLong("ChargeReady", this.chargeReadyAt);
        tag.putLong("LastStandReady", this.lastStandReadyAt);
        tag.putLong("OrderReady", this.orderReadyAt);

        return tag;
    }

    /**
     * Reads a profile back.
     *
     * <p>An empty tag is the ordinary case, not an error: every player who has ever logged in before
     * the Ork progression existed has one. They come back as a Boy with nothing, which is exactly
     * where an Ork starts, and nothing else about their save is touched.
     */
    public static PlayerOrkProgressionProfile load(CompoundTag tag) {
        PlayerOrkProgressionProfile profile = new PlayerOrkProgressionProfile();

        if (tag == null || tag.isEmpty()) {
            return profile;
        }

        profile.version = tag.getInt("Version");
        profile.krumpScore = tag.getInt("Krump");
        profile.teef = tag.getInt("Teef");
        profile.totalTeefEarned = tag.getInt("TeefEarned");
        profile.totalTeefSpent = tag.getInt("TeefSpent");

        profile.validKills = tag.getInt("ValidKills");
        profile.eliteKills = tag.getInt("EliteKills");
        profile.imperialKills = tag.getInt("ImperialKills");
        profile.imperialCoresDestroyed = tag.getInt("CoresDestroyed");
        profile.majorVictories = tag.getInt("MajorVictories");

        profile.stage = PlayerOrkEvolutionStage.byId(tag.getString("Stage"));
        profile.clan = tag.contains("Clan") ? OrkClan.fromName(tag.getString("Clan")) : null;

        CompoundTag rankTag = tag.getCompound("OrkRanks");
        for (String key : rankTag.getAllKeys()) {
            profile.ranks.put(key, rankTag.getInt(key));
        }

        profile.furyAtLastCombat = tag.getInt("Fury");
        profile.lastCombatGameTime = tag.getLong("LastCombat");
        profile.lastFuryDamageTick = tag.getLong("LastFuryDamage");

        profile.waaaghReadyAt = tag.getLong("WaaaghReady");
        profile.headbuttReadyAt = tag.getLong("HeadbuttReady");
        profile.chargeReadyAt = tag.getLong("ChargeReady");
        profile.lastStandReadyAt = tag.getLong("LastStandReady");
        profile.orderReadyAt = tag.getLong("OrderReady");

        // Never hand back a profile the rest of the code has to distrust.
        profile.sanitize();

        return profile;
    }
}
