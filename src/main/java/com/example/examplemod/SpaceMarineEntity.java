package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

public class SpaceMarineEntity extends PathfinderMob {
    // A Neophyte must be blooded in battle (score a kill) before being made a full Battle-Brother:
    // a short trial after proving itself, or — failing any war — a long autonomous maturation.
    private static final int NEOPHYTE_TRIAL_MIN_TICKS = 1200;   // ~1 min after a confirmed kill
    private static final int NEOPHYTE_FALLBACK_TICKS = 24000;   // ~20 min if no battle ever comes

    private BlockPos commandCorePos;
    private BlockPos guardPostPos;

    private boolean neophyte = false;
    private int neophyteTicks = 0;
    private boolean battleProven = false;

    public SpaceMarineEntity(EntityType<? extends SpaceMarineEntity> entityType, Level level) {
        super(entityType, level);
        prepareSpaceMarine();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 90.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 72.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35D);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);

        prepareSpaceMarine();
        this.setHealth(this.getMaxHealth());

        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.level().isClientSide) {
            return;
        }

        if (this.getTarget() != null && !FirstCrusadeFactionManager.canAttack(this, this.getTarget())) {
            this.setTarget(null);
        }

        if (this.neophyte) {
            this.neophyteTicks++;

            boolean trialComplete = (this.battleProven && this.neophyteTicks >= NEOPHYTE_TRIAL_MIN_TICKS)
                    || this.neophyteTicks >= NEOPHYTE_FALLBACK_TICKS;

            if (trialComplete) {
                matureIntoFullMarine();
            }
        }

        if (this.tickCount % 100 == 0) {
            prepareSpaceMarine();
            moveToGuardPostIfNeeded();
        }
    }

    private void prepareSpaceMarine() {
        updateNameForState();
        this.setPersistenceRequired();

        equipAsSpaceMarine();
    }

    private void updateNameForState() {
        this.setCustomName(Component.literal(this.neophyte ? "Neophyte" : "Space Marine"));
        this.setCustomNameVisible(true);
    }

    // Marks a freshly ascended Guardsman as a Neophyte with reduced implants. Called by the
    // upgrade manager; the Neophyte then matures on its own (see aiStep).
    public void beginAsNeophyte() {
        this.neophyte = true;
        this.neophyteTicks = 0;
        this.battleProven = false;

        applyNeophyteStats();
        updateNameForState();
    }

    public boolean isNeophyte() {
        return this.neophyte;
    }

    // The battle test: called when this Neophyte slays a foe, after which it can be made a full
    // Battle-Brother (see aiStep). Has no effect on a Marine that has already ascended.
    public void markBattleProven() {
        if (this.neophyte) {
            this.battleProven = true;
        }
    }

    private void applyNeophyteStats() {
        setBaseAttribute(Attributes.MAX_HEALTH, 55.0D);
        setBaseAttribute(Attributes.ATTACK_DAMAGE, 8.0D);
        setBaseAttribute(Attributes.ARMOR, 12.0D);
        this.setHealth(this.getMaxHealth());
    }

    private void applyFullMarineStats() {
        setBaseAttribute(Attributes.MAX_HEALTH, 90.0D);
        setBaseAttribute(Attributes.ATTACK_DAMAGE, 13.0D);
        setBaseAttribute(Attributes.ARMOR, 18.0D);
        this.setHealth(this.getMaxHealth());
    }

    private void matureIntoFullMarine() {
        this.neophyte = false;
        this.neophyteTicks = 0;
        this.battleProven = false;

        applyFullMarineStats();
        updateNameForState();

        if (this.commandCorePos != null && this.level() instanceof ServerLevel serverLevel) {
            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    this.commandCorePos,
                    Component.translatable("msg.firstcrusade.bcast.neophyte_matured")
            );
        }
    }

    private void setBaseAttribute(Attribute attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);

        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void moveToGuardPostIfNeeded() {
        if (this.guardPostPos == null) {
            return;
        }

        if (this.getTarget() != null) {
            return;
        }

        double distance = this.distanceToSqr(
                this.guardPostPos.getX() + 0.5D,
                this.guardPostPos.getY(),
                this.guardPostPos.getZ() + 0.5D
        );

        if (distance > 36.0D) {
            this.getNavigation().moveTo(
                    this.guardPostPos.getX() + 0.5D,
                    this.guardPostPos.getY(),
                    this.guardPostPos.getZ() + 0.5D,
                    1.05D
            );
        }
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

    public void equipAsSpaceMarine() {
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.CHAINSWORD.get()));

        this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        this.setDropChance(EquipmentSlot.LEGS, 0.0F);
        this.setDropChance(EquipmentSlot.FEET, 0.0F);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos;
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    public BlockPos getCommandCorePos() {
        return this.commandCorePos;
    }

    public void assignGuardPost(BlockPos guardPostPos) {
        this.guardPostPos = guardPostPos;
    }

    public BlockPos getGuardPostPos() {
        return this.guardPostPos;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        if (this.level().isClientSide) {
            return;
        }

        if (this.commandCorePos == null) {
            return;
        }

        if (this.level().getBlockEntity(this.commandCorePos) instanceof ImperialCommandCoreBlockEntity commandCore) {
            commandCore.onAssignedGuardsmanDeath();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (this.commandCorePos != null) {
            tag.putLong("CommandCorePos", this.commandCorePos.asLong());
        }

        if (this.guardPostPos != null) {
            tag.putLong("GuardPostPos", this.guardPostPos.asLong());
        }

        tag.putBoolean("Neophyte", this.neophyte);
        tag.putInt("NeophyteTicks", this.neophyteTicks);
        tag.putBoolean("BattleProven", this.battleProven);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("CommandCorePos")) {
            this.commandCorePos = BlockPos.of(tag.getLong("CommandCorePos"));
        }

        if (tag.contains("GuardPostPos")) {
            this.guardPostPos = BlockPos.of(tag.getLong("GuardPostPos"));
        }

        this.neophyte = tag.getBoolean("Neophyte");
        this.neophyteTicks = tag.getInt("NeophyteTicks");
        this.battleProven = tag.getBoolean("BattleProven");

        prepareSpaceMarine();

        if (this.neophyte) {
            applyNeophyteStats();
        }
    }
}