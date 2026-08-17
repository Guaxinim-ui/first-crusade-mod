package com.example.examplemod;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class GuardsmanEntity extends PathfinderMob
        implements RangedAttackMob, LasgunAimingEntity, ImperialTroopVisuals {
    private static final EntityDataAccessor<Integer> LASGUN_COMBAT_POSE = SynchedEntityData.defineId(GuardsmanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LASGUN_COMBAT_TICKS = SynchedEntityData.defineId(GuardsmanEntity.class, EntityDataSerializers.INT);

    /** Which individual this Guardsman is. Rolled once, then his for life. */
    private static final EntityDataAccessor<Integer> VISUAL_VARIANT = SynchedEntityData.defineId(GuardsmanEntity.class, EntityDataSerializers.INT);

    /**
     * Which wardrobe his career has earned him.
     *
     * <p>Synced separately from the rank itself because the rank is a server-side field with eight
     * values and a pile of stats attached, while the client needs exactly one thing from it: which
     * of three texture sets to draw. Sending the whole rank to the client would be sending the
     * client a promotion system it has no use for.
     */
    private static final EntityDataAccessor<Byte> VISUAL_GRADE = SynchedEntityData.defineId(GuardsmanEntity.class, EntityDataSerializers.BYTE);

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
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LASGUN_COMBAT_POSE, LasgunCombatPose.IDLE.ordinal());
        this.entityData.define(LASGUN_COMBAT_TICKS, POSE_NEVER_STARTED);

        // Rolled at the one point every spawn path passes through. See the same note on
        // AbstractImperialTroopEntity: the client's roll lives for a frame and is then replaced by
        // the server's, and a save pins it for good.
        this.entityData.define(VISUAL_VARIANT,
                this.random.nextInt(ImperialTroopAppearance.variantCount(this.appearanceKey())));
        this.entityData.define(VISUAL_GRADE, (byte) ImperialTroopGrade.LINE.ordinal());
    }

    // ==================================================================== appearance

    @Override
    public String appearanceKey() {
        return "guardsman";
    }

    @Override
    public int getVisualVariant() {
        return this.entityData.get(VISUAL_VARIANT);
    }

    public void setVisualVariant(int variant) {
        this.entityData.set(VISUAL_VARIANT, Math.max(0, variant));
    }

    @Override
    public ImperialTroopGrade getVisualGrade() {
        ImperialTroopGrade[] grades = ImperialTroopGrade.values();
        int index = this.entityData.get(VISUAL_GRADE);
        return index >= 0 && index < grades.length ? grades[index] : ImperialTroopGrade.LINE;
    }

    /**
     * Pushes the current rank's look onto the wire.
     *
     * <p>Called from every place a rank is set. It changes nothing but the picture: the entity is
     * the same object it was a tick ago, so the UUID, the name, the merit, the Ork tally, the
     * Command Core and the gear all carry through a promotion untouched.
     */
    private void refreshVisualGrade() {
        this.entityData.set(VISUAL_GRADE,
                (byte) ImperialTroopGrade.forRank(this.guardsmanRank).ordinal());
    }

    @Override
    public LasgunCombatPose getLasgunCombatPose() {
        return LasgunCombatPose.fromId(this.entityData.get(LASGUN_COMBAT_POSE));
    }

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
        this.goalSelector.addGoal(0, new OpenDoorGoal(this, true));

        this.goalSelector.addGoal(1, new GuardsmanKnifeAttackGoal(this, 1.15D, 4.0D));
        this.goalSelector.addGoal(2, new ImperialLasgunAttackGoal<>(this, 1.0D, 35, 18.0F, 4.0F));

        // Loose around the base, not pinned to a post. GuardsmanGuardPostGoal pulled the soldier to
        // an exact block that the patrol manager rotated every thirty seconds, so a garrison marched
        // in circles forever; this goal does nothing at all until the soldier is genuinely far from
        // home. The stroll's long interval is the other half: standing still is the default.
        this.goalSelector.addGoal(4, new LightweightReturnToBaseGoal(this, this::getCommandCorePos,
                1.0D, SimpleImperialBaseBalance.RETURN_TRIGGER_DISTANCE));

        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D,
                SimpleImperialBaseBalance.STROLL_INTERVAL_TICKS));
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
        this.refreshVisualGrade();
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

    /**
     * Promotes as far as merit — and the base's establishment — allow.
     *
     * <p>Merit alone used to decide this, and the result was that every soldier who survived long
     * enough became a Sergeant and then a Commander. A rank everyone reaches carries no information,
     * so the base now holds a quota: {@link com.example.examplemod.crusade.ImperialSoldierCareerManager}
     * answers whether this particular soldier may wear this particular grade at this base right now.
     * Merit that cannot be spent is not lost — it sits there until a slot opens, which is what makes
     * a Sergeant's death matter to the man behind him.
     */
    private void tryPromoteFromMerit() {
        GuardsmanRank nextRank = this.guardsmanRank.getNextRank();

        while (nextRank != null && this.merit >= nextRank.getRequiredMerit()) {
            if (!mayHold(nextRank)) {
                break;
            }

            this.setRank(nextRank, true);
            nextRank = this.guardsmanRank.getNextRank();
        }

        // Whatever he ended up as, the record follows. A promotion never recreates the soldier: same
        // entity, same UUID, same name, same tally.
        if (this.level() instanceof ServerLevel serverLevel && this.commandCorePos != null) {
            com.example.examplemod.crusade.ImperialSoldierCareerManager.setGrade(
                    serverLevel, this.commandCorePos, this.getUUID(),
                    ImperialTroopGrade.forRank(this.guardsmanRank));
        }
    }

    /** Whether the base this soldier belongs to has room for him at that rank. */
    private boolean mayHold(GuardsmanRank rank) {
        if (!(this.level() instanceof ServerLevel serverLevel) || this.commandCorePos == null) {
            // Not attached to a base: nobody is keeping an establishment, so the old rule applies.
            return true;
        }

        return com.example.examplemod.crusade.ImperialSoldierCareerManager.mayPromoteTo(
                serverLevel, this.commandCorePos, ImperialTroopGrade.forRank(rank));
    }

    public void setRank(GuardsmanRank rank, boolean healToFull) {
        this.guardsmanRank = rank;
        this.refreshVisualGrade();

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
            this.setItemSlot(EquipmentSlot.FEET, new ItemStack(FCRegistry.GUARDSMAN_BOOTS.get()));
            this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FCRegistry.GUARDSMAN_LEGGINGS.get()));
        } else {
            this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }

        if (equipmentTier >= 3) {
            this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FCRegistry.GUARDSMAN_CHESTPLATE.get()));
        } else {
            this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }

        if (equipmentTier >= 4) {
            this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FCRegistry.GUARDSMAN_HELMET.get()));
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

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        this.performLasgunAttack(target);
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

        if (!currentItem.is(FCRegistry.LASGUN.get())) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.LASGUN.get()));
        }
    }

    public void setUsingKnife() {
        ItemStack currentItem = this.getMainHandItem();

        if (!currentItem.is(FCRegistry.GUARDSMAN_COMBAT_KNIFE.get())) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FCRegistry.GUARDSMAN_COMBAT_KNIFE.get()));
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

        tag.putInt(AbstractImperialTroopEntity.VISUAL_VARIANT_TAG, this.getVisualVariant());

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
        this.refreshVisualGrade();

        // Absent on soldiers saved before the appearance system: they keep the roll their
        // constructor made, and it is written out from the next save on.
        if (tag.contains(AbstractImperialTroopEntity.VISUAL_VARIANT_TAG)) {
            this.setVisualVariant(tag.getInt(AbstractImperialTroopEntity.VISUAL_VARIANT_TAG));
        }

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