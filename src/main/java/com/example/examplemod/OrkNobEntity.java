package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import javax.annotation.Nullable;
import com.example.examplemod.ai.formation.FCLeaderGoal;
import com.example.examplemod.ai.formation.FCSquad;
import com.example.examplemod.ai.formation.FCSquadLeader;
import com.example.examplemod.performance.config.FirstCrusadePerformanceConfig;
import com.example.examplemod.unit.profile.FCCombatProfile;
import com.example.examplemod.unit.profile.FCUnit;
import com.example.examplemod.unit.profile.UnitRole;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class OrkNobEntity extends Monster implements FCUnit, FCSquadLeader, GeoEntity {

    /**
     * A Nob leads by being the biggest thing present, so its profile is a Boy's with the dials
     * turned up and {@code leads()} added. It never retreats: an Ork mob that breaks is a Warboss's
     * problem, not a Nob's.
     */
    private static final FCCombatProfile PROFILE = FCCombatProfile.builder()
            .range(4.0F, 2.0F, 0.0F)
            .accuracy(0.6F)
            .speeds(1.05D, 1.0D)
            .courage(0.95F)
            .retreatHealthThreshold(0.05F)
            .usesCover(false)
            .backsAwayFromMelee(false)
            .melee(3.2F, 18)
            .perception(20, 32.0D, 120)
            .leads(16, 2.5D)
            .build();

    // NOT a field initializer: registerGoals() runs from the super constructor, BEFORE subclass
    // field initializers, so a `= new FCSquad(this)` here would still be null when FCLeaderGoal is
    // built. Same trap the Guardsman Sergeant documents. Created lazily via getSquad().
    private FCSquad squad;

    public OrkNobEntity(EntityType<? extends OrkNobEntity> entityType, Level level) {
        super(entityType, level);

        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(FCRegistry.CHOPPA.get()));
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 55.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 7.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // =========================
    // FCUnit / FCSquadLeader
    // =========================

    @Override
    public FirstCrusadeFaction getUnitFaction() {
        return FirstCrusadeFaction.ORKS;
    }

    @Override
    public UnitRole getUnitRole() {
        return UnitRole.SQUAD_LEADER;
    }

    @Override
    public FCCombatProfile getCombatProfile() {
        return PROFILE;
    }

    @Override
    public String getUnitId() {
        return "ork_nob";
    }

    @Override
    public FCSquad getSquad() {
        if (this.squad == null) {
            this.squad = new FCSquad(this);
        }

        return this.squad;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Sets no goal flags, so it layers under the melee goal rather than competing with it: a
        // Nob fights normally and the mob organises itself around wherever it ends up.
        // Above the leader's own combat goals: an order the enemy can veto is not an order, and
        // a squad told to fall back has to be able to break contact. It only engages when the order
        // points somewhere the leader is not already standing — see FCSquadOrderGoal.
        this.goalSelector.addGoal(0,
                new com.example.examplemod.ai.formation.FCSquadOrderGoal(this, getSquad()));

        this.goalSelector.addGoal(1, new FCLeaderGoal(this, getSquad(),
                FirstCrusadePerformanceConfig.squadFollowerCap(
                        getUnitFaction(), PROFILE.maxFollowers())));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.05D, true));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    @Override
    public void die(DamageSource damageSource) {
        Entity attacker = damageSource.getEntity();

        if (!this.level().isClientSide) {
            if (attacker instanceof GuardsmanEntity guardsman) {
                guardsman.recordOrkKill(5);
            }

            // Release the mob so Boyz stop forming up on a corpse, and so each of them learns it is
            // unattached rather than being left pointing at a dead Nob. See FCSquad.
            getSquad().disband();
        }

        super.die(damageSource);
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

    // Publish it to the mob. Free: the Nob had already chosen for itself, and every Boy that
    // inherits this skips its own scan. Done by overriding setTarget rather than in a tick hook
    // because a Nob acquires targets through its target selector, which has no per-tick entry
    // point of its own the way the Guardsman Sergeant's customServerAiStep does.
    getSquad().setSharedTarget(target);
}

    // ===== GeckoLib (§29) =====
    //
    // Os nomes abaixo tem de bater com as chaves dentro de
    // assets/firstcrusade/animations/ork_nob.animation.json, que sao geradas por
    // tools/generate_ork_assets.py. Mexer num sem o outro e o unico jeito de partir isto.

    private static final RawAnimation FC_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation FC_WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation FC_ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache fcCache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);

        if (hit) {
            triggerAnim("attack", "attack");
        }

        return hit;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, state ->
                state.isMoving() ? state.setAndContinue(FC_WALK) : state.setAndContinue(FC_IDLE)));

        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", FC_ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.fcCache;
    }
}
