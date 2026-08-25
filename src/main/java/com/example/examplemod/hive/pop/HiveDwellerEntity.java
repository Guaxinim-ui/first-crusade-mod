package com.example.examplemod.hive.pop;

import com.example.examplemod.GuardsmanEntity;
import com.example.examplemod.ImperialCitizenEntity;
import com.example.examplemod.performance.ai.FCLodGoal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A person who lives in the Hive (spec §19).
 *
 * <h2>One class, five roles</h2>
 *
 * The spec asks for eight kinds of dweller. Three already existed — {@link ImperialCitizenEntity},
 * {@link GuardsmanEntity} and the Enforcer — and the five that did not are the same creature with
 * different clothes and one bit of difference: whether they attack you. Writing five subclasses
 * would have meant five copies of this goal list, and {@code STATUS.md} §5 says to reuse before
 * adding.
 *
 * <p>The role is derived from the {@link EntityType}, not stored in synched data. A worker cannot
 * become a priest, so there is nothing to synchronise: both sides already know which type they are
 * dealing with. That is also why each role needs its own EntityType — a spawn egg addresses a type,
 * and the owner asked to be able to place these by hand.
 *
 * <h2>Doors</h2>
 *
 * They open them, like the citizen does. A hive is corridors, and a dweller that cannot pass a
 * closed door is a dweller standing in a hab-block forever.
 */
public class HiveDwellerEntity extends PathfinderMob
        implements com.example.examplemod.unit.profile.FCUnit {

    /**
     * Civilians: they do not shoot, they do not hold, and they are excluded from targeting by
     * {@code UnitRole.CIVILIAN}. The numbers exist because the interface asks for them, not because
     * a merchant has a firing solution.
     */
    private static final com.example.examplemod.unit.profile.FCCombatProfile CIVILIAN_PROFILE =
            com.example.examplemod.unit.profile.FCCombatProfile.builder()
                    .range(0.0F, 2.0F, 0.0F)
                    .accuracy(0.0F)
                    .speeds(1.0D, 1.3D)
                    .courage(0.0F)
                    .retreatHealthThreshold(1.0F)   // runs from the first scratch, which is correct
                    .usesCover(false)
                    .backsAwayFromMelee(true)
                    .melee(0.0F, 20)
                    .perception(20, 16.0D, 100)
                    .build();

    /** The gangs. Short reach, poor aim, and brave enough to be a problem in a corridor. */
    private static final com.example.examplemod.unit.profile.FCCombatProfile GANG_PROFILE =
            com.example.examplemod.unit.profile.FCCombatProfile.builder()
                    .range(3.5F, 2.0F, 0.0F)
                    .accuracy(0.45F)
                    .speeds(1.1D, 1.0D)
                    .courage(0.6F)
                    .retreatHealthThreshold(0.25F)
                    .usesCover(true)
                    .backsAwayFromMelee(false)
                    .melee(3.0F, 20)
                    .perception(20, 24.0D, 100)
                    .build();

    /**
     * Resolved from the entity type on first read, never assigned in the constructor.
     *
     * <p>{@code Mob}'s constructor calls {@link #registerGoals()}, so the goals are built during
     * {@code super(...)} — before any field this class declares has been assigned. Taking the role
     * as a constructor argument therefore gave every dweller a null role inside its own goal list.
     * See {@link FCHiveDwellers#roleOf}.
     */
    private HiveRole role;

    public HiveDwellerEntity(EntityType<? extends HiveDwellerEntity> entityType, Level level) {
        super(entityType, level);

        if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
        }
    }

    public HiveRole role() {
        if (this.role == null) {
            this.role = FCHiveDwellers.roleOf(getType());
        }

        return this.role;
    }

    /**
     * Attributes for one role.
     *
     * <p>Built per role rather than shared, because the numbers <i>are</i> the difference between a
     * merchant and a ganger. Everything else about them is identical and lives in this one class.
     */
    public static AttributeSupplier.Builder createAttributes(HiveRole role) {
        AttributeSupplier.Builder builder = Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, role.health())
                .add(Attributes.MOVEMENT_SPEED, role.speed())
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, role.armour());

        if (role.hostile()) {
            builder.add(Attributes.ATTACK_DAMAGE, 3.0D);
        }

        return builder;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new OpenDoorGoal(this, true));

        if (role().hostile()) {
            this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));

            // Gangers pick on the Imperium, not on each other. Targeting by the two concrete
            // Imperial types rather than by "anything alive" keeps a fight in the Underhive from
            // turning into every dweller attacking every other one.
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                    this, Player.class, true));
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                    this, GuardsmanEntity.class, true));
            this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                    this, ImperialCitizenEntity.class, false));
        } else {
            // Civilians run. That is the whole of their combat model, and it is the correct one:
            // a hive worker who fights back is a soldier, and the mod already has eleven of those.
            this.goalSelector.addGoal(1, new PanicGoal(this, 1.3D));
        }

        // The idle goals go through the performance layer's LOD gate, exactly as the citizen's do.
        // Without it a populated hive is hundreds of pathfinders ticking behind a player who left.
        // Float, OpenDoor, Panic and the combat goals are deliberately NOT wrapped — those are
        // safety and reaction, and a dweller that drowns because the throttle had not come round is
        // a bug rather than an optimisation.
        this.goalSelector.addGoal(5, new FCLodGoal(this,
                new WaterAvoidingRandomStrollGoal(this, 0.7D), 2));
        this.goalSelector.addGoal(6, new FCLodGoal(this,
                new LookAtPlayerGoal(this, Player.class, 8.0F), 2));
        this.goalSelector.addGoal(7, new FCLodGoal(this, new RandomLookAroundGoal(this), 2));
    }

    // ====================================================================================
    // FCUnit — the dweller answers for itself
    // ====================================================================================
    //
    // Implemented rather than added to FirstCrusadeFactionManager's instanceof chain, because that
    // class says in its own javadoc that the chain is a legacy fallback and every new mob should
    // use this seam instead. Without it a dweller falls through to NEUTRAL, and an Ork raid would
    // walk past a hab-block full of people without noticing them.

    @Override
    public com.example.examplemod.FirstCrusadeFaction getUnitFaction() {
        // The gangs are nobody's. HOSTILE and not ORKS: a hive ganger fighting a Guardsman is not
        // a WAAAGH!, and filing them with the Orks would let them stand in an Ork formation.
        return role().hostile()
                ? com.example.examplemod.FirstCrusadeFaction.HOSTILE
                : com.example.examplemod.FirstCrusadeFaction.IMPERIUM;
    }

    @Override
    public com.example.examplemod.unit.profile.UnitRole getUnitRole() {
        return role().hostile()
                ? com.example.examplemod.unit.profile.UnitRole.LINE_INFANTRY
                : com.example.examplemod.unit.profile.UnitRole.CIVILIAN;
    }

    @Override
    public com.example.examplemod.unit.profile.FCCombatProfile getCombatProfile() {
        return role().hostile() ? GANG_PROFILE : CIVILIAN_PROFILE;
    }

    @Override
    public String getUnitId() {
        return role().entityName();
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================
    //
    // The role is NOT written. It is a property of the entity type, and the type is already in the
    // save — writing it as well would create a second copy that a future rename could contradict.

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("HiveRole", role().key());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Read but not applied: the tag is written for anyone reading the save with a NBT tool, and
        // for the marker system's own debugging. The authority is the entity type.
    }

}
