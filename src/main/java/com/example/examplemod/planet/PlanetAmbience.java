package com.example.examplemod.planet;

import javax.annotation.Nullable;

import com.example.examplemod.FCRegistry;
import com.example.examplemod.campaign.CampaignData;
import com.example.examplemod.campaign.planet.PlanetWarState;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What a planet sounds like, and who you meet walking across it (§5, second slice).
 *
 * <h2>Keyed by dimension, and that is load-bearing</h2>
 *
 * The obvious place for wild spawns is the biome file's {@code spawners} block, and
 * {@code tools/generate_biomes.py} even leaves the door open for it: <i>"Mobs hostis vanilla ficam de
 * fora: o mod povoa o mundo com as duas faccoes."</i> The trouble is that biomes are <b>shared</b>.
 * {@code salt_waste} is 30% of Armageddon, 25% of the Forge World and <b>60% of the tomb world</b>;
 * {@code ash_waste} is on Armageddon, the Forge World and the Ork World. Putting Orks in either would
 * put Orks on the Necron tomb world — undercutting the one hundred-point clock the whole Necron
 * design hangs on.
 *
 * <p>That is the trap {@link PlanetHazard} already wrote down: a table keyed by something that is
 * nearly-but-not-quite unique is a table that will be wrong exactly once. So this is keyed by the
 * dimension, in Java, exactly as the hazards are.
 *
 * <h2>The garrison is read from the war, not from a list</h2>
 *
 * Which enemies roam a planet is not a fixed property of it. It is {@code enemyControl} — the number
 * the campaign already recomputes from the sectors every pass. Hold 90% of Armageddon and the wastes
 * go quiet; lose it back to 60% Ork and you meet them between the sectors again.
 *
 * <p>This is the point of the whole slice. Until now the campaign's percentages were something a
 * player read on the War Table and never felt underfoot: taking a sector changed a bar. Now it
 * changes who is standing in the ash.
 *
 * <h2>The Necrons are deliberately not here</h2>
 *
 * The tomb world spawns nothing through this class. {@code NecronAwakeningSpawner} owns what comes
 * out of that ground, gated on the awakening, and the Necrons have no spawn eggs for the same reason
 * — the roster arrives when the tomb decides. A second, control-driven source of Necrons would make
 * the clock something you can walk past.
 */
public final class PlanetAmbience {

    private PlanetAmbience() {
    }

    /** Ticks between spawn attempts, matching the Necron spawner's unhurried cadence. */
    private static final int SPAWN_INTERVAL = 600;

    /** Ticks between ambient sounds. Long: a noise on a timer stops being atmosphere and becomes a tic. */
    private static final int SOUND_INTERVAL = 1200;

    private static final int RADIUS = 48;
    private static final int RING_MIN = 24;
    private static final int RING_MAX = 40;

    /**
     * Enemy control below which a planet's wilderness is simply empty.
     *
     * <p>Not zero. A planet the player has all but cleared should feel cleared — leaving a thin
     * trickle at 5% enemy would mean the reward for winning a campaign is slightly fewer ambushes,
     * which reads as nothing at all.
     */
    private static final int QUIET_BELOW = 20;

    // ====================================================================================
    // The table
    // ====================================================================================

    /**
     * The wild population of one world.
     *
     * @param sound   played occasionally to anyone standing there, or null for a world with no
     *                signature of its own
     * @param roamers what enemy control puts in the field here, or null for a planet whose enemies
     *                are not the kind that wander
     * @param cap     living roamers near a player above which nothing more is sent
     */
    private record Ambience(@Nullable java.util.function.Supplier<SoundEvent> sound,
                            @Nullable Roamers roamers, int cap) {
    }

    /** The three Ork bodies a hostile world puts between the player and their objective. */
    private record Roamers(java.util.function.Supplier<EntityType<? extends Mob>> common,
                           java.util.function.Supplier<EntityType<? extends Mob>> filler,
                           java.util.function.Supplier<EntityType<? extends Mob>> leader) {
    }

    private static Roamers orks() {
        return new Roamers(
                () -> FCRegistry.ORK_BOY.get(),
                () -> FCRegistry.GRETCHIN.get(),
                () -> FCRegistry.ORK_NOB.get());
    }

    /**
     * What each world is, or null for one that is nothing in particular.
     *
     * <p>Macragge, Cadia and Verdanis get neither sound nor roamers on purpose, and it is the same
     * argument the hazards make: without somewhere quiet, "hostile" stops meaning anything.
     */
    @Nullable
    private static Ambience of(ResourceKey<Level> dimension) {
        if (FCPlanets.ORK_WORLD.equals(dimension)) {
            // Their own world. Loud, and full whatever the map says — but still read from control,
            // so a player who somehow takes it sees it empty out.
            return new Ambience(PlanetSounds.AMBIENT_ORK_WORLD, orks(), 12);
        }

        if (FCPlanets.ARMAGEDDON.equals(dimension)) {
            return new Ambience(PlanetSounds.AMBIENT_ASH, orks(), 8);
        }

        if (FCPlanets.FORGE_WORLD.equals(dimension)) {
            // Machinery, no roamers: the Forge World's enemy is its own air, which the ash hazard
            // already delivers.
            return new Ambience(PlanetSounds.AMBIENT_FORGE, null, 0);
        }

        if (FCPlanets.VALHALLA.equals(dimension)) {
            return new Ambience(PlanetSounds.AMBIENT_WIND, null, 0);
        }

        if (FCPlanets.CATACHAN.equals(dimension)) {
            // A death world's threat is the ground and the wildlife, both of which exist already.
            // Feral Orks in the jungle would be a second explanation for a place that has one.
            return new Ambience(PlanetSounds.AMBIENT_JUNGLE, null, 0);
        }

        if (FCPlanets.NECRON_TOMB_WORLD.equals(dimension)) {
            // Sound only. See the class note: the spawns there belong to the awakening.
            return new Ambience(PlanetSounds.AMBIENT_TOMB, null, 0);
        }

        return null;
    }

    // ====================================================================================
    // The tick
    // ====================================================================================

    /**
     * One player's worth of ambience. The whole entry point.
     *
     * <p>Called from the same throttled player tick the hazards use, and declines on the same cheap
     * question: {@link #of} is a handful of key comparisons and returns null for the overworld and
     * for the three quiet planets, so nobody standing anywhere else reads a block or queries an
     * entity.
     */
    public static void tick(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }

        Ambience ambience = of(player.level().dimension());

        if (ambience == null) {
            return;
        }

        ServerLevel level = player.serverLevel();

        java.util.function.Supplier<SoundEvent> sound = ambience.sound();

        if (sound != null && player.tickCount % SOUND_INTERVAL == 0) {
            playAmbient(level, player, sound.get());
        }

        Roamers roamers = ambience.roamers();

        if (roamers != null && player.tickCount % SPAWN_INTERVAL == 0 && !player.isCreative()) {
            maybeRoam(level, player, roamers, ambience.cap());
        }
    }

    /**
     * Plays the planet's signature to one player.
     *
     * <p>Sent at the player rather than from a block, and slightly off-centre so it does not read as
     * coming from inside their own head. {@link SoundSource#AMBIENT} so it obeys the ambient slider
     * a player has already set for exactly this kind of noise.
     */
    private static void playAmbient(ServerLevel level, ServerPlayer player, SoundEvent sound) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;

        level.playSound(null,
                player.getX() + Math.cos(angle) * 8.0D,
                player.getY(),
                player.getZ() + Math.sin(angle) * 8.0D,
                sound, SoundSource.AMBIENT,
                0.6F, 0.8F + level.random.nextFloat() * 0.4F);
    }

    /**
     * Puts a handful of the planet's owners in the field, if the war says they are there.
     *
     * <p>The count is control-driven and small. A planet at 100% enemy sends three at a time; one at
     * the quiet threshold sends one. This is meant to make a world feel occupied, not to be the
     * mod's combat — the raids and the war parties are that, and they already have owners.
     */
    private static void maybeRoam(ServerLevel level, ServerPlayer player, Roamers roamers, int cap) {
        PlanetWarState state = CampaignData.get(level)
                .existingState(level.dimension().location());

        // A front nobody has activated has no control to read. Silence is the honest answer: the
        // campaign has never had an opinion about this planet.
        if (state == null) {
            return;
        }

        int enemy = state.enemyControl();

        if (enemy < QUIET_BELOW) {
            return;
        }

        if (countRoamers(level, player) >= cap) {
            return;
        }

        // One to three, by how much of the planet they hold.
        int count = 1 + enemy / 40;

        for (int i = 0; i < count; i++) {
            // A Nob leads roughly one band in four, and only on a planet they largely hold — a
            // warband boss wandering out of ground they barely own reads as a bug.
            EntityType<? extends Mob> type;

            if (enemy >= 60 && level.random.nextInt(4) == 0) {
                type = roamers.leader().get();
            } else {
                type = level.random.nextBoolean() ? roamers.common().get() : roamers.filler().get();
            }

            spawn(level, player, type);
        }
    }

    /**
     * Living mod mobs of the enemy's own faction near the player.
     *
     * <p>Counts by faction rather than by exact type so a Nob, a Boy and a Gretchin all fill the same
     * bucket — otherwise the cap would be per-species and a clearing could hold three times as many
     * Orks as intended.
     */
    private static int countRoamers(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RADIUS),
                mob -> mob.isAlive()
                        && com.example.examplemod.FirstCrusadeFactionManager.getFaction(mob)
                        == com.example.examplemod.FirstCrusadeFaction.ORKS).size();
    }

    private static void spawn(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type) {
        BlockPos at = ringPos(level, player);

        if (at == null) {
            return;
        }

        Mob mob = type.create(level);

        if (mob == null) {
            return;
        }

        mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);

        // Deliberately NOT setPersistenceRequired, which is the opposite of what the Necron spawner
        // does and the difference is the whole point: those are an event and must not evaporate
        // mid-fight, while these are population. A player who walks away should leave them behind
        // rather than tow a growing tail of Orks across the planet.
        level.addFreshEntity(mob);
    }

    /** A ground position in a ring around the player, in already-loaded chunks only. */
    @Nullable
    private static BlockPos ringPos(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = RING_MIN + level.random.nextDouble() * (RING_MAX - RING_MIN);

            int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);

            // Loaded chunks only. Otherwise this forces a ring of chunk loads around a walking
            // player, which is the most expensive way to spawn anything.
            if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight() + 1, z))) {
                continue;
            }

            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));

            if (level.noCollision(new net.minecraft.world.phys.AABB(ground).inflate(0.4D, 0.0D, 0.4D)
                    .expandTowards(0.0D, 2.0D, 0.0D))) {
                return ground;
            }
        }

        return null;
    }
}
