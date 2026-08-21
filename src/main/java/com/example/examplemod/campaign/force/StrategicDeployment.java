package com.example.examplemod.campaign.force;

import com.example.examplemod.campaign.StrategicLocation;
import com.example.examplemod.campaign.war.WarFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A body of troops on its way to a sector.
 *
 * <h2>One type for both sides</h2>
 *
 * A player ordering an assault from the War Table and an Ork camp launching a raid are the same
 * thing seen from two directions: a strength leaves a place, takes time to arrive, and then presses
 * on a sector until it is used up. Writing them as one type is what stops the two from drifting —
 * an Ork raid that resolved by different rules from a player's attack would be a balance problem
 * nobody could reason about, because there would be no shared number to compare.
 *
 * <h2>Strength is a number until somebody is watching</h2>
 *
 * A deployment is {@link #strength} — no entities, no composition, no NBT for three hundred mobs.
 * It becomes real units only when it commits within sight of a player, and even then only a capped
 * handful; the rest stays arithmetic. The mod's existing strategic layer then absorbs whatever was
 * spawned once the player leaves, so the round trip already has an owner and this class does not
 * need to reinvent it.
 *
 * <p>{@link #materialisedStrength} is what was turned into bodies. It is subtracted from the
 * abstract strength at the moment of spawning, so the same troops can never be counted twice — once
 * as pressure and once as a mob standing on the field.
 */
public class StrategicDeployment {

    private final String id;
    private final ResourceLocation frontId;
    private final WarFaction faction;

    /** Where it set out from — a Command Core, an Ork camp. */
    private final StrategicLocation origin;

    /** The sector it is going to. */
    private final String targetSectorId;

    private int strength;
    private int materialisedStrength;

    private DeploymentState state = DeploymentState.MUSTERING;

    /** Game time this deployment moves to its next state. */
    private long nextStateTime;

    /** Game time it was created, for the War Table's ordering and the retirement sweep. */
    private final long issuedAt;

    /** True for a deployment a player ordered, so the log and the table can say who moved. */
    private final boolean playerOrdered;

    public StrategicDeployment(String id, ResourceLocation frontId, WarFaction faction,
                               StrategicLocation origin, String targetSectorId, int strength,
                               long issuedAt, long musterUntil, boolean playerOrdered) {
        this.id = id;
        this.frontId = frontId;
        this.faction = faction;
        this.origin = origin;
        this.targetSectorId = targetSectorId == null ? "" : targetSectorId;
        this.strength = Math.max(1, strength);
        this.issuedAt = issuedAt;
        this.nextStateTime = musterUntil;
        this.playerOrdered = playerOrdered;
    }

    // ====================================================================================
    // Reading
    // ====================================================================================

    public String id() {
        return this.id;
    }

    public ResourceLocation frontId() {
        return this.frontId;
    }

    public WarFaction faction() {
        return this.faction;
    }

    public StrategicLocation origin() {
        return this.origin;
    }

    public BlockPos originPos() {
        return this.origin.pos();
    }

    public String targetSectorId() {
        return this.targetSectorId;
    }

    public int strength() {
        return this.strength;
    }

    public int materialisedStrength() {
        return this.materialisedStrength;
    }

    public DeploymentState state() {
        return this.state;
    }

    public long nextStateTime() {
        return this.nextStateTime;
    }

    public long issuedAt() {
        return this.issuedAt;
    }

    public boolean playerOrdered() {
        return this.playerOrdered;
    }

    public boolean isActive() {
        return this.state.isActive();
    }

    /**
     * Contest points this deployment pushes per pass.
     *
     * <p>Only what is still abstract counts. Strength that was turned into mobs is on the field
     * fighting for itself, and counting it here as well would let a player's assault press twice as
     * hard for the crime of being watched.
     */
    public int pressure() {
        return this.state == DeploymentState.COMMITTED ? this.strength : 0;
    }

    // ====================================================================================
    // Writing
    // ====================================================================================

    /**
     * Advances the state if its clock has run out.
     *
     * @return the new state if it changed, otherwise null
     */
    public DeploymentState advanceIfDue(long gameTime, long moveTicks) {
        if (gameTime < this.nextStateTime || this.state == DeploymentState.SPENT) {
            return null;
        }

        return switch (this.state) {
            case MUSTERING -> {
                this.state = DeploymentState.MOVING;
                this.nextStateTime = gameTime + moveTicks;
                yield this.state;
            }
            case MOVING -> {
                this.state = DeploymentState.COMMITTED;
                // Committed deployments have no clock: they are spent by attrition, not by time.
                this.nextStateTime = Long.MAX_VALUE;
                yield this.state;
            }
            default -> null;
        };
    }

    /**
     * Spends part of the strength — the cost of a pass in contact.
     *
     * @return true when this exhausted the deployment
     */
    public boolean spend(int amount) {
        this.strength = Math.max(0, this.strength - Math.max(0, amount));

        // Spent when the ABSTRACT pool runs out, whatever was materialised.
        //
        // The earlier version also required materialisedStrength to be zero, and that number only
        // ever goes up: a deployment that put a single squad on the ground could therefore never
        // finish. It stayed COMMITTED forever, pressing for zero (pressure reads strength), and the
        // retirement sweep — which only takes inactive ones — never touched it. Every raid ever
        // launched would still be on the books.
        //
        // Materialised troops are ordinary mobs by then. They belong to the world and to the
        // performance layer's absorption sweep, not to this record.
        if (this.strength <= 0) {
            this.state = DeploymentState.SPENT;
            return true;
        }

        return false;
    }

    /**
     * Moves strength out of the abstract pool and into bodies.
     *
     * @return how much was actually converted, which is less than asked for when the pool is short
     */
    public int materialise(int amount) {
        int taken = Math.min(this.strength, Math.max(0, amount));

        this.strength -= taken;
        this.materialisedStrength += taken;

        return taken;
    }

    /**
     * Gives materialised strength back to the abstract pool.
     *
     * <p>For a deployment whose spawned units were absorbed again, or whose materialisation failed.
     * Without this the strength would simply evaporate — the exact failure the performance layer's
     * own notes warn about, where troops vanish for a reason no player could understand.
     */
    public void demateralise() {
        this.strength += this.materialisedStrength;
        this.materialisedStrength = 0;
    }

    public void markSpent() {
        this.state = DeploymentState.SPENT;
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Front", this.frontId.toString());
        tag.putString("Faction", this.faction.name());
        tag.putString("Target", this.targetSectorId);
        tag.putInt("Strength", this.strength);
        tag.putInt("Materialised", this.materialisedStrength);
        tag.putString("State", this.state.name());
        tag.putLong("Next", this.nextStateTime);
        tag.putLong("Issued", this.issuedAt);
        tag.putBoolean("Player", this.playerOrdered);
        this.origin.saveInto(tag, "Origin");
        return tag;
    }

    /** @return null for a tag naming a front this installation no longer has */
    public static StrategicDeployment load(CompoundTag tag) {
        ResourceLocation front = ResourceLocation.tryParse(tag.getString("Front"));

        if (front == null) {
            return null;
        }

        StrategicDeployment deployment = new StrategicDeployment(
                tag.getString("Id"),
                front,
                WarFaction.fromName(tag.getString("Faction")),
                StrategicLocation.load(tag, "Origin"),
                tag.getString("Target"),
                tag.getInt("Strength"),
                tag.getLong("Issued"),
                tag.getLong("Next"),
                tag.getBoolean("Player"));

        deployment.materialisedStrength = tag.getInt("Materialised");
        deployment.state = DeploymentState.fromName(tag.getString("State"));

        return deployment;
    }

    /** {@code ORKS 24 -> armageddon.manufactorum  MOVING} — the command's and the table's line. */
    public String shortText() {
        return this.faction.name() + " " + this.strength
                + (this.materialisedStrength > 0 ? " (+" + this.materialisedStrength + " no campo)" : "")
                + " -> " + this.targetSectorId
                + "  " + this.state.name()
                + (this.playerOrdered ? "  [ordem]" : "");
    }
}
