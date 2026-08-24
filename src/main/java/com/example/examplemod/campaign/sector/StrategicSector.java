package com.example.examplemod.campaign.sector;

import com.example.examplemod.campaign.StrategicLocation;
import com.example.examplemod.campaign.war.WarFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One piece of ground worth fighting for, and who currently has it.
 *
 * <h2>Contest, not a coin flip</h2>
 *
 * Ownership does not change the moment the other side has more units nearby. A sector carries a
 * {@link #contest} score in [-100, 100]: negative pulls toward the Orks, positive toward the
 * Imperium, and the owner only changes when it crosses {@link #CAPTURE_THRESHOLD} at the far end.
 * That band is the whole reason a front line can exist. Without it, a sector between two bases
 * flips every strategic pass forever, the war log fills with the same two lines, and "who holds
 * Armageddon" becomes a number that means nothing because it never settles.
 *
 * <p>The score moves by pressure — how much force each side has near the sector — divided by the
 * sector's {@link SectorType#defence()}. A fortress therefore takes many passes of sustained
 * pressure to fall and falls back the moment the pressure stops; an artillery position changes
 * hands almost as fast as somebody walks onto it.
 */
public class StrategicSector {

    /** Contest score at which the sector changes hands. */
    public static final int CAPTURE_THRESHOLD = 100;

    /** Below this much |contest|, a sector held by nobody is genuinely contested ground. */
    public static final int CONTESTED_BAND = 25;

    private final String id;
    private final StrategicLocation location;
    private final SectorType type;

    private WarFaction owner;
    private int contest;

    /** True while both sides have force on it. Drives the "contested" slice of planetary control. */
    private boolean disputed;

    /** Game time of the last ownership change, so the War Table can say "taken 3 days ago". */
    private long lastChangeTime;

    public StrategicSector(String id, StrategicLocation location, SectorType type, WarFaction owner) {
        this.id = id;
        this.location = location;
        this.type = type;
        this.owner = owner == null ? WarFaction.NEUTRAL : owner;
    }

    // ====================================================================================
    // Identity
    // ====================================================================================

    /** {@code armageddon.manufactorum} — unique across every front. */
    public String id() {
        return this.id;
    }

    public StrategicLocation location() {
        return this.location;
    }

    public BlockPos pos() {
        return this.location.pos();
    }

    public ResourceKey<Level> dimension() {
        return this.location.dimension();
    }

    public SectorType type() {
        return this.type;
    }

    public Component displayName() {
        return this.type.displayName();
    }

    public int importance() {
        return this.type.importance();
    }

    // ====================================================================================
    // Ownership
    // ====================================================================================

    public WarFaction owner() {
        return this.owner;
    }

    public int contest() {
        return this.contest;
    }

    public boolean isDisputed() {
        return this.disputed;
    }

    public long lastChangeTime() {
        return this.lastChangeTime;
    }

    public void setDisputed(boolean value) {
        this.disputed = value;
    }

    /**
     * Applies one pass of pressure.
     *
     * @param pressure positive toward the Imperium, negative toward the Orks; already scaled by the
     *                 caller for how much force is actually present
     * @return the previous owner if this pass changed hands, otherwise null
     */
    public WarFaction applyPressure(int pressure, long gameTime) {
        // The Orks are the default attacker because settlement pressure — the caller this was
        // written for — is Ork camps against Imperial cities.
        return applyPressure(pressure, gameTime, WarFaction.ORKS);
    }

    /**
     * The same, naming which enemy the negative end hands ground to.
     *
     * <p>Added when the Necron awakening started pushing on its own planet. Before that the negative
     * end was hard-coded to the Orks — correct while they were the only enemy that pushed, and
     * quietly wrong the moment another one did: a waking tomb would have handed its own landing zone
     * to a WAAAGH! that does not exist, on a world with no Orks on it.
     *
     * @param attacker who takes the sector if the contest reaches the negative threshold
     */
    public WarFaction applyPressure(int pressure, long gameTime, WarFaction attacker) {
        if (pressure == 0) {
            // No one is pushing: the ground settles back toward its holder rather than freezing
            // wherever the last fight left it. This is what lets a line that was nearly broken
            // recover once the attack is beaten off.
            this.contest -= Integer.signum(this.contest);
            return null;
        }

        int resistance = Math.max(1, this.type.defence());
        int applied = pressure > 0
                ? Math.max(1, pressure / resistance)
                : Math.min(-1, pressure / resistance);

        this.contest = Math.max(-CAPTURE_THRESHOLD, Math.min(CAPTURE_THRESHOLD, this.contest + applied));

        if (this.contest >= CAPTURE_THRESHOLD && this.owner != WarFaction.IMPERIUM) {
            return changeOwner(WarFaction.IMPERIUM, gameTime);
        }

        // The guard is "not already an enemy" rather than "not the attacker": a sector sitting at
        // full negative contest would otherwise be handed back and forth between the two enemy
        // factions every pass, on a planet where only one of them is actually pushing.
        if (this.contest <= -CAPTURE_THRESHOLD && !this.owner.isEnemyOfImperium()) {
            return changeOwner(attacker, gameTime);
        }

        return null;
    }

    /**
     * Hands the sector to a faction outright — a command, a scripted objective, a stronghold being
     * destroyed. The contest score is reset to that side's edge of the band rather than to the
     * middle, so a sector just taken is not immediately winnable back by one pass of pressure.
     *
     * @return the previous owner if this changed anything, otherwise null
     */
    public WarFaction setOwner(WarFaction faction, long gameTime) {
        if (faction == null || faction == this.owner) {
            return null;
        }

        return changeOwner(faction, gameTime);
    }

    private WarFaction changeOwner(WarFaction faction, long gameTime) {
        WarFaction previous = this.owner;

        this.owner = faction;
        this.lastChangeTime = gameTime;

        // Reset to just inside the band on the new owner's side. Leaving it pinned at the threshold
        // would mean a single point of enemy pressure flips it straight back.
        this.contest = switch (faction) {
            case IMPERIUM -> CAPTURE_THRESHOLD / 2;
            case ORKS, NECRONS -> -CAPTURE_THRESHOLD / 2;
            default -> 0;
        };

        return previous;
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Type", this.type.name());
        tag.putString("Owner", this.owner.name());
        tag.putInt("Contest", this.contest);
        tag.putBoolean("Disputed", this.disputed);
        tag.putLong("Changed", this.lastChangeTime);
        this.location.saveInto(tag, "Loc");
        return tag;
    }

    public static StrategicSector load(CompoundTag tag) {
        StrategicSector sector = new StrategicSector(
                tag.getString("Id"),
                StrategicLocation.load(tag, "Loc"),
                SectorType.fromName(tag.getString("Type")),
                WarFaction.fromName(tag.getString("Owner")));

        sector.contest = tag.getInt("Contest");
        sector.disputed = tag.getBoolean("Disputed");
        sector.lastChangeTime = tag.getLong("Changed");

        return sector;
    }

    /** {@code MANUFACTORUM  IMPERIUM  +40  armageddon [120, 64, -340]} — the debug command's line. */
    public String shortText() {
        return this.type.name() + "  " + this.owner.name()
                + "  " + (this.contest >= 0 ? "+" : "") + this.contest
                + (this.disputed ? "  (disputado)" : "")
                + "  " + this.location.shortText();
    }
}
