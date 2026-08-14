package com.example.examplemod.performance.strategic;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A battle nobody is watching, resolved as arithmetic instead of as three hundred entities.
 *
 * <h2>Why this exists at all</h2>
 *
 * The AI level-of-detail work made distant units cheap, and measuring it exposed the flaw in that
 * approach: at STRATEGIC cadence a hundred-a-side fight produced eight casualties in thirty seconds
 * where a full-rate one produced about a hundred. Cheap, but the war stops happening. This class is
 * the answer to that — a distant battle is not slowed down, it is <i>resolved</i>, at a rate that
 * has nothing to do with how often anybody's pathfinder runs.
 *
 * <h2>The model</h2>
 *
 * Each step, both sides deal damage proportional to their {@link FCStrategicForce#offence()}, and
 * losses land as whole dead units plus wounds on the survivors. Three things bend the result, all of
 * them from the brief's list: a defender takes a configured discount for holding the ground, a
 * commander halves how fast morale falls, and morale itself scales a force's output — so a side that
 * starts losing loses faster, which is how real formations come apart. When a side's morale hits
 * zero it has broken, and the battle is over.
 *
 * <p>It is deliberately not a simulation of individual shots. It cannot be: the whole point is that
 * no shot is being fired anywhere. What it has to be is <i>plausible and finite</i> — a battle that
 * a player walks away from must reach a believable answer in a believable time.
 */
public final class FCStrategicBattle {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final BlockPos center;
    private final List<FCStrategicForce> forces = new ArrayList<>();

    /** Unit counts when the battle began, the yardstick morale erosion is measured against. */
    private final List<Integer> startingStrengths = new ArrayList<>();

    /** Ticks since a player was last close enough to matter. Drives dematerialisation hysteresis. */
    private int ticksWithoutPlayer;

    /** Ticks until the next resolution step. */
    private int stepCountdown;

    /**
     * Consecutive failed placements during materialisation. Widens the scatter radius and, at the
     * configured ceiling, hands the battle back to the strategic layer.
     *
     * <p>Deliberately not saved. A world that restarts mid-materialisation resumes with a clean
     * count, which errs towards trying again — the safe direction, since the failure mode being
     * guarded against is losing troops, not spending a few extra ticks looking for floor space.
     */
    private int spawnFailures;

    /**
     * Ticks before a stalled battle may try to materialise again.
     *
     * <p>Without it, a battle that stalls is put straight back on the strategic list, where the
     * very next tick sees the same nearby player and queues it again — spending its whole attempt
     * budget every tick for as long as somebody stands there. One strategic scan interval is the
     * natural pause: it is already the cadence at which this layer re-examines the world, and the
     * obstruction that caused the stall is not going to move faster than that.
     */
    private int materialiseRetryCooldown;

    /** Whether this battle is allowed to be queued for materialisation again yet. */
    public boolean readyToMaterialise() {
        return this.materialiseRetryCooldown <= 0;
    }

    /** Counts down the retry pause. Called once per tick for every battle on the strategic list. */
    public void tickRetryCooldown() {
        if (this.materialiseRetryCooldown > 0) {
            this.materialiseRetryCooldown--;
        }
    }

    /** Starts the retry pause after a stalled materialisation. */
    public void noteMaterialiseStalled(int cooldownTicks) {
        this.materialiseRetryCooldown = Math.max(0, cooldownTicks);
    }

    public FCStrategicBattle(BlockPos center) {
        this.center = center;
    }

    public BlockPos center() {
        return this.center;
    }

    public List<FCStrategicForce> forces() {
        return this.forces;
    }

    public int ticksWithoutPlayer() {
        return this.ticksWithoutPlayer;
    }

    public void noteWatched() {
        this.ticksWithoutPlayer = 0;
    }

    public void noteUnwatched(int ticks) {
        this.ticksWithoutPlayer += ticks;
    }

    public void addForce(FCStrategicForce force) {
        this.forces.add(force);
        this.startingStrengths.add(Math.max(1, force.totalUnits()));
    }

    public int totalUnits() {
        int total = 0;
        for (FCStrategicForce force : this.forces) {
            total += force.totalUnits();
        }
        return total;
    }

    /** True once one side or fewer is left standing. */
    public boolean isResolved() {
        int standing = 0;
        for (FCStrategicForce force : this.forces) {
            if (!force.isBroken()) {
                standing++;
            }
        }
        return standing <= 1;
    }

    /**
     * Advances the battle. Call every tick; it rations itself to the configured step interval so the
     * cost of a strategic battle is a few multiplications per second, whatever its size.
     */
    public void tick() {
        if (this.stepCountdown > 0) {
            this.stepCountdown--;
            return;
        }

        this.stepCountdown = FirstCrusadePerformanceConfig.strategicStepTicks();
        resolveStep();
    }

    private void resolveStep() {
        if (this.forces.size() < 2 || isResolved()) {
            return;
        }

        double rate = FirstCrusadePerformanceConfig.strategicDamageRate();
        double defenceDiscount = FirstCrusadePerformanceConfig.strategicDefenceAdvantage();

        // Damage is computed for everyone before anything is applied, so the side that happens to be
        // first in the list does not get a free strike every single step.
        double[] incoming = new double[this.forces.size()];

        for (int attacker = 0; attacker < this.forces.size(); attacker++) {
            FCStrategicForce attackingForce = this.forces.get(attacker);
            if (attackingForce.isBroken()) {
                continue;
            }

            double output = attackingForce.offence() * rate;

            for (int defender = 0; defender < this.forces.size(); defender++) {
                if (defender == attacker) {
                    continue;
                }

                FCStrategicForce defendingForce = this.forces.get(defender);
                if (defendingForce.isBroken()) {
                    continue;
                }

                double share = output / Math.max(1, this.forces.size() - 1);
                incoming[defender] += defendingForce.isDefending() ? share * defenceDiscount : share;
            }
        }

        for (int i = 0; i < this.forces.size(); i++) {
            FCStrategicForce force = this.forces.get(i);
            if (incoming[i] <= 0.0D) {
                continue;
            }

            int killed = force.applyDamage(incoming[i]);
            force.erodeMorale(killed, this.startingStrengths.get(i));
        }
    }

    /** How a materialisation batch ended. */
    public enum MaterialiseResult {
        /** Everything is back in the world; the battle is finished with. */
        DONE,
        /** Some units are placed, more remain. Call again next tick. */
        IN_PROGRESS,
        /**
         * Nowhere to put anybody right now. The battle goes back to the strategic list with its
         * remaining units intact, to be tried again when a player next comes close.
         */
        STALLED
    }

    /**
     * Puts one batch of this battle's units back into the world.
     *
     * <p>Spawning three hundred entities in a single tick is a guaranteed stall, so materialisation
     * takes as many ticks as it needs — the caller comes back every tick until this reports
     * {@link MaterialiseResult#DONE}.
     *
     * <h2>A failed placement is not a casualty</h2>
     *
     * <p>This used to decrement the stack whether or not the unit found room, on the reasoning that
     * an entry which retries forever would hang materialisation. The reasoning was right about the
     * hazard and wrong about the cure: it meant a battle whose centre had drifted inside a building,
     * under a cliff or into an unloaded chunk silently deleted an entire army, and reported nothing.
     * The unit now stays in its stack until it is genuinely standing in the level, and the runaway
     * case is bounded by an attempt budget per tick plus a failure ceiling that hands the battle
     * back to the strategic layer rather than to the void.
     */
    public MaterialiseResult materialiseBatch(ServerLevel level, int batchSize) {
        int spawned = 0;
        int attempts = 0;
        int attemptCap = FirstCrusadePerformanceConfig.strategicMaxSpawnAttempts();

        for (FCStrategicForce force : this.forces) {
            List<FCStrategicForce.Stack> stacks = force.stacks();

            while (!stacks.isEmpty() && spawned < batchSize && attempts < attemptCap) {
                FCStrategicForce.Stack stack = stacks.get(0);

                if (stack.count() <= 0) {
                    stacks.remove(0);
                    continue;
                }

                attempts++;

                switch (placeOne(level, stack)) {
                    case PLACED -> {
                        spawned++;
                        this.spawnFailures = 0;
                        stack.placedOne();
                        if (stack.count() <= 0) {
                            stacks.remove(0);
                        }
                    }
                    case NO_ROOM -> {
                        // Keep the unit. Next attempt searches a wider radius; if this tick's budget
                        // runs out, the next tick carries on from the same stack.
                        this.spawnFailures++;
                        if (this.spawnFailures >= FirstCrusadePerformanceConfig.strategicMaxSpawnFailures()) {
                            LOGGER.warn("[first crusade] strategic battle at {} could not place a {}"
                                            + " after {} attempts; returning {} units to the strategic"
                                            + " layer to retry later",
                                    this.center, stack.type(), this.spawnFailures, totalUnits());
                            this.spawnFailures = 0;
                            return MaterialiseResult.STALLED;
                        }
                    }
                    case IMPOSSIBLE -> {
                        // The entity type itself refuses to be constructed — a removed or broken
                        // registration. No radius will fix that, so retrying it forever would jam
                        // every unit queued behind it. Dropped loudly, never silently.
                        LOGGER.error("[first crusade] entity type {} could not be created; dropping"
                                        + " {} abstracted unit(s) of it from the battle at {}",
                                stack.type(), stack.count(), this.center);
                        stacks.remove(0);
                    }
                }
            }
        }

        return totalUnits() <= 0 ? MaterialiseResult.DONE : MaterialiseResult.IN_PROGRESS;
    }

    /** The outcome of one attempt to put a single unit on the ground. */
    private enum PlacementOutcome {
        PLACED,
        NO_ROOM,
        IMPOSSIBLE
    }

    /**
     * Tries once to stand one unit of a stack somewhere near the battle centre.
     *
     * <p>The scatter radius widens with {@link #spawnFailures}, so a centre that happens to sit
     * inside a bunker wall walks its way out to open ground instead of failing identically forever.
     */
    private PlacementOutcome placeOne(ServerLevel level, FCStrategicForce.Stack stack) {
        EntityType<?> type = stack.type();

        int spread = Math.min(
                FirstCrusadePerformanceConfig.strategicMaxSpawnSpread(),
                FirstCrusadePerformanceConfig.strategicSpawnSpread()
                        + this.spawnFailures * FirstCrusadePerformanceConfig.strategicSpawnRadiusExpansion());

        int x = this.center.getX() + level.random.nextInt(spread * 2 + 1) - spread;
        int z = this.center.getZ() + level.random.nextInt(spread * 2 + 1) - spread;

        // Before getHeight, which would otherwise load the chunk synchronously just to find out
        // that nobody is meant to be standing there yet.
        if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return PlacementOutcome.NO_ROOM;
        }

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        Entity entity = type.create(level);
        if (entity == null) {
            return PlacementOutcome.IMPOSSIBLE;
        }

        entity.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);

        // The check that gives the retry something to find: without it every attempt "succeeded"
        // and units were posted into walls. moveTo first, so the box being tested is the box the
        // unit would actually occupy.
        if (!level.noCollision(entity)) {
            return PlacementOutcome.NO_ROOM;
        }

        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.EVENT, null, null);
        }

        if (stack.name() != null) {
            entity.setCustomName(net.minecraft.network.chat.Component.literal(stack.name()));
            entity.setCustomNameVisible(true);
        }

        if (entity instanceof LivingEntity living) {
            // Restore the wear the battle put on it, so a force that materialises after a hard fight
            // looks like one: the survivors come back bloodied, not freshly issued.
            living.setHealth(Math.max(1.0F, living.getMaxHealth() * stack.health()));
        }

        return level.addFreshEntity(entity) ? PlacementOutcome.PLACED : PlacementOutcome.NO_ROOM;
    }

    // ==================================================================== persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", this.center.getX());
        tag.putInt("Y", this.center.getY());
        tag.putInt("Z", this.center.getZ());
        tag.putInt("Unwatched", this.ticksWithoutPlayer);

        ListTag forceList = new ListTag();
        ListTag strengthList = new ListTag();

        for (int i = 0; i < this.forces.size(); i++) {
            forceList.add(this.forces.get(i).save());

            CompoundTag strength = new CompoundTag();
            strength.putInt("Value", this.startingStrengths.get(i));
            strengthList.add(strength);
        }

        tag.put("Forces", forceList);
        tag.put("Starting", strengthList);
        return tag;
    }

    public static FCStrategicBattle load(CompoundTag tag) {
        FCStrategicBattle battle = new FCStrategicBattle(
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")));
        battle.ticksWithoutPlayer = tag.getInt("Unwatched");

        ListTag forceList = tag.getList("Forces", Tag.TAG_COMPOUND);
        ListTag strengthList = tag.getList("Starting", Tag.TAG_COMPOUND);

        for (int i = 0; i < forceList.size(); i++) {
            battle.forces.add(FCStrategicForce.load(forceList.getCompound(i)));

            int starting = i < strengthList.size() ? strengthList.getCompound(i).getInt("Value") : 1;
            battle.startingStrengths.add(Math.max(1, starting));
        }

        return battle;
    }

    /** A one-line summary for the debug command. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append('[').append(this.center.getX()).append(", ").append(this.center.getZ()).append("] ");

        for (int i = 0; i < this.forces.size(); i++) {
            FCStrategicForce force = this.forces.get(i);
            if (i > 0) {
                text.append(" vs ");
            }
            text.append(force.faction().name()).append(' ').append(force.totalUnits())
                    .append(" (moral ").append(String.format("%.0f%%", force.morale() * 100.0F)).append(')');
        }

        return text.toString();
    }
}
