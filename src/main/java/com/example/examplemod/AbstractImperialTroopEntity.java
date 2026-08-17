package com.example.examplemod;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared base for the Imperium's standalone themed city troops (Skitarii Ranger, Kasrkin, Enforcer,
 * Mine Guard, Agri Militia, Sister of Battle, Penal Legionnaire, Jungle Fighter, ...). Holds what
 * every one of them shares: the bind to an Imperial Command Core, faction-gated targeting,
 * persistence, NBT (Core + guard post), the death bookkeeping that frees a slot in the city's
 * military tally, and the common AI goals (idle/look/stroll, target selection and a guard-post goal
 * so the patrol manager can circulate them around the city like Guardsmen).
 *
 * Subclasses only add their attributes, weapon, name and their single attack goal (via
 * {@link #registerCombatGoals()}). The rank/chapter machinery is deliberately left to the Guardsman.
 */
public abstract class AbstractImperialTroopEntity extends PathfinderMob
        implements LasgunAimingEntity, ImperialTroopVisuals {
    private static final EntityDataAccessor<Integer> LASGUN_COMBAT_POSE = SynchedEntityData.defineId(AbstractImperialTroopEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LASGUN_COMBAT_TICKS = SynchedEntityData.defineId(AbstractImperialTroopEntity.class, EntityDataSerializers.INT);

    /**
     * Which individual this soldier is. Synced because the renderer runs on the client, and an int
     * because seven appearance features in one field is one packet instead of seven.
     */
    private static final EntityDataAccessor<Integer> VISUAL_VARIANT = SynchedEntityData.defineId(AbstractImperialTroopEntity.class, EntityDataSerializers.INT);

    /** The NBT key the roll persists under. Named for the mod so it cannot collide with vanilla. */
    public static final String VISUAL_VARIANT_TAG = "FirstCrusadeVisualVariant";

    private BlockPos commandCorePos;
    private BlockPos guardPostPos;

    /** Cached so the renderer's per-frame lookup never touches the registry. */
    private String appearanceKey;

    protected AbstractImperialTroopEntity(EntityType<? extends AbstractImperialTroopEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();

        // City buildings now have real wooden doors — troops must be able to open and path
        // through them, or a closed door would trap them inside their own barracks.
        if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LASGUN_COMBAT_POSE, LasgunCombatPose.IDLE.ordinal());
        this.entityData.define(LASGUN_COMBAT_TICKS, POSE_NEVER_STARTED);

        // Rolled here, at the one moment every spawn path passes through — constructors run for
        // troops created by a barracks, by a raid, by a spawn egg and by /summon alike, and
        // finalizeSpawn does not. The client rolls its own value for a frame and is immediately
        // overwritten by the server's; a save then pins it forever in readAdditionalSaveData.
        this.entityData.define(VISUAL_VARIANT,
                this.random.nextInt(ImperialTroopAppearance.variantCount(this.appearanceKey())));
    }

    // ==================================================================== appearance

    @Override
    public String appearanceKey() {
        if (this.appearanceKey == null) {
            net.minecraft.resources.ResourceLocation id =
                    net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(this.getType());
            this.appearanceKey = id == null ? ImperialTroopAppearance.FALLBACK_TROOP : id.getPath();
        }

        return this.appearanceKey;
    }

    @Override
    public int getVisualVariant() {
        return this.entityData.get(VISUAL_VARIANT);
    }

    /** Used by {@code /fctroop variant} and by any future "this unit was re-kitted" path. */
    public void setVisualVariant(int variant) {
        this.entityData.set(VISUAL_VARIANT, Math.max(0, variant));
    }

    @Override
    public LasgunCombatPose getLasgunCombatPose() {
        return LasgunCombatPose.fromId(this.entityData.get(LASGUN_COMBAT_POSE));
    }

    /**
     * How far into its current combat pose this unit is, in ticks.
     *
     * <h2>Derived, not synchronised</h2>
     *
     * <p>The synchronised value behind this is the game time the pose <i>began</i>, not the counter
     * itself. That looks like an indirection for nothing until you count packets: the combat goals
     * call {@code setLasgunCombatPose} every single tick with a counter that has advanced by one, so
     * storing the counter meant {@code SynchedEntityData} saw a changed value every tick and pushed
     * an entity-metadata packet to every client tracking that soldier — twenty a second, per
     * trooper, for a number the client can work out on its own. Storing the start instead means the
     * stored value is <i>identical</i> on every one of those calls, and vanilla only marks an entry
     * dirty when the value actually differs, so the packets stop entirely.</p>
     *
     * <p>The cost is that the client reads its own {@code getGameTime()}, which can sit a few ticks
     * away from the server's between time syncs. For an animation phase that is invisible; it would
     * not be acceptable for anything that decided damage, which is why nothing here does.</p>
     */
    @Override
    public int getLasgunCombatTicks() {
        int start = this.entityData.get(LASGUN_COMBAT_TICKS);
        return start == POSE_NEVER_STARTED ? 0 : Math.max(0, (int) this.level().getGameTime() - start);
    }

    @Override
    public void setLasgunCombatPose(LasgunCombatPose pose, int poseTicks) {
        LasgunCombatPose safePose = pose == null ? LasgunCombatPose.IDLE : pose;
        this.entityData.set(LASGUN_COMBAT_POSE, safePose.ordinal());
        this.entityData.set(LASGUN_COMBAT_TICKS,
                (int) this.level().getGameTime() - Math.max(0, poseTicks));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));

        // Combat (priority 2) is added by the subclass.
        registerCombatGoals();

        // Walk back only when genuinely far from the base, then idle. ImperialTroopGuardPostGoal
        // used to drag the troop onto a rotating waypoint every few seconds; a simplified base has
        // no patrol ring, so a troop stands where it stands until something is wrong.
        this.goalSelector.addGoal(3, new LightweightReturnToBaseGoal(this, this::getCommandCorePos,
                1.0D, SimpleImperialBaseBalance.RETURN_TRIGGER_DISTANCE));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8D,
                SimpleImperialBaseBalance.STROLL_INTERVAL_TICKS));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    /** Subclasses add their single attack goal here, at priority 2 (ranged or melee). */
    protected abstract void registerCombatGoals();

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos;
        this.setPersistenceRequired();
    }

    public BlockPos getCommandCorePos() {
        return this.commandCorePos;
    }

    public boolean isAssignedToCommandCore(BlockPos pos) {
        return this.commandCorePos != null && this.commandCorePos.equals(pos);
    }

    public void assignGuardPost(BlockPos guardPostPos) {
        this.guardPostPos = guardPostPos;
    }

    @Nullable
    public BlockPos getGuardPostPos() {
        return this.guardPostPos;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (!FirstCrusadeFactionManager.canAttack(this, target)) {
            return false;
        }

        return super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !FirstCrusadeFactionManager.canAttack(this, target)) {
            super.setTarget(null);
            return;
        }

        super.setTarget(target);
    }

    /**
     * Whether this troop occupies a slot in the city's recruited-military tally (and therefore
     * frees one on death). Units that are granted rather than recruited (e.g. the City Commander)
     * override this to false so their death doesn't corrupt the recruit count.
     */
    protected boolean countsTowardGarrisonTally() {
        return true;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide && this.commandCorePos != null && countsTowardGarrisonTally()) {
            BlockEntity blockEntity = this.level().getBlockEntity(this.commandCorePos);

            if (blockEntity instanceof ImperialCommandCoreBlockEntity commandCore) {
                commandCore.onAssignedGuardsmanDeath();
            }
        }

        super.die(damageSource);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putInt(VISUAL_VARIANT_TAG, this.getVisualVariant());

        if (this.commandCorePos != null) {
            tag.putBoolean("HasCommandCore", true);
            tag.putInt("CommandCoreX", this.commandCorePos.getX());
            tag.putInt("CommandCoreY", this.commandCorePos.getY());
            tag.putInt("CommandCoreZ", this.commandCorePos.getZ());
        } else {
            tag.putBoolean("HasCommandCore", false);
        }

        if (this.guardPostPos != null) {
            tag.putBoolean("HasGuardPost", true);
            tag.putInt("GuardPostX", this.guardPostPos.getX());
            tag.putInt("GuardPostY", this.guardPostPos.getY());
            tag.putInt("GuardPostZ", this.guardPostPos.getZ());
        } else {
            tag.putBoolean("HasGuardPost", false);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        // A soldier saved before this system existed has no tag and keeps the value his
        // constructor rolled, which is then written out on the next save. He looks the same from
        // that point on — the one thing the appearance must never do is change on relog.
        if (tag.contains(VISUAL_VARIANT_TAG)) {
            this.setVisualVariant(tag.getInt(VISUAL_VARIANT_TAG));
        }

        if (tag.getBoolean("HasCommandCore")) {
            this.commandCorePos = new BlockPos(
                    tag.getInt("CommandCoreX"),
                    tag.getInt("CommandCoreY"),
                    tag.getInt("CommandCoreZ")
            );
        }

        if (tag.getBoolean("HasGuardPost")) {
            this.guardPostPos = new BlockPos(
                    tag.getInt("GuardPostX"),
                    tag.getInt("GuardPostY"),
                    tag.getInt("GuardPostZ")
            );
        }
    }
}
