package com.example.examplemod.animal;

import java.util.List;

import javax.annotation.Nullable;

import com.example.examplemod.flora.FloraTags;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * The shared behaviour of the mod's wildlife: the parts of "being an animal in a war zone" that
 * every species needs and none of them should implement twice.
 *
 * <h2>Panic is a state, not a goal</h2>
 *
 * The brief asks for animals that flee explosions, gunfire and vehicles. Writing that as three
 * separate goals means three separate searches running every tick on every animal, which is exactly
 * the kind of cost the performance rules forbid for a mod that also fields armies.
 *
 * <p>So it is inverted: nothing searches for danger. Danger reports itself, through the one call the
 * game already makes when something bad happens — {@link #hurt}. A hit of any kind sets
 * {@link #alarmTicks}, and while that is running the animal's speed is raised and its ordinary
 * goals give way to panic. An explosion that hurts three of a herd alarms three animals for free;
 * one that hurts none was not close enough to matter.
 *
 * <h2>Population is capped at spawn, not culled later</h2>
 *
 * {@link #tooCrowded} is checked once, when the game asks whether a spawn may happen. Culling
 * afterwards would be both wasteful (the entity was built, ticked and then thrown away) and visible
 * to the player, who would watch animals vanish. Refusing the spawn costs one AABB query at the
 * moment a decision is being made anyway.
 */
public abstract class FCAnimalEntity extends Animal {

    /** How long a hit keeps the animal running, in ticks. Six seconds — long enough to break off. */
    protected static final int ALARM_TICKS = 120;

    /** Radius the population cap counts over, in blocks. */
    private static final double CROWD_RADIUS = 48.0D;

    private int alarmTicks;

    protected FCAnimalEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ alarm

    /** True while the animal is fleeing something that just hurt it or one of its herd. */
    public boolean isAlarmed() {
        return this.alarmTicks > 0;
    }

    /** Raises the alarm without dealing damage — used to pass panic along a herd. */
    public void alarm() {
        if (!FCAnimalConfig.FLEE_FROM_COMBAT.get()) {
            return;
        }

        this.alarmTicks = ALARM_TICKS;
    }

    /**
     * Passes the alarm to neighbours of the same species.
     *
     * <p>One query, only at the moment of a hit — not a per-tick sweep. A herd scatters together
     * because the alarm spreads, not because every member is watching for trouble.
     */
    protected void alarmHerd(double radius) {
        if (this.level().isClientSide || !FCAnimalConfig.FLEE_FROM_COMBAT.get()) {
            return;
        }

        List<? extends FCAnimalEntity> neighbours = this.level().getEntitiesOfClass(
                this.getClass(), this.getBoundingBox().inflate(radius), other -> other != this);

        for (FCAnimalEntity neighbour : neighbours) {
            neighbour.alarm();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!super.hurt(source, amount)) {
            return false;
        }

        if (!this.level().isClientSide) {
            alarm();
            alarmHerd(16.0D);
        }

        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.alarmTicks > 0) {
            this.alarmTicks--;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AlarmTicks", this.alarmTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.alarmTicks = tag.getInt("AlarmTicks");
    }

    // ------------------------------------------------------------------ population

    /**
     * True when this stretch of ground already holds its share of this species.
     *
     * <p>Counts the exact type rather than "animals", so a plain full of Grox does not stop a Sump
     * Rat from existing there. The limit itself is config, because the right number is a matter of
     * taste and of how heavy a server can afford to be.
     *
     * <p><b>It has no effect during chunk generation.</b> There the accessor is a
     * {@code WorldGenRegion}, whose entity queries return an empty list by contract, so the count is
     * always zero and the answer is always "not crowded". The herds a new chunk is born with are
     * therefore bounded by the datapack instead — {@code creature_spawn_probability} and the group
     * size in the biome's spawners list. Both ceilings are real; they just live in different places,
     * and {@code FCAnimals} documents which is which.
     */
    public static boolean tooCrowded(ServerLevelAccessor level, EntityType<?> type, BlockPos pos,
                                     int limit) {
        AABB box = new AABB(pos).inflate(CROWD_RADIUS);

        return level.getEntitiesOfClass(Mob.class, box, mob -> mob.getType() == type).size() >= limit;
    }

    /**
     * The ordinary spawn test for the mod's wildlife: daylight, ground the mod calls natural, and
     * the cap.
     *
     * <h3>Why not {@code Animal.checkAnimalSpawnRules}</h3>
     *
     * Vanilla's test asks for {@code #minecraft:animals_spawnable_on}, and that tag contains exactly
     * one block: {@code grass_block}. On the planets that is a silent, total refusal for half the
     * ground the mod builds — {@code rocky_highland} is topped with gravel, {@code salt_waste} with
     * salt crust, {@code sump_marsh} with sump mud. An animal listed in those biomes would simply
     * never appear, with no error anywhere to explain it.
     *
     * <p>So the ground rule is the mod's own vocabulary, {@code firstcrusade:flora_ground_natural} —
     * the same tag that decides where a plant may take root. If the ground grows grass, it can carry
     * something that eats grass, and a datapack that widens one widens the other.
     */
    public static boolean checkWildlifeSpawn(EntityType<? extends FCAnimalEntity> type,
                                             ServerLevelAccessor level, MobSpawnType reason,
                                             BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!FCAnimalConfig.ANIMAL_SPAWN_ENABLED.get()) {
            return false;
        }

        // A spawn egg or a command means somebody asked for this animal on purpose; the cap is for
        // natural spawning, and refusing a deliberate spawn would just look broken.
        if (reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND
                || reason == MobSpawnType.BUCKET) {
            return true;
        }

        if (!level.getBlockState(pos.below()).is(FloraTags.GROUND_NATURAL)) {
            return false;
        }

        if (tooCrowded(level, type, pos, FCAnimalConfig.WILDLIFE_POPULATION_LIMIT.get())) {
            return false;
        }

        return isBrightEnoughToSpawn(level, pos);
    }

    // ------------------------------------------------------------------ misc

    /**
     * Wildlife does not despawn once it has settled somewhere.
     *
     * <p>A herd the player has walked past and fenced in should still be there tomorrow. The
     * population cap is what keeps the count honest, so despawning would only take away animals the
     * cap has already accounted for.
     */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return super.getTarget();
    }

    /** Convenience for goals: is the given entity something this animal should run from? */
    protected static boolean isThreat(Entity entity) {
        return entity instanceof Player || entity instanceof Mob;
    }

    protected static boolean isServer(LevelAccessor level) {
        return level instanceof ServerLevel;
    }
}
