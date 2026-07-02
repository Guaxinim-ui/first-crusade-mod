package com.example.examplemod;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
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
public abstract class AbstractImperialTroopEntity extends PathfinderMob {
    private BlockPos commandCorePos;
    private BlockPos guardPostPos;

    protected AbstractImperialTroopEntity(EntityType<? extends AbstractImperialTroopEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Combat (priority 2) is added by the subclass.
        registerCombatGoals();

        // Return to the assigned patrol post when idle, then idle behaviours.
        this.goalSelector.addGoal(3, new ImperialTroopGuardPostGoal(this, 1.0D, 3.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8D));

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
