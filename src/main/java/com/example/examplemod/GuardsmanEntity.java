package com.example.examplemod;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class GuardsmanEntity extends PathfinderMob {
    private BlockPos commandCorePos;
    private BlockPos guardPostPos;

    private GuardsmanRank guardsmanRank = GuardsmanRank.RECRUIT;
    private ImperiumChapter chapter = ImperiumChapter.IMPERIAL_STANDARD;
    private GuardsmanSpecialization specialization = GuardsmanSpecialization.NONE;
    private ImperialCityType cityType = ImperialCityType.CIVILISED;

    private boolean chapterAssigned = false;

    private int merit = 0;
    private int orkKills = 0;

    public GuardsmanEntity(EntityType<? extends GuardsmanEntity> entityType, Level level) {
        super(entityType, level);

        this.setUsingLasgun();
        this.setPersistenceRequired();
        this.updateRankName();

        // City buildings have real wooden doors — Guardsmen must open and path through them.
        if (this.getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new OpenDoorGoal(this, true));

        this.goalSelector.addGoal(1, new GuardsmanKnifeAttackGoal(this, 1.15D, 4.0D));
        this.goalSelector.addGoal(2, new GuardsmanLasgunAttackGoal(this, 1.0D, 35, 18.0F, 4.0F));

        this.goalSelector.addGoal(4, new GuardsmanGuardPostGoal(this, 1.0D, 8.0D));

        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new FirstCrusadeHurtByTargetGoal(this));
this.targetSelector.addGoal(2, new FirstCrusadeNearestEnemyTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            this.ensureChapterAssigned();

            if (this.specialization.isSpecialist() && this.tickCount % 40 == 0) {
                this.performSpecializationBehavior();
            }
        }
    }

    private void ensureChapterAssigned() {
        if (!this.chapterAssigned) {
            this.assignRandomChapter();
        }
    }

    private void performSpecializationBehavior() {
        switch (this.specialization) {
            case MEDIC -> healNearbyAllies();
            case OFFICER -> buffNearbyAllies();
            case ENGINEER -> repairCommandCore();
            default -> {
            }
        }
    }

    private void healNearbyAllies() {
        List<GuardsmanEntity> allies = this.level().getEntitiesOfClass(
                GuardsmanEntity.class,
                this.getBoundingBox().inflate(8.0D),
                ally -> ally.isAlive() && ally.getHealth() < ally.getMaxHealth()
        );

        for (GuardsmanEntity ally : allies) {
            ally.heal(2.0F);
        }

        if (this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }
    }

    private void buffNearbyAllies() {
        List<GuardsmanEntity> allies = this.level().getEntitiesOfClass(
                GuardsmanEntity.class,
                this.getBoundingBox().inflate(10.0D),
                ally -> ally.isAlive() && ally != this
        );

        for (GuardsmanEntity ally : allies) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, false));
        }
    }

    private void repairCommandCore() {
        if (this.commandCorePos == null) {
            return;
        }

        double distance = this.distanceToSqr(
                this.commandCorePos.getX() + 0.5D,
                this.commandCorePos.getY() + 0.5D,
                this.commandCorePos.getZ() + 0.5D
        );

        if (distance > 12.0D * 12.0D) {
            return;
        }

        BlockEntity blockEntity = this.level().getBlockEntity(this.commandCorePos);

        if (blockEntity instanceof ImperialCommandCoreBlockEntity commandCore) {
            commandCore.engineerRepair(1);
        }
    }

    public void assignToCommandCore(BlockPos commandCorePos) {
        this.commandCorePos = commandCorePos;
        this.setPersistenceRequired();
    }

    public BlockPos getCommandCorePos() {
        return this.commandCorePos;
    }

    public boolean isAssignedToCommandCore(BlockPos commandCorePos) {
        return this.commandCorePos != null && this.commandCorePos.equals(commandCorePos);
    }

    public void assignGuardPost(BlockPos guardPostPos) {
        this.guardPostPos = guardPostPos;
        this.setPersistenceRequired();
    }

    public BlockPos getGuardPostPos() {
        return this.guardPostPos;
    }

    public boolean hasGuardPost() {
        return this.guardPostPos != null;
    }

    public void clearGuardPost() {
        this.guardPostPos = null;
    }

    public void initializeFromCity(GuardsmanRank startingRank) {
        initializeFromCity(startingRank, this.cityType);
    }

    public void initializeFromCity(GuardsmanRank startingRank, ImperialCityType cityType) {
        if (startingRank == null) {
            startingRank = GuardsmanRank.RECRUIT;
        }

        this.guardsmanRank = startingRank;
        this.cityType = cityType == null ? ImperialCityType.CIVILISED : cityType;
        this.merit = Math.max(this.merit, startingRank.getRequiredMerit());

        this.applyRankStats(true);
        this.updateEquipmentByRank();
        this.updateRankName();
    }

    public void setCityType(ImperialCityType cityType, boolean healToFull) {
        this.cityType = cityType == null ? ImperialCityType.CIVILISED : cityType;

        this.applyRankStats(healToFull);
        this.updateEquipmentByRank();
        this.updateRankName();
    }

    public ImperialCityType getCityType() {
        return this.cityType;
    }

    public void assignRandomChapter() {
        this.chapter = ImperiumChapter.getRandom(this.getRandom());
        this.chapterAssigned = true;

        this.applyRankStats(true);
        this.updateEquipmentByRank();
        this.updateRankName();
    }

    public void setChapter(ImperiumChapter chapter, boolean healToFull) {
        this.chapter = chapter;
        this.chapterAssigned = true;

        this.applyRankStats(healToFull);
        this.updateEquipmentByRank();
        this.updateRankName();
    }

    public void recordOrkKill(int gainedMerit) {
        if (this.level().isClientSide) {
            return;
        }

        this.orkKills++;
        this.addMerit(gainedMerit);
    }

    public void addMerit(int amount) {
        if (amount <= 0) {
            return;
        }

        this.merit += amount;
        this.tryPromoteFromMerit();
        this.updateRankName();
    }

    private void tryPromoteFromMerit() {
        GuardsmanRank nextRank = this.guardsmanRank.getNextRank();

        while (nextRank != null && this.merit >= nextRank.getRequiredMerit()) {
            this.setRank(nextRank, true);
            nextRank = this.guardsmanRank.getNextRank();
        }
    }

    public void setRank(GuardsmanRank rank, boolean healToFull) {
        this.guardsmanRank = rank;

        this.applyRankStats(healToFull);
        this.updateEquipmentByRank();
        this.updateRankName();
    }

    private void applyRankStats(boolean healToFull) {
        AttributeInstance maxHealthAttribute = this.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attackDamageAttribute = this.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance armorAttribute = this.getAttribute(Attributes.ARMOR);
        AttributeInstance movementSpeedAttribute = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(Math.max(1.0D, this.guardsmanRank.getMaxHealth() + this.chapter.getMaxHealthBonus() + this.specialization.getMaxHealthBonus() + this.cityType.getMaxHealthBonus()));
        }

        if (attackDamageAttribute != null) {
            attackDamageAttribute.setBaseValue(Math.max(0.0D, this.guardsmanRank.getAttackDamage() + this.chapter.getAttackDamageBonus() + this.specialization.getAttackDamageBonus() + this.cityType.getAttackDamageBonus()));
        }

        if (armorAttribute != null) {
            armorAttribute.setBaseValue(Math.max(0.0D, this.guardsmanRank.getArmor() + this.chapter.getArmorBonus() + this.specialization.getArmorBonus() + this.cityType.getArmorBonus()));
        }

        if (movementSpeedAttribute != null) {
            movementSpeedAttribute.setBaseValue(Math.max(0.1D, 0.28D + this.chapter.getMovementSpeedBonus() + this.specialization.getMovementSpeedBonus() + this.cityType.getMovementSpeedBonus()));
        }

        if (healToFull || this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void updateEquipmentByRank() {
        int equipmentTier = this.guardsmanRank.getEquipmentTier();

        if (equipmentTier >= 2) {
            this.setItemSlot(EquipmentSlot.FEET, new ItemStack(ExampleMod.GUARDSMAN_BOOTS.get()));
            this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ExampleMod.GUARDSMAN_LEGGINGS.get()));
        } else {
            this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }

        if (equipmentTier >= 3) {
            this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ExampleMod.GUARDSMAN_CHESTPLATE.get()));
        } else {
            this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }

        if (equipmentTier >= 4) {
            this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ExampleMod.GUARDSMAN_HELMET.get()));
        } else {
            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }

    private void updateRankName() {
        String specTag = this.specialization.isSpecialist() ? " {" + this.specialization.getShortTag() + "}" : "";

        // Show the city regiment name unless it is the same as the plain rank (e.g. Civilised "Guardsman").
        String troopName = this.cityType.getTroopName();
        String regimentTag = troopName.equals(this.guardsmanRank.getDisplayName()) ? "" : troopName + " ";

        this.setCustomName(Component.literal(
                "[" + this.chapter.getShortName() + "] " + regimentTag + this.guardsmanRank.getDisplayName() + specTag + " [" + this.merit + "M]"
        ));

        this.setCustomNameVisible(true);
    }

    public void setSpecialization(GuardsmanSpecialization specialization, boolean healToFull) {
        this.specialization = specialization == null ? GuardsmanSpecialization.NONE : specialization;

        this.applyRankStats(healToFull);
        this.updateEquipmentByRank();
        this.updateRankName();
    }

    public GuardsmanSpecialization getSpecialization() {
        return this.specialization;
    }

    public boolean hasSpecialization() {
        return this.specialization.isSpecialist();
    }

    public GuardsmanRank getGuardsmanRank() {
        return this.guardsmanRank;
    }

    public ImperiumChapter getChapter() {
        return this.chapter;
    }

    public int getMerit() {
        return this.merit;
    }

    public int getOrkKills() {
        return this.orkKills;
    }

    public int getCommandCapacity() {
        return this.guardsmanRank.getCommandCapacity();
    }

    public int getEquipmentTier() {
        return this.guardsmanRank.getEquipmentTier();
    }

    public boolean canCommandTroops() {
        return this.guardsmanRank.canCommandTroops();
    }

    public double getLasgunDamageWithBonuses() {
        return Math.max(1.0D, this.guardsmanRank.getLasgunDamage() + this.chapter.getLasgunDamageBonus() + this.specialization.getLasgunDamageBonus() + this.cityType.getLasgunDamageBonus());
    }

    public void performLasgunAttack(LivingEntity target) {
        if (this.level().isClientSide) {
            return;
        }

        this.setUsingLasgun();

        LasgunShotEntity projectile = new LasgunShotEntity(this.level(), this);
        projectile.setBaseDamage(this.getLasgunDamageWithBonuses());
        projectile.setKnockback(0);

        double xPower = target.getX() - this.getX();
        double yPower = target.getY(0.5D) - projectile.getY();
        double zPower = target.getZ() - this.getZ();

        double horizontalDistance = Math.sqrt(xPower * xPower + zPower * zPower);

        projectile.shoot(
                xPower,
                yPower + horizontalDistance * 0.05D,
                zPower,
                3.4F,
                0.6F
        );

        this.level().addFreshEntity(projectile);

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE,
                0.55F,
                1.8F
        );
    }

    public void setUsingLasgun() {
        ItemStack currentItem = this.getMainHandItem();

        if (!currentItem.is(ExampleMod.LASGUN.get())) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.LASGUN.get()));
        }
    }

    public void setUsingKnife() {
        ItemStack currentItem = this.getMainHandItem();

        if (!currentItem.is(ExampleMod.GUARDSMAN_COMBAT_KNIFE.get())) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.GUARDSMAN_COMBAT_KNIFE.get()));
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide && this.commandCorePos != null) {
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

        tag.putString("GuardsmanRank", this.guardsmanRank.name());
        tag.putString("ImperiumChapter", this.chapter.name());
        tag.putString("Specialization", this.specialization.name());
        tag.putString("CityType", this.cityType.name());
        tag.putBoolean("ChapterAssigned", this.chapterAssigned);

        tag.putInt("Merit", this.merit);
        tag.putInt("OrkKills", this.orkKills);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.getBoolean("HasCommandCore")) {
            int x = tag.getInt("CommandCoreX");
            int y = tag.getInt("CommandCoreY");
            int z = tag.getInt("CommandCoreZ");

            this.commandCorePos = new BlockPos(x, y, z);
        }

        if (tag.getBoolean("HasGuardPost")) {
            int x = tag.getInt("GuardPostX");
            int y = tag.getInt("GuardPostY");
            int z = tag.getInt("GuardPostZ");

            this.guardPostPos = new BlockPos(x, y, z);
        }

        this.guardsmanRank = GuardsmanRank.fromName(tag.getString("GuardsmanRank"));
        this.chapter = ImperiumChapter.fromName(tag.getString("ImperiumChapter"));
        this.specialization = GuardsmanSpecialization.fromName(tag.getString("Specialization"));
        this.cityType = ImperialCityType.fromName(tag.getString("CityType"));
        this.chapterAssigned = tag.getBoolean("ChapterAssigned");

        this.merit = tag.getInt("Merit");
        this.orkKills = tag.getInt("OrkKills");

        this.applyRankStats(false);
        this.updateEquipmentByRank();
        this.updateRankName();
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

}