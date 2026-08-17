package com.example.examplemod.performance.strategic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.example.examplemod.FirstCrusadeFaction;
import com.example.examplemod.FirstCrusadeFactionManager;
import com.example.examplemod.WorldWarMapData;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;
import com.example.examplemod.unit.profile.FCUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The registry of battles that are being fought as numbers, and the rules for crossing between
 * numbers and entities.
 *
 * <h2>Saved, not cached</h2>
 *
 * This is {@link SavedData} because it holds armies. A cache may be thrown away; an army may not. If
 * a server stops while two hundred Guardsmen are abstracted, they have to be there when it comes
 * back — so absorption marks the data dirty in the same breath that it removes the entities, and
 * never the other way round.
 *
 * <h2>What is safe to absorb</h2>
 *
 * Three gates, and all three must pass:
 * <ul>
 *   <li><b>It is a battle.</b> Two mutually hostile factions in contact, at least
 *       {@code strategic.minUnits} bodies between them. A lone garrison is never absorbed, however
 *       far from a player it sits — a base with nobody attacking it is not a battle, and swallowing
 *       it would make a player's troops vanish for no reason they could understand.</li>
 *   <li><b>Nobody is watching.</b> No player within {@code dematerialiseDistance}, which is larger
 *       than the distance that brings a battle back.</li>
 *   <li><b>Nobody has been watching for a while.</b> {@code dematerialiseDelayTicks} of that being
 *       continuously true. Distance alone flickers when a player circles at the boundary; the delay
 *       is what makes the boundary quiet.</li>
 * </ul>
 *
 * <p>What is <b>not</b> preserved: inventories, squad membership and guard-post assignments. Type,
 * health and display name survive; everything else is rebuilt by the unit's own constructor when it
 * comes back. That is the deliberate trade for not writing three hundred entities' NBT to disk.
 */
public class FCStrategicBattleData extends SavedData {

    private static final String NAME = "firstcrusade_strategic_battles";

    private final List<FCStrategicBattle> battles = new ArrayList<>();

    /** Ticks until the next absorption sweep of this level. */
    private int scanCountdown;

    /** Battles currently being turned back into entities, spawned a batch per tick. */
    private final List<FCStrategicBattle> materialising = new ArrayList<>();

    /**
     * How many times a materialisation has given up and handed its units back to the strategic
     * layer. Runtime only, and reported by {@code /fc perf strategic}.
     *
     * <p>Worth surfacing because a non-zero value is the only outward sign that battles are landing
     * somewhere they cannot be placed — terrain that changed under them, a centre inside a
     * structure. It used to be invisible for the worst possible reason: the units were deleted
     * instead, so there was nothing left to notice.
     */
    private int failedMaterialisations;

    /** Materialisations that stalled and returned their units to the strategic layer. */
    public int failedMaterialisations() {
        return this.failedMaterialisations;
    }

    /**
     * How long each candidate battle has gone unwatched, keyed by where it was found. Runtime only:
     * a world that restarts simply starts the delay again, which errs towards keeping troops
     * physical — the safe direction to err in.
     *
     * <p>Keyed by position rather than by grid cell so that a battle drifting a few blocks between
     * sweeps keeps its accumulated patience instead of silently restarting the clock every time.
     */
    private final Map<BlockPos, Integer> candidates = new HashMap<>();

    /**
     * What the last sweep saw, gate by gate.
     *
     * <p>Not decoration. A sweep that absorbs nothing has at least six possible reasons, and a debug
     * command that answers "0" without saying which one is a command that costs more time than it
     * saves — which is exactly what happened while this class was being written.
     */
    private String lastSweepReport = "nenhuma varredura ainda";

    public String lastSweepReport() {
        return this.lastSweepReport;
    }

    public static FCStrategicBattleData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                FCStrategicBattleData::load, FCStrategicBattleData::new, NAME);
    }

    public List<FCStrategicBattle> battles() {
        return this.battles;
    }

    public int materialisingCount() {
        return this.materialising.size();
    }

    /** Adds a ready-made battle. Used by the debug command and by absorption. */
    public void addBattle(FCStrategicBattle battle) {
        this.battles.add(battle);
        setDirty();
    }

    // ==================================================================== the tick

    public void tick(ServerLevel level) {
        if (!FirstCrusadePerformanceConfig.strategicEnabled()) {
            return;
        }

        tickMaterialising(level);
        tickBattles(level);

        if (this.scanCountdown > 0) {
            this.scanCountdown--;
        } else {
            this.scanCountdown = FirstCrusadePerformanceConfig.strategicScanInterval();
            sweepForBattles(level);
        }
    }

    private void tickMaterialising(ServerLevel level) {
        if (this.materialising.isEmpty()) {
            return;
        }

        int batch = FirstCrusadePerformanceConfig.strategicBatchSize();
        Iterator<FCStrategicBattle> iterator = this.materialising.iterator();

        while (iterator.hasNext()) {
            FCStrategicBattle battle = iterator.next();

            switch (battle.materialiseBatch(level, batch)) {
                case DONE -> iterator.remove();
                case IN_PROGRESS -> {
                    // Nothing to do: it stays in the queue and continues next tick.
                }
                case STALLED -> {
                    // Nowhere to put the survivors right now — the centre is walled in, or the
                    // chunks are not loaded yet. The units are NOT lost: the battle goes back to
                    // the strategic list still holding them, and materialisation is attempted again
                    // the next time a player is close enough to ask for it.
                    iterator.remove();
                    battle.noteMaterialiseStalled(
                            FirstCrusadePerformanceConfig.strategicScanInterval());
                    this.battles.add(battle);
                    this.failedMaterialisations++;
                }
            }

            // One battle's batch per tick. Two armies arriving at once would defeat the point of
            // batching in the first place.
            break;
        }

        setDirty();
    }

    private void tickBattles(ServerLevel level) {
        double materialiseDistance = FirstCrusadePerformanceConfig.strategicMaterialiseDistance();
        Iterator<FCStrategicBattle> iterator = this.battles.iterator();
        boolean changed = false;

        while (iterator.hasNext()) {
            FCStrategicBattle battle = iterator.next();

            // Only an empty battle is deleted. A battle that has been *decided* keeps its winner:
            // those units are still an army standing on that ground, and the player who walks over
            // there has to find them. Removing a resolved battle would quietly delete whichever side
            // won it — the exact failure the brief's rule about converting troops properly before
            // removing them is meant to prevent.
            if (battle.totalUnits() <= 0) {
                iterator.remove();
                changed = true;
                continue;
            }

            battle.tickRetryCooldown();

            if (battle.readyToMaterialise()
                    && nearestPlayerDistance(level, battle.center()) <= materialiseDistance) {
                iterator.remove();
                this.materialising.add(battle);
                changed = true;
                continue;
            }

            if (!battle.isResolved()) {
                battle.tick();
                changed = true;
            }
        }

        if (changed) {
            setDirty();
        }
    }

    /**
     * Runs an absorption sweep immediately, ignoring the interval.
     *
     * <p>Exists for {@code /fc strategic sweep}. Waiting out the scan interval to find out whether
     * absorption works is how a bug in it stays hidden: the hysteresis delay alone is ten seconds,
     * and nobody tests a path they have to stand still for.
     */
    public void forceSweep(ServerLevel level) {
        sweepForBattles(level);
    }

    // ==================================================================== absorption

    /**
     * Looks for physical battles that nobody is near and turns them into numbers.
     *
     * <p>One pass over the level's entities, at {@code strategic.scanIntervalTicks}, then grouped by
     * growing a cluster around a seed unit.
     *
     * <p><b>Why not a grid.</b> The first version of this bucketed units into fixed cells, which is
     * cheaper and wrong: two armies meeting along a cell boundary land in different cells, each
     * holding exactly one faction, and neither is ever recognised as a battle. That is not an edge
     * case — a battle line <i>is</i> a boundary, and the test that caught it had the two sides
     * straddling x=1200 with a 48-block grid. Seeding costs more comparisons and cannot split a
     * battle in half.
     */
    private void sweepForBattles(ServerLevel level) {
        List<Mob> pool = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && canAbsorb(mob)) {
                pool.add(mob);
            }
        }

        int minUnits = FirstCrusadePerformanceConfig.strategicMinUnits();
        int poolSize = pool.size();

        if (poolSize < minUnits) {
            this.candidates.clear();
            this.lastSweepReport = "unidades absorviveis=" + poolSize + " < minUnits=" + minUnits;
            return;
        }

        double radius = FirstCrusadePerformanceConfig.strategicClusterRadius();
        double radiusSq = radius * radius;
        List<BlockPos> seenThisSweep = new ArrayList<>();
        int clusters = 0;
        int biggestCluster = 0;
        this.lastRejection = "nenhum agrupamento avaliado";

        while (!pool.isEmpty()) {
            Mob seed = pool.remove(pool.size() - 1);
            List<Mob> cluster = new ArrayList<>();
            cluster.add(seed);

            // Grown against the seed rather than transitively: a chain of units two blocks apart
            // could otherwise link two genuinely separate battles across half a continent.
            Iterator<Mob> rest = pool.iterator();
            while (rest.hasNext()) {
                Mob candidate = rest.next();
                if (candidate.distanceToSqr(seed) <= radiusSq) {
                    cluster.add(candidate);
                    rest.remove();
                }
            }

            clusters++;
            biggestCluster = Math.max(biggestCluster, cluster.size());

            BlockPos centre = tryAbsorbCluster(level, cluster);
            if (centre != null) {
                seenThisSweep.add(centre);
            }
        }

        this.lastSweepReport = "absorviveis=" + poolSize + " agrupamentos=" + clusters
                + " maior=" + biggestCluster + " -> " + this.lastRejection;

        // A candidate that did not appear this sweep has moved, died or materialised; its patience
        // counter must not survive to let a future battle skip the delay.
        this.candidates.keySet().removeIf(tracked -> {
            for (BlockPos seen : seenThisSweep) {
                if (seen.closerThan(tracked, radius)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Turns one cluster into a battle if all three gates pass.
     *
     * @return where the cluster was, if it is a live candidate worth remembering between
     *         sweeps; null when it is not a battle at all
     */
    /** Why the last cluster was not absorbed. Written by every early return below. */
    private String lastRejection = "";

    private BlockPos tryAbsorbCluster(ServerLevel level, List<Mob> occupants) {
        if (occupants.size() < FirstCrusadePerformanceConfig.strategicMinUnits()) {
            this.lastRejection = "agrupamento de " + occupants.size() + " < minUnits="
                    + FirstCrusadePerformanceConfig.strategicMinUnits();
            return null;
        }

        // One representative per faction. Hostility is a property of factions, not of individuals,
        // so testing every pair of three hundred mobs would be ninety thousand checks to answer a
        // question that four representatives settle.
        Map<FirstCrusadeFaction, Mob> representatives = new HashMap<>();
        for (Mob mob : occupants) {
            representatives.putIfAbsent(FirstCrusadeFactionManager.getFaction(mob), mob);
        }

        if (representatives.size() < 2) {
            this.lastRejection = "so uma faccao presente (" + representatives.keySet() + ")";
            return null;
        }

        boolean hostilePairFound = false;
        for (Mob first : representatives.values()) {
            for (Mob second : representatives.values()) {
                if (FirstCrusadeFactionManager.canAttack(first, second)) {
                    hostilePairFound = true;
                    break;
                }
            }
            if (hostilePairFound) {
                break;
            }
        }

        // Orks and vanilla monsters can share a field without either being able to attack the other.
        // That is a crowd, not a battle, and abstracting it would freeze two idle mobs into a fight
        // that never resolves.
        if (!hostilePairFound) {
            this.lastRejection = "faccoes presentes nao sao hosteis entre si ("
                    + representatives.keySet() + ")";
            return null;
        }

        Map<FirstCrusadeFaction, FCStrategicForce> sides = new HashMap<>();
        for (FirstCrusadeFaction faction : representatives.keySet()) {
            sides.put(faction, new FCStrategicForce(faction));
        }

        BlockPos centre = occupants.get(0).blockPosition();

        double dematerialiseDistance = FirstCrusadePerformanceConfig.strategicDematerialiseDistance();
        if (nearestPlayerDistance(level, centre) <= dematerialiseDistance) {
            // Somebody is close enough to see it. Forget any patience it had built up.
            this.candidates.remove(matchKey(centre));
            this.lastRejection = "jogador a "
                    + Math.round(nearestPlayerDistance(level, centre)) + " blocos (limite "
                    + Math.round(dematerialiseDistance) + ")";
            return null;
        }

        // The delay half of the hysteresis: a battle has to STAY unwatched, not merely be unwatched
        // at the instant the sweep happened to look at it.
        BlockPos key = matchKey(centre);
        int unwatched = this.candidates.merge(key,
                FirstCrusadePerformanceConfig.strategicScanInterval(), Integer::sum);

        if (unwatched < FirstCrusadePerformanceConfig.strategicDematerialiseDelay()) {
            this.lastRejection = "paciencia " + unwatched + "/"
                    + FirstCrusadePerformanceConfig.strategicDematerialiseDelay() + " ticks";
            return key;
        }

        this.candidates.remove(key);

        FCStrategicBattle battle = new FCStrategicBattle(centre);

        for (Mob mob : occupants) {
            FirstCrusadeFaction faction = FirstCrusadeFactionManager.getFaction(mob);
            FCStrategicForce force = sides.get(faction);
            force.add(mob.getType(), mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()),
                    mob.hasCustomName() ? mob.getCustomName().getString() : null,
                    FCStrategicForce.equipmentOf(mob));

            if (mob instanceof FCUnit unit && unit.canLead()) {
                force.setHasCommander(true);
            }
        }

        FirstCrusadeFaction holder = territoryHolder(level, centre);

        for (FCStrategicForce force : sides.values()) {
            if (force.totalUnits() > 0) {
                // Whoever owns the ground is defending it. This is the one place the strategic layer
                // touches the existing war map, and it is the right one: a fight inside a Hive's
                // border or inside an Ork camp's corruption is a fight on somebody's home turf, and
                // that is exactly what the defence advantage is meant to represent.
                force.setDefending(force.faction() == holder);
                battle.addForce(force);
            }
        }

        this.lastRejection = "absorvido: " + battle.describe();

        // Record the army before removing it, never after.
        this.battles.add(battle);
        setDirty();

        for (Mob mob : occupants) {
            mob.discard();
        }

        return centre;
    }

    /**
     * The key a cluster at this position should accumulate patience under: an existing candidate
     * within a cluster radius, or the position itself.
     */
    private BlockPos matchKey(BlockPos centre) {
        double radius = FirstCrusadePerformanceConfig.strategicClusterRadius();

        for (BlockPos known : this.candidates.keySet()) {
            if (known.closerThan(centre, radius)) {
                return known;
            }
        }

        return centre;
    }

    /**
     * Which faction, if any, owns the ground a battle is being fought on.
     *
     * <p>Reads the war map the mod already keeps rather than scanning for blocks: a city knows its
     * border radius and a camp knows its corruption radius, so this is a walk over two small sets of
     * packed positions and no world access at all.
     */
    private static FirstCrusadeFaction territoryHolder(ServerLevel level, BlockPos centre) {
        WorldWarMapData warMap = WorldWarMapData.get(level);

        for (long packed : warMap.getCities()) {
            WorldWarMapData.CityInfo info = warMap.getCityInfo(packed);
            int radius = info == null ? 0 : info.borderRadius();

            if (radius > 0 && centre.closerThan(BlockPos.of(packed), radius)) {
                return FirstCrusadeFaction.IMPERIUM;
            }
        }

        for (long packed : warMap.getCamps()) {
            WorldWarMapData.CampInfo info = warMap.getCampInfo(packed);
            int radius = info == null ? 0 : info.corruptionRadius();

            if (radius > 0 && centre.closerThan(BlockPos.of(packed), radius)) {
                return FirstCrusadeFaction.ORKS;
            }
        }

        return FirstCrusadeFaction.NEUTRAL;
    }

    /**
     * Whether one entity may be folded into a strategic force.
     *
     * <p>Delegated to {@link FCStrategicEligibility} so that absorption and the debug command cannot
     * drift apart, and so a unit that must stay physical declares that on its own class rather than
     * relying on this file remembering it.
     */
    private static boolean canAbsorb(Mob mob) {
        return FCStrategicEligibility.canAbsorb(mob);
    }

    private static double nearestPlayerDistance(ServerLevel level, BlockPos pos) {
        List<? extends Player> players = level.players();
        double best = Double.MAX_VALUE;

        for (int i = 0; i < players.size(); i++) {
            double distanceSq = players.get(i)
                    .distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distanceSq < best) {
                best = distanceSq;
            }
        }

        return best == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(best);
    }

    // ==================================================================== persistence

    public static FCStrategicBattleData load(CompoundTag tag) {
        FCStrategicBattleData data = new FCStrategicBattleData();

        ListTag list = tag.getList("Battles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.battles.add(FCStrategicBattle.load(list.getCompound(i)));
        }

        ListTag pending = tag.getList("Materialising", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            // A world that stopped mid-materialisation resumes it rather than losing the remainder.
            data.materialising.add(FCStrategicBattle.load(pending.getCompound(i)));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (FCStrategicBattle battle : this.battles) {
            list.add(battle.save());
        }
        tag.put("Battles", list);

        ListTag pending = new ListTag();
        for (FCStrategicBattle battle : this.materialising) {
            pending.add(battle.save());
        }
        tag.put("Materialising", pending);

        return tag;
    }

}
