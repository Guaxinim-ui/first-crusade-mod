package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A living Ork Camp. It keeps a standing garrison of Boyz, builds WAAAGH! over time, and once
 * the WAAAGH! peaks it hurls a war party at the city it threatens. Destroy the camp block to
 * silence the threat at its source — making the camp a natural objective for a sortie.
 */
public class OrkCampBlockEntity extends BlockEntity {
    private static final String CAMP_ORK_TAG = "FirstCrusadeCampOrk";
    private static final String CAMP_POS_TAG = "FirstCrusadeCampPos";

    private static final int GARRISON_SIZE = 4;
    private static final int GARRISON_RADIUS = 24;
    private static final int WAAAGH_PER_CYCLE = 8;
    private static final int WAAAGH_THRESHOLD = 100;
    private static final int WAR_PARTY_SIZE = 4;

    private static final int WAR_PARTIES_BEFORE_WARBOSS = 3;

    private BlockPos targetCorePos;
    private OrkClan clan = OrkClan.GOFFS;
    private int waaagh = 0;
    private int warPartiesLaunched = 0;
    private boolean warbossSpawned = false;
    private int tickCounter = 0;

    public OrkCampBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.ORK_CAMP_BLOCK_ENTITY.get(), pos, state);
    }

    public void setTargetCore(BlockPos corePos) {
        this.targetCorePos = corePos == null ? null : corePos.immutable();
        setChanged();
    }

    public void setClan(OrkClan clan) {
        this.clan = clan == null ? OrkClan.GOFFS : clan;
        setChanged();
    }

    public OrkClan getClan() {
        return this.clan;
    }

    public int getWaaagh() {
        return this.waaagh;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OrkCampBlockEntity camp) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        camp.tickCounter++;

        if (camp.tickCounter < 200) {
            return;
        }

        camp.tickCounter = 0;

        camp.maintainGarrison(serverLevel, pos);
        camp.buildWaaagh(serverLevel, pos);
    }

    private void maintainGarrison(ServerLevel serverLevel, BlockPos pos) {
        int current = countCampOrks(serverLevel, pos);

        for (int i = current; i < GARRISON_SIZE; i++) {
            spawnCampOrk(serverLevel, pos);
        }
    }

    private void buildWaaagh(ServerLevel serverLevel, BlockPos pos) {
        // The global WAAAGH! tier makes every camp gather momentum faster.
        int tier = WaaaghOverlordManager.getTier(serverLevel);
        this.waaagh += WAAAGH_PER_CYCLE + tier * 3;
        setChanged();

        if (this.waaagh >= WAAAGH_THRESHOLD) {
            launchWarParty(serverLevel, pos);
            this.waaagh = 0;
            setChanged();
        }
    }

    private void launchWarParty(ServerLevel serverLevel, BlockPos pos) {
        if (this.targetCorePos == null) {
            return;
        }

        int tier = WaaaghOverlordManager.getTier(serverLevel);

        // Composition varies by clan: Goffs swarm Boyz, Bad Moons bring Nobz, Deathskulls grots...
        int boyz = WAR_PARTY_SIZE + tier + this.clan.getBonusBoyz();
        for (int i = 0; i < boyz; i++) {
            dispatchMarcher(serverLevel, pos, ExampleMod.ORK_BOY.get().create(serverLevel));
        }

        for (int i = 0; i < this.clan.getNobz(); i++) {
            dispatchMarcher(serverLevel, pos, ExampleMod.ORK_NOB.get().create(serverLevel));
        }

        // Grots tag along as fodder (Deathskulls bring extra, Snakebites fewer).
        int gretchin = Math.max(0, 2 + this.clan.getBonusGretchin());
        for (int i = 0; i < gretchin; i++) {
            dispatchMarcher(serverLevel, pos, ExampleMod.GRETCHIN.get().create(serverLevel));
        }

        // A grown WAAAGH! (camp Warboss risen, or global tier 2+) fields Meganobz at the head.
        int meganobz = (this.warbossSpawned ? 1 : 0) + Math.max(0, tier - 1);
        for (int i = 0; i < meganobz; i++) {
            dispatchMarcher(serverLevel, pos, ExampleMod.MEGANOB.get().create(serverLevel));
        }

        // The greatest WAAAGH!s (global tier 3+) roll out a Killa Kan war machine.
        if (tier >= 3) {
            dispatchMarcher(serverLevel, pos, ExampleMod.KILLA_KAN.get().create(serverLevel));
        }

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                this.targetCorePos,
                "WAAAGH! A " + this.clan.getDisplayName() + " war party (" + this.clan.getTactics() + ") marches on the city!"
        );

        this.warPartiesLaunched++;
        setChanged();

        if (!this.warbossSpawned && this.warPartiesLaunched >= WAR_PARTIES_BEFORE_WARBOSS) {
            spawnWarboss(serverLevel, pos);
        }
    }

    // Positions an Ork at the camp, applies its clan profile, and sends it marching on the city.
    private void dispatchMarcher(ServerLevel serverLevel, BlockPos pos, Mob ork) {
        if (ork == null) {
            return;
        }

        BlockPos spawnPos = groundPos(serverLevel, pos.offset(serverLevel.random.nextInt(5) - 2, 0, serverLevel.random.nextInt(5) - 2));

        ork.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        this.clan.applyTo(ork);

        ork.getNavigation().moveTo(
                this.targetCorePos.getX() + 0.5D,
                this.targetCorePos.getY(),
                this.targetCorePos.getZ() + 0.5D,
                1.1D
        );

        ork.setPersistenceRequired();
        serverLevel.addFreshEntity(ork);
    }

    // Once the WAAAGH! has built enough momentum, the camp's biggest Ork rises to lead it.
    private void spawnWarboss(ServerLevel serverLevel, BlockPos pos) {
        if (this.targetCorePos == null) {
            return;
        }

        WarbossEntity warboss = ExampleMod.WARBOSS.get().create(serverLevel);

        if (warboss == null) {
            return;
        }

        BlockPos spawnPos = groundPos(serverLevel, pos);

        warboss.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        this.clan.applyTo(warboss);
        warboss.setTargetCore(this.targetCorePos);
        warboss.setHealth(warboss.getMaxHealth());

        serverLevel.addFreshEntity(warboss);

        this.warbossSpawned = true;
        setChanged();

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                this.targetCorePos,
                "A " + this.clan.getDisplayName() + " Warboss rises to lead the WAAAGH! against the city!"
        );
    }

    private int countCampOrks(ServerLevel serverLevel, BlockPos pos) {
        List<OrkBoyEntity> orks = serverLevel.getEntitiesOfClass(
                OrkBoyEntity.class,
                garrisonBox(pos),
                ork -> ork.isAlive() && isCampOrk(ork, pos)
        );

        return orks.size();
    }

    private void spawnCampOrk(ServerLevel serverLevel, BlockPos pos) {
        OrkBoyEntity ork = ExampleMod.ORK_BOY.get().create(serverLevel);

        if (ork == null) {
            return;
        }

        BlockPos spawnPos = groundPos(serverLevel, pos.offset(serverLevel.random.nextInt(9) - 4, 0, serverLevel.random.nextInt(9) - 4));

        ork.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        this.clan.applyTo(ork);
        markAsCampOrk(ork, pos);
        ork.setPersistenceRequired();
        serverLevel.addFreshEntity(ork);
    }

    private static void markAsCampOrk(Mob mob, BlockPos campPos) {
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(CAMP_ORK_TAG, true);
        data.putLong(CAMP_POS_TAG, campPos.asLong());
    }

    private static boolean isCampOrk(Mob mob, BlockPos campPos) {
        CompoundTag data = mob.getPersistentData();
        return data.getBoolean(CAMP_ORK_TAG) && data.getLong(CAMP_POS_TAG) == campPos.asLong();
    }

    private BlockPos groundPos(ServerLevel serverLevel, BlockPos around) {
        return serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
    }

    private AABB garrisonBox(BlockPos pos) {
        return new AABB(
                pos.getX() - GARRISON_RADIUS,
                pos.getY() - 32,
                pos.getZ() - GARRISON_RADIUS,
                pos.getX() + GARRISON_RADIUS,
                pos.getY() + 48,
                pos.getZ() + GARRISON_RADIUS
        );
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt("Waaagh", this.waaagh);
        tag.putInt("WarPartiesLaunched", this.warPartiesLaunched);
        tag.putBoolean("WarbossSpawned", this.warbossSpawned);
        tag.putString("Clan", this.clan.name());

        if (this.targetCorePos != null) {
            tag.putLong("TargetCorePos", this.targetCorePos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        this.waaagh = tag.getInt("Waaagh");
        this.warPartiesLaunched = tag.getInt("WarPartiesLaunched");
        this.warbossSpawned = tag.getBoolean("WarbossSpawned");
        this.clan = OrkClan.fromName(tag.getString("Clan"));

        if (tag.contains("TargetCorePos")) {
            this.targetCorePos = BlockPos.of(tag.getLong("TargetCorePos"));
        } else {
            this.targetCorePos = null;
        }
    }
}
