package com.example.examplemod.hive.city;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.FCRegistry;
import com.example.examplemod.hive.HiveMarkers.MarkerType;
import com.example.examplemod.hive.city.HiveCityMarkerData.MarkerRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Spec §12 — "cidade viva". Turns the persistent structure markers ({@link HiveCityMarkerData})
 * into a living population: near a player, each spawn marker holds one entity; when that entity
 * dies the marker respawns another after a cooldown. Runs only on {@code hive_world}.
 *
 * <p><b>This first slice</b> handles the three friendly spawn markers only —
 * {@link MarkerType#CIVIL_SPAWN} and {@link MarkerType#WORKER_SPAWN} → {@code imperial_citizen},
 * {@link MarkerType#GUARDSMAN_SPAWN} → {@code guardsman} — reusing the existing entities exactly
 * as {@link com.example.examplemod.ImperialPopulationManager} spawns them (create → moveTo →
 * addFreshEntity). Deliberately deferred to the next slice (so this stays reviewable and can't
 * mis-spawn): {@link MarkerType#ENEMY_SPAWN} (needs the invasion/sector-state gate),
 * {@link MarkerType#COMMANDER_POINT} (a single unique commander), Skitarii/Enforcer mapping, and
 * actually walking patrols along {@link MarkerType#PATROL_POINT} routes.
 *
 * <p><b>Why marker-driven, not a global scan.</b> Spec §16 forbids per-tick sweeps of the whole
 * 960×960 city. This ticks at {@link #TICK_INTERVAL}, and only ever touches a marker whose chunk
 * is loaded and which has a player within {@link #ACTIVATION_DISTANCE}. Occupancy is tracked by
 * the marker's linked-entity UUID (already persisted by {@link HiveCityMarkerData}), so it
 * survives save/reload and never double-spawns: a marker with a live linked entity is skipped; a
 * linked entity confirmed gone (chunk loaded, {@code getEntity} null) clears the link and starts
 * the respawn cooldown.
 *
 * <p>Caps (global + per type) count occupied markers — a marker whose linked entity is merely in
 * an unloaded chunk still counts, so roaming the city can't blow past the limit. Spawned mobs are
 * {@code setPersistenceRequired} so this manager, not vanilla despawn, owns their lifecycle.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class HivePopulationManager {
    private HivePopulationManager() {}

    // ---- tuning (spec §16: everything bounded/configurable) ----
    /** Only evaluates the population every this many ticks (2s). */
    public static final int TICK_INTERVAL = 40;
    /** A spawn marker is "active" only with a player within this distance (blocks). */
    public static final double ACTIVATION_DISTANCE = 72.0;
    private static final double ACTIVATION_DISTANCE_SQ = ACTIVATION_DISTANCE * ACTIVATION_DISTANCE;
    /** Ticks after an entity dies before its marker may respawn another (60s). */
    public static final int RESPAWN_COOLDOWN_TICKS = 1200;
    /** Hard ceiling across the whole hive population managed here. */
    public static final int GLOBAL_CAP = 200;
    private static final int CAP_CIVIL = 120;
    private static final int CAP_WORKER = 60;
    private static final int CAP_GUARDSMAN = 60;
    // Spec §19, second slice. The four new kinds are deliberately scarcer than the crowd: a hive is
    // mostly workers and civilians, and a city where every third person is a priest reads as a joke.
    private static final int CAP_MERCHANT = 20;
    private static final int CAP_MECHANICUS = 12;
    private static final int CAP_ENFORCER = 24;
    private static final int CAP_GANG = 24;
    /** At most this many new spawns per evaluation, so a first activation ramps up over seconds. */
    private static final int SPAWNS_PER_PASS = 6;

    private static volatile boolean enabled = true;

    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean isEnabled() { return enabled; }

    private static boolean isSpawnType(MarkerType t) {
        return switch (t) {
            case CIVIL_SPAWN, WORKER_SPAWN, GUARDSMAN_SPAWN,
                 TRADE_POINT, CONSTRUCTION_POINT, DEFENSE_POINT, ENEMY_SPAWN -> true;
            default -> false;
        };
    }

    private static int capFor(MarkerType t) {
        return switch (t) {
            case CIVIL_SPAWN -> CAP_CIVIL;
            case WORKER_SPAWN -> CAP_WORKER;
            case GUARDSMAN_SPAWN -> CAP_GUARDSMAN;
            case TRADE_POINT -> CAP_MERCHANT;
            case CONSTRUCTION_POINT -> CAP_MECHANICUS;
            case DEFENSE_POINT -> CAP_ENFORCER;
            case ENEMY_SPAWN -> CAP_GANG;
            default -> 0;
        };
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (!level.dimension().equals(HiveWorld.LEVEL)) return;
        if (!enabled) return;
        if (level.getGameTime() % TICK_INTERVAL != 0) return;
        if (level.players().isEmpty()) return; // nobody to activate anything

        HiveCityMarkerData data = HiveCityMarkerData.get(level);
        List<MarkerRecord> markers = data.markers(); // immutable copy of shared, mutable records
        if (markers.isEmpty()) return;

        long now = level.getGameTime();

        // ---- pass 1: reconcile links (detect deaths) + count current occupancy ----
        Map<MarkerType, Integer> occupied = new EnumMap<>(MarkerType.class);
        int total = 0;
        for (MarkerRecord m : markers) {
            if (!isSpawnType(m.type) || m.linkedEntity == null) continue;
            boolean loaded = level.hasChunkAt(m.pos);
            Entity ent = loaded ? level.getEntity(m.linkedEntity) : null;
            if (loaded && ent == null) {
                // Confirmed gone (chunk is loaded but the entity is not there): free the marker
                // and start its respawn cooldown.
                data.linkEntity(m.id, null, now + RESPAWN_COOLDOWN_TICKS);
            } else {
                // Alive, or in an unloaded chunk (assume alive): still occupies the marker + cap.
                occupied.merge(m.type, 1, Integer::sum);
                total++;
            }
        }

        // ---- pass 2: spawn into free, active, valid markers under caps ----
        if (total >= GLOBAL_CAP) return;
        int spawnedThisPass = 0;
        for (MarkerRecord m : markers) {
            if (spawnedThisPass >= SPAWNS_PER_PASS || total >= GLOBAL_CAP) break;
            if (!isSpawnType(m.type) || m.linkedEntity != null) continue;
            if (now < m.nextSpawnAtGameTime) continue;
            if (occupied.getOrDefault(m.type, 0) >= capFor(m.type)) continue;
            if (!level.hasChunkAt(m.pos)) continue;
            if (!nearAnyPlayer(level, m.pos)) continue;
            if (!isSafeSpawn(level, m.pos)) continue;

            Mob mob = createFor(m.type, level, m.pos);
            if (mob == null) continue;
            mob.moveTo(m.pos.getX() + 0.5D, m.pos.getY(), m.pos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);

            data.linkEntity(m.id, mob.getUUID(), 0L);
            occupied.merge(m.type, 1, Integer::sum);
            total++;
            spawnedThisPass++;
        }
    }

    /**
     * The body a marker puts in the world (spec §19).
     *
     * <h2>The bug this fixed on the way past</h2>
     *
     * {@code CIVIL_SPAWN} and {@code WORKER_SPAWN} both used to return {@code imperial_citizen}, so
     * the sixteen worker markers the district generators place produced the same person the
     * twenty-six civil markers did. The templates had been marking a distinction the game never
     * showed.
     *
     * <h2>Gangers only live at the bottom</h2>
     *
     * {@code ENEMY_SPAWN} is the one marker whose answer depends on where it is. A gang in the
     * Administratum is not a gang, it is an incident; the Underhive is the whole reason the role
     * exists. The test goes through {@link HiveTier}, so it is the same level ladder the transit
     * lift rides rather than a second opinion about which Y is which floor.
     *
     * <p>An enemy marker above the Underhive returns null and the marker simply stays empty — the
     * spec's own note for that marker is "needs the invasion/sector-state gate", and when that gate
     * exists this is where it hangs.
     */
    private static Mob createFor(MarkerType type, ServerLevel level, BlockPos at) {
        return switch (type) {
            case CIVIL_SPAWN -> FCRegistry.IMPERIAL_CITIZEN.get().create(level);
            case GUARDSMAN_SPAWN -> FCRegistry.GUARDSMAN.get().create(level);
            case DEFENSE_POINT -> FCRegistry.ENFORCER.get().create(level);

            case WORKER_SPAWN -> com.example.examplemod.hive.pop.FCHiveDwellers
                    .typeFor(com.example.examplemod.hive.pop.HiveRole.WORKER).create(level);
            case TRADE_POINT -> com.example.examplemod.hive.pop.FCHiveDwellers
                    .typeFor(com.example.examplemod.hive.pop.HiveRole.MERCHANT).create(level);
            case CONSTRUCTION_POINT -> com.example.examplemod.hive.pop.FCHiveDwellers
                    .typeFor(com.example.examplemod.hive.pop.HiveRole.MECHANICUS_WORKER).create(level);

            case ENEMY_SPAWN -> HiveTier.of(at.getY()) == HiveTier.UNDERHIVE
                    ? com.example.examplemod.hive.pop.FCHiveDwellers
                            .typeFor(com.example.examplemod.hive.pop.HiveRole.GANG_MEMBER).create(level)
                    : null;

            default -> null;
        };
    }

    private static boolean nearAnyPlayer(ServerLevel level, BlockPos pos) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= ACTIVATION_DISTANCE_SQ) {
                return true;
            }
        }
        return false;
    }

    /** Feet + head clear, solid floor below, and not standing in a fluid (toxic sludge/water). */
    private static boolean isSafeSpawn(ServerLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) return false;
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        if (!at.getCollisionShape(level, pos).isEmpty()) return false;
        if (!above.getCollisionShape(level, pos.above()).isEmpty()) return false;
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // ---- admin helpers used by /fchive population ... ----

    /** Live/occupied counts per spawn type, for {@code /fchive population status}. */
    public static Map<MarkerType, Integer> census(ServerLevel level) {
        Map<MarkerType, Integer> out = new EnumMap<>(MarkerType.class);
        for (MarkerRecord m : HiveCityMarkerData.get(level).markers()) {
            if (isSpawnType(m.type) && m.linkedEntity != null) out.merge(m.type, 1, Integer::sum);
        }
        return out;
    }

    /**
     * Discard every entity currently linked to a hive marker and clear the links (spec §18
     * "limpar apenas NPCs da Hive City"). Only removes entities this manager spawned/linked —
     * never a player's own mobs. Returns how many were removed. Unloaded-chunk links are cleared
     * too (their entity, if any, is left to be reconciled/removed on next load).
     */
    public static int clearPopulation(ServerLevel level) {
        HiveCityMarkerData data = HiveCityMarkerData.get(level);
        int removed = 0;
        for (MarkerRecord m : data.markers()) {
            if (m.linkedEntity == null) continue;
            Entity ent = level.getEntity(m.linkedEntity);
            if (ent != null) {
                ent.discard();
                removed++;
            }
            data.linkEntity(m.id, null, 0L);
        }
        return removed;
    }
}
