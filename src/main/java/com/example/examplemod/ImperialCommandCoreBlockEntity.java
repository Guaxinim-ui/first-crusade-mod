package com.example.examplemod;

import javax.annotation.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class ImperialCommandCoreBlockEntity extends BlockEntity {
    private static final int MAX_CITY_LEVEL = 5;

    // Passive "Imperial supply line" production is temporary and is being phased out as
    // the city's own staffed work sites take over. A fully self-sufficient city keeps only
    // this fraction of its passive output as an external supply subsidy.
    private static final double PASSIVE_PRODUCTION_FLOOR = 0.2;

    private String baseName = "Imperial Outpost";
    private UUID ownerUUID;
    private String ownerName = "Unclaimed";

    // Settlement flavour (Hive, Forge, Fortress, ...). Assigned on first server tick.
    private ImperialCityType cityType;
    // Size axis, independent of cityType (theme). Defaults to TOWN; set at founding by the seeder.
    // HIVE_CAPITAL is never assigned here — it has its own dedicated founding path.
    private SettlementScale settlementScale = SettlementScale.TOWN;

    private int cityLevel = 1;

    // The city's Governor — a persona (no entity) that runs its politics, economy and building.
    // For an unclaimed city the Governor always rules (autonomous). For a player-owned city the
    // owner may "appoint" the Governor (delegate) so the city keeps progressing while they are away.
    // Personality biases the autonomous decisions and grants a small perk; the name is an id into a
    // shared pool so the GUI can show it without networking a string (see ImperialGovernorManager).
    private ImperialGovernorPersonality governorPersonality;
    private int governorNameId = -1;
    private boolean governanceDelegated = false;

    // The city's six stockpiled resources (Iron, Coal, Scrap, Gold, Emerald, Crusadium) and their
    // capacity-aware fill/spend logic live in a dedicated storage; capacity scales with city level.
    private final ImperialResourceStorage resources = new ImperialResourceStorage(this::getStorageCapacity);

    // Civilian morale (0-100). Eased toward a target each slow tick by ImperialCityMoraleManager.
    private int cityMorale = ImperialCityMoraleManager.DEFAULT_MORALE;

    // While > 0 the city mourns a fallen Primarch and no new one may rise.
    private int primarchMourningCooldownTicks = 0;

    // A single Ork Camp is seeded once the city draws enough attention; its position is
    // remembered so the Primarch can lead a sortie against it.
    private boolean orkCampSeeded = false;
    private BlockPos orkCampPos;

    private int recruitedGuardsmen = 0;

    // Food sustains the population: Farms produce it into the stockpile and citizen growth
    // consumes it. Kept separate from the six tradeable resources (like the Emperor's gene-seed).
    private int food = 0;

    private long lastProductionDay = -1;
    private int tickCounter = 0;

    // Cached counts for the Core GUI, refreshed a few times per second by recomputeMenuStats
    // instead of being recomputed (via block/entity scans) on every tick the menu polls them.
    private static final int STATS_SCAN_RADIUS = 64;
    private int statMineCount;
    private int statGoldMineCount;
    private int statScrapYardCount;
    private int statForgeCount;
    private int statRefineryCount;
    private int statFarmCount;
    private int statTradeDepotCount;
    private int statBarracksCount;
    private int statCitizenCount;
    private int statUnemployedCount;
    private int statMinerCount;
    private int statGoldMinerCount;
    private int statScrapperCount;
    private int statSmithCount;
    private int statStokerCount;
    private int statFarmerCount;
    private int statTraderCount;
    private int statRecruitCount;
    private int statThreatScore;

    // How many players currently have this Core's GUI open. Transient (not saved): the cached menu
    // stats above are only refreshed while this is > 0. See onMenuOpened/onMenuClosed.
    private int openMenuCount;

 private long lastOrkRaidDay = -1;
 private int orkRaidCount = 0;
private boolean activeOrkRaid = false;
private int activeOrkRaidTicks = 0;

private int orkRaidVictories = 0;
private int imperialWarSupport = 0;

private int cityIntegrity = 100;
private int raidPressureTicks = 0;

private int reinforcementCooldownTicks = 0;

private int emperorGeneSeed = 0;

private int spaceMarinePromotionCooldownTicks = 0;
private UUID pendingSpaceMarineCandidateUUID;

// Currently selected specialist type for the Promote Specialist action (1 = first real specialist).
private int selectedSpecialistOrdinal = 1;

public void tryBuildImperialMine(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentMines = ImperialWorkSiteManager.countImperialMines(serverLevel, this, 128);

    if (currentMines >= getImperialMineCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.mine_cap"), true);
        return;
    }

    int ironCost = 20;
    int scrapCost = 10;
    int coalCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_3", ironCost, scrapCost, coalCost), true);
        return;
    }

    boolean built = ImperialWorkSiteManager.buildImperialMine(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_3", this.resources.getIron(), this.resources.getScrapMetal(), this.resources.getCoal()), false);
}

// Each focus city type runs one extra of its signature work site (Mining→Mine, Fortress→Barracks,
// Hive→Scrap Yard, Forge→Forge, Agri→Farm), so a city's type shapes its economy/defence, not just
// its troops. Types with no production focus (Civilised/Shrine/Penal/Death World/Feudal) get no bonus.
private int specialtyBonus(ImperialCityType specialtyType) {
    return getCityType() == specialtyType ? 1 : 0;
}

public int getImperialMineCapacity() {
    return Math.max(1, this.cityLevel) + specialtyBonus(ImperialCityType.MINING);
}

// Gold mining unlocks at city level 2 and stays scarce: capacity grows slowly with the city.
public void tryBuildImperialGoldMine(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    if (this.cityLevel < 2) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.gold_mine_level"), true);
        return;
    }

    int currentGoldMines = ImperialGoldMineManager.countGoldMines(serverLevel, this, 128);

    if (currentGoldMines >= getGoldMineCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.gold_mine_cap"), true);
        return;
    }

    int ironCost = 40;
    int scrapCost = 25;
    int coalCost = 15;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_3", ironCost, scrapCost, coalCost), true);
        return;
    }

    boolean built = ImperialGoldMineManager.buildImperialGoldMine(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_3", this.resources.getIron(), this.resources.getScrapMetal(), this.resources.getCoal()), false);
}

public int getGoldMineCapacity() {
    if (this.cityLevel < 2) {
        return 0;
    }

    return Math.max(1, this.cityLevel / 2);
}

// Farms feed the city: a staffed Farm raises morale and sustains population growth.
public void tryBuildImperialFarm(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentFarms = ImperialFarmManager.countFarms(serverLevel, this, 128);

    if (currentFarms >= getFarmCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.farm_cap"), true);
        return;
    }

    int ironCost = 15;
    int scrapCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_2_is", ironCost, scrapCost), true);
        return;
    }

    boolean built = ImperialFarmManager.buildImperialFarm(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, 0);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_2", this.resources.getIron(), this.resources.getScrapMetal()), false);
}

public int getFarmCapacity() {
    return Math.max(1, this.cityLevel) + specialtyBonus(ImperialCityType.AGRI);
}

// Trade Depots unlock at city level 3 and trade Gold for Emerald with the capital.
public void tryBuildEmeraldTradeDepot(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    if (this.cityLevel < 3) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.trade_depot_level"), true);
        return;
    }

    int currentDepots = ImperialEmeraldTradeDepotManager.countTradeDepots(serverLevel, this, 128);

    if (currentDepots >= getTradeDepotCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.trade_depot_cap"), true);
        return;
    }

    int ironCost = 30;
    int scrapCost = 15;
    int coalCost = 10;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_3", ironCost, scrapCost, coalCost), true);
        return;
    }

    boolean built = ImperialEmeraldTradeDepotManager.buildTradeDepot(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_3", this.resources.getIron(), this.resources.getScrapMetal(), this.resources.getCoal()), false);
}

public int getTradeDepotCapacity() {
    if (this.cityLevel < 3) {
        return 0;
    }

    return Math.max(1, this.cityLevel / 2);
}

// Emerald obtained per Trader work cycle, scaling with city level.
public int getTradeDepotEmeraldYield() {
    return ImperialCityLevelStats.tradeDepotEmeraldYield(this.cityLevel);
}

// Trades stored Gold for Emerald at a fixed rate. Returns the Emerald produced (0 if it can't).
public int tradeGoldForEmeraldAtDepot() {
    int produced = this.resources.tradeGoldForEmerald(getTradeDepotEmeraldYield(), 4);

    if (produced > 0) {
        setChanged();
    }

    return produced;
}

// Gold produced per Gold Miner work cycle. Kept low because Gold is a premium resource.
public int getGoldMineYield() {
    return ImperialCityLevelStats.goldMineYield(this.cityLevel);
}

// Iron produced per Miner work cycle. Scales with city level so staffed mines can
// replace the passive output that is phased out as the settlement grows.
public int getMineIronYield() {
    return ImperialCityLevelStats.mineIronYield(this.cityLevel);
}

// Scrap Metal produced per Scrapper work cycle, scaling with city level.
public int getScrapYardScrapYield() {
    return ImperialCityLevelStats.scrapYardScrapYield(this.cityLevel);
}

public int getPromethiumRefineryCapacity() {
    return Math.max(1, this.cityLevel);
}

// Coal produced per Stoker work cycle, scaling with city level.
public int getRefineryCoalYield() {
    return ImperialCityLevelStats.refineryCoalYield(this.cityLevel);
}

public int receiveProducedResource(ImperialResourceType resourceType, int amount) {
    int accepted = this.resources.add(resourceType, amount);

    if (accepted > 0) {
        setChanged();
    }

    return accepted;
}

    public ImperialCommandCoreBlockEntity(BlockPos pos, BlockState state) {
        super(FCRegistry.IMPERIAL_COMMAND_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImperialCommandCoreBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (blockEntity.cityType == null) {
            blockEntity.assignCityType(serverLevel);
        }

        if (blockEntity.governorPersonality == null) {
            blockEntity.ensureGovernor(serverLevel);
        }

        ImperialPopulationManager.tickCitizenGrowth(serverLevel, blockEntity);

        // The (expensive) structure/citizen/threat counts are only read by the Core GUI, so we
        // refresh them at most a few times per second AND only while a player actually has the menu
        // open. When nobody is viewing the Core it does no scans at all (it used to scan ~19 times
        // every 40 ticks per city even with no viewers, which caused stutter as cities multiplied).
        if (blockEntity.openMenuCount > 0 && serverLevel.getGameTime() % 40L == 0L) {
            blockEntity.recomputeMenuStats(serverLevel);
        }

        blockEntity.tickCounter++;

        if (blockEntity.tickCounter < 200) {
            return;
        }

       blockEntity.tickCounter = 0;
WorldWarMapData.get(serverLevel).recordCity(blockEntity.worldPosition);
ImperialCityMoraleManager.tickMorale(serverLevel, blockEntity);
ImperialPatrolManager.tickPatrols(serverLevel, blockEntity);
ImperialWorkforceManager.autoManageWorkforce(serverLevel, blockEntity);
blockEntity.produceResourcesIfNewDay(level);
blockEntity.reduceReinforcementCooldown();
blockEntity.reduceSpaceMarinePromotionCooldown();
AspirantManager.tickAspirants(serverLevel, blockEntity);
ImperialCustodesManager.tickCustodes(serverLevel, blockEntity);
blockEntity.reducePrimarchMourningCooldown();
ImperialPrimarchManager.tickPrimarch(serverLevel, blockEntity);
WaaaghOverlordManager.contributeFromCity(serverLevel, blockEntity);
ImperiumOverlordManager.contributeFromCity(serverLevel, blockEntity);
blockEntity.trySeedOrkCamp(serverLevel);
blockEntity.trySpawnOrkRaid(serverLevel);
blockEntity.checkActiveOrkRaid(serverLevel);
blockEntity.tickAutonomousGovernance(serverLevel);
OrkCorruptionManager.purifyAround(serverLevel, blockEntity.worldPosition, blockEntity.getTerritoryRadius(), 6);
    }

    // An unclaimed, world-generated city governs itself: it keeps a standing garrison and grows in
    // level (expanding its walls/houses/population) on its own — no player owner required. A city a
    // player has claimed is run by that player instead (this does nothing for owned cities).
    private void tickAutonomousGovernance(ServerLevel serverLevel) {
        // The Governor runs the city when it is unclaimed, or when a player owner has appointed
        // (delegated to) the Governor so it keeps progressing while they are away.
        if (!isGoverned()) {
            return;
        }

        autonomousRecruit(serverLevel);
        autonomousUpgrade(serverLevel);
        autonomousOffensive(serverLevel);
    }

    private static final int OFFENSIVE_MIN_TROOPS = 4;
    private static final int OFFENSIVE_CHANCE = 3;

    // A strong autonomous city takes the war to the Orks: it musters half its garrison and marches
    // them on the nearby Ork camp. The troops fight on arrival; once they overwhelm the defenders the
    // camp is razed (see OrkCampBlockEntity.checkOverrun).
    private void autonomousOffensive(ServerLevel serverLevel) {
        if (this.orkCampPos == null || !OrkCampManager.isCampStillThere(serverLevel, this.orkCampPos)) {
            return;
        }
        if (serverLevel.random.nextInt(OFFENSIVE_CHANCE) != 0) {
            return;
        }

        int radius = getTerritoryRadius();
        AABB box = new AABB(
                this.worldPosition.getX() - radius, this.worldPosition.getY() - 32, this.worldPosition.getZ() - radius,
                this.worldPosition.getX() + radius, this.worldPosition.getY() + 48, this.worldPosition.getZ() + radius);

        List<net.minecraft.world.entity.PathfinderMob> troops = serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.PathfinderMob.class, box,
                mob -> mob.isAlive()
                        && !(mob instanceof ImperialCitizenEntity)
                        && FirstCrusadeFactionManager.getFaction(mob) == FirstCrusadeFaction.IMPERIUM);

        if (troops.size() < OFFENSIVE_MIN_TROOPS) {
            return;
        }

        int dispatch = troops.size() / 2;
        for (int i = 0; i < dispatch; i++) {
            troops.get(i).getNavigation().moveTo(
                    this.orkCampPos.getX() + 0.5D, this.orkCampPos.getY(), this.orkCampPos.getZ() + 0.5D, 1.0D);
        }

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel, this.worldPosition, Component.translatable("msg.firstcrusade.bcast.expedition"));
    }

    // Refill the garrison toward the city's military capacity, paying iron and ramping up gradually.
    private void autonomousRecruit(ServerLevel serverLevel) {
        if (this.recruitedGuardsmen >= getMilitaryCapacity()) {
            return;
        }

        int ironCost = getCityType().getRecruitIronCost();
        if (this.resources.getIron() < ironCost) {
            return;
        }

        // Don't raise the whole garrison at once. A Warmonger Governor levies more eagerly.
        int recruitGate = Math.max(1, 2 + getGovernorPersonality().getRecruitGateBias());
        if (serverLevel.random.nextInt(recruitGate) != 0) {
            return;
        }

        if (spawnGarrisonTroop(serverLevel, getReinforcementRank())) {
            this.resources.remove(ImperialResourceType.IRON, ironCost);
            this.recruitedGuardsmen++;
            setChanged();
        }
    }

    // A thriving autonomous city (near its population cap, with the resources to spare) expands to
    // the next level by itself — no Crusadium Plate needed, unlike a player upgrade.
    private void autonomousUpgrade(ServerLevel serverLevel) {
        if (!canUpgradeMore()) {
            return;
        }

        int ironCost = getUpgradeIronCost();
        int scrapCost = getUpgradeScrapCost();
        int coalCost = getUpgradeCoalCost();

        if (this.resources.getIron() < ironCost
                || this.resources.getScrapMetal() < scrapCost
                || this.resources.getCoal() < coalCost) {
            return;
        }

        // Only grow once the town is full of people pushing for more room.
        if (ImperialPopulationManager.countAssignedCitizens(serverLevel, this)
                < ImperialPopulationManager.getCitizenCapacity(this)) {
            return;
        }

        // Growth is a slow, deliberate thing. An Administrator/Architect Governor pushes harder.
        int upgradeGate = Math.max(1, 3 + getGovernorPersonality().getUpgradeGateBias());
        if (serverLevel.random.nextInt(upgradeGate) != 0) {
            return;
        }

        this.resources.spend(ironCost, scrapCost, coalCost);
        this.cityLevel++;
        reorganizeExistingGuardsmen(serverLevel);
        setChanged();

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                this.worldPosition,
                Component.translatable("msg.firstcrusade.bcast.city_grew", this.cityLevel)
        );
    }

    // Raises a single garrison soldier (the city's themed troop, or a Guardsman with a guard post).
    private boolean spawnGarrisonTroop(ServerLevel serverLevel, GuardsmanRank rank) {
        BlockPos spawnPos = findSpawnPosition(serverLevel);
        EntityType<? extends AbstractImperialTroopEntity> themedType = getThemedTroopType(getCityType());

        if (themedType != null) {
            return spawnThemedTroopAt(serverLevel, themedType,
                    spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        }

        GuardsmanEntity guardsman = FCRegistry.GUARDSMAN.get().create(serverLevel);
        if (guardsman == null) {
            return false;
        }

        BlockPos guardPostPos = findGuardPostPosition(this.recruitedGuardsmen);
        prepareGuardPost(serverLevel, guardPostPos);

        guardsman.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        guardsman.assignToCommandCore(this.worldPosition);
        guardsman.assignGuardPost(guardPostPos);
        guardsman.assignRandomChapter();
        guardsman.initializeFromCity(rank, getCityType());

        serverLevel.addFreshEntity(guardsman);
        return true;
    }

    // Garrisons a freshly founded autonomous town so it can defend itself from the first night.
    private void spawnInitialGarrison(ServerLevel serverLevel, int count) {
        for (int i = 0; i < count; i++) {
            if (this.recruitedGuardsmen >= getMilitaryCapacity()) {
                break;
            }
            if (spawnGarrisonTroop(serverLevel, getStartingGuardsmanRank())) {
                this.recruitedGuardsmen++;
            }
        }
        setChanged();
    }

    // Called by ImperialCommandCoreMenu when a player opens the GUI. Refreshes once immediately so
    // the viewer sees current data, then serverTick keeps it fresh while the menu stays open.
    public void onMenuOpened() {
        this.openMenuCount++;

        if (this.level instanceof ServerLevel serverLevel) {
            recomputeMenuStats(serverLevel);
        }
    }

    // Called by ImperialCommandCoreMenu when a player closes the GUI.
    public void onMenuClosed() {
        if (this.openMenuCount > 0) {
            this.openMenuCount--;
        }
    }

    // Recomputes the cached counts read by the Core GUI. Called a few times per second from
    // serverTick while the menu is open, so the menu never triggers these block/entity scans on
    // its own polling.
    // --- War Table (strategic world map) cached data ---
    public static final int MAX_BLIPS = 32;
    private static final int WAR_MAP_MAX_RANGE = 2600; // covers the world border (radius 2500) with margin
    private static final int WAR_MAP_MIN_RANGE = 128;  // floor so a lone settlement isn't a single dot
    private final int[] statBlipDx = new int[MAX_BLIPS];
    private final int[] statBlipDz = new int[MAX_BLIPS];
    private final int[] statBlipKind = new int[MAX_BLIPS]; // 1 imperial city, 2 ork camp
    private int statBlipCount;
    private int statWarDominion;
    private int statWaaaghTier;
    private int statMapRange = WAR_MAP_MIN_RANGE;

    // Builds the strategic war map: every Imperial city and Ork camp in the world (from the global
    // register), placed relative to this city. The map range auto-fits the farthest settlement so the
    // whole planet's front line is visible at once — no longer just a small radius around the Core.
    private void computeWarTable(ServerLevel serverLevel) {
        this.statWarDominion = WarDominionData.get(serverLevel).getDominion();
        this.statWaaaghTier = WaaaghOverlordManager.getTier(serverLevel);
        this.statBlipCount = 0;

        WorldWarMapData map = WorldWarMapData.get(serverLevel);

        // First pass: how far out does the farthest settlement sit? That sets the zoom.
        int farthest = WAR_MAP_MIN_RANGE;
        farthest = Math.max(farthest, farthestSettlement(map.getCities()));
        farthest = Math.max(farthest, farthestSettlement(map.getCamps()));
        this.statMapRange = Math.min(WAR_MAP_MAX_RANGE, farthest);

        // Second pass: plot the blips (imperial cities, then ork camps).
        for (long packed : map.getCities()) {
            if (packed == this.worldPosition.asLong()) {
                continue; // this city is the centre marker, drawn separately
            }
            BlockPos pos = BlockPos.of(packed);
            addBlip(pos.getX() - this.worldPosition.getX(), pos.getZ() - this.worldPosition.getZ(), 1);
        }

        for (long packed : map.getCamps()) {
            BlockPos pos = BlockPos.of(packed);
            addBlip(pos.getX() - this.worldPosition.getX(), pos.getZ() - this.worldPosition.getZ(), 2);
        }
    }

    private int farthestSettlement(java.util.Set<Long> packedPositions) {
        int farthest = 0;

        for (long packed : packedPositions) {
            BlockPos pos = BlockPos.of(packed);
            int dx = Math.abs(pos.getX() - this.worldPosition.getX());
            int dz = Math.abs(pos.getZ() - this.worldPosition.getZ());
            farthest = Math.max(farthest, Math.max(dx, dz));
        }

        return farthest;
    }

    private void addBlip(int dx, int dz, int kind) {
        if (this.statBlipCount >= this.statBlipKind.length) {
            return;
        }
        int i = this.statBlipCount++;
        this.statBlipDx[i] = Math.max(-this.statMapRange, Math.min(this.statMapRange, dx));
        this.statBlipDz[i] = Math.max(-this.statMapRange, Math.min(this.statMapRange, dz));
        this.statBlipKind[i] = kind;
    }

    public int getWarDominionGui() {
        return this.statWarDominion;
    }

    public int getWaaaghTierGui() {
        return this.statWaaaghTier;
    }

    // World range (blocks from this city's centre) the war map currently spans, for the GUI scale.
    public int getMapRangeGui() {
        return this.statMapRange;
    }

    public int getBlipCount() {
        return this.statBlipCount;
    }

    public int getBlipDx(int i) {
        return i >= 0 && i < this.statBlipDx.length ? this.statBlipDx[i] : 0;
    }

    public int getBlipDz(int i) {
        return i >= 0 && i < this.statBlipDz.length ? this.statBlipDz[i] : 0;
    }

    public int getBlipKind(int i) {
        return i >= 0 && i < this.statBlipKind.length ? this.statBlipKind[i] : 0;
    }

    private void recomputeMenuStats(ServerLevel serverLevel) {
        this.statMineCount = ImperialWorkSiteManager.countImperialMines(serverLevel, this, STATS_SCAN_RADIUS);
        this.statGoldMineCount = ImperialGoldMineManager.countGoldMines(serverLevel, this, STATS_SCAN_RADIUS);
        this.statScrapYardCount = ImperialScrapYardManager.countScrapYards(serverLevel, this, STATS_SCAN_RADIUS);
        this.statForgeCount = ImperialForgeManager.countForges(serverLevel, this, STATS_SCAN_RADIUS);
        this.statRefineryCount = ImperialPromethiumRefineryManager.countRefineries(serverLevel, this, STATS_SCAN_RADIUS);
        this.statFarmCount = ImperialFarmManager.countFarms(serverLevel, this, STATS_SCAN_RADIUS);
        this.statTradeDepotCount = ImperialEmeraldTradeDepotManager.countTradeDepots(serverLevel, this, STATS_SCAN_RADIUS);
        this.statBarracksCount = ImperialBarracksManager.countBarracks(serverLevel, this, STATS_SCAN_RADIUS);

        // One citizen scan tallies the total, the unemployed and every job at once.
        ImperialPopulationManager.CitizenCensus census = ImperialPopulationManager.censusAssignedCitizens(serverLevel, this);
        this.statCitizenCount = census.assigned();
        this.statUnemployedCount = census.unemployed();
        this.statMinerCount = census.withJob(ImperialCitizenJob.MINER);
        this.statGoldMinerCount = census.withJob(ImperialCitizenJob.GOLD_MINER);
        this.statScrapperCount = census.withJob(ImperialCitizenJob.SCRAPPER);
        this.statSmithCount = census.withJob(ImperialCitizenJob.SMITH);
        this.statStokerCount = census.withJob(ImperialCitizenJob.STOKER);
        this.statFarmerCount = census.withJob(ImperialCitizenJob.FARMER);
        this.statTraderCount = census.withJob(ImperialCitizenJob.TRADER);
        this.statRecruitCount = census.withJob(ImperialCitizenJob.RECRUIT);

        this.statThreatScore = getLiveThreatScore();

        computeWarTable(serverLevel);
    }

    public int getCachedMineCount() {
        return this.statMineCount;
    }

    public int getCachedGoldMineCount() {
        return this.statGoldMineCount;
    }

    public int getCachedScrapYardCount() {
        return this.statScrapYardCount;
    }

    public int getCachedForgeCount() {
        return this.statForgeCount;
    }

    public int getCachedRefineryCount() {
        return this.statRefineryCount;
    }

    public int getCachedFarmCount() {
        return this.statFarmCount;
    }

    public int getCachedTradeDepotCount() {
        return this.statTradeDepotCount;
    }

    public int getCachedBarracksCount() {
        return this.statBarracksCount;
    }

    public int getCachedCitizenCount() {
        return this.statCitizenCount;
    }

    public int getCachedUnemployedCount() {
        return this.statUnemployedCount;
    }

    public int getCachedMinerCount() {
        return this.statMinerCount;
    }

    public int getCachedGoldMinerCount() {
        return this.statGoldMinerCount;
    }

    public int getCachedScrapperCount() {
        return this.statScrapperCount;
    }

    public int getCachedSmithCount() {
        return this.statSmithCount;
    }

    public int getCachedStokerCount() {
        return this.statStokerCount;
    }

    public int getCachedFarmerCount() {
        return this.statFarmerCount;
    }

    public int getCachedTraderCount() {
        return this.statTraderCount;
    }

    public int getCachedRecruitCount() {
        return this.statRecruitCount;
    }

    public int getCachedThreatScore() {
        return this.statThreatScore;
    }

    private void produceResourcesIfNewDay(Level level) {
        long currentDay = level.getDayTime() / 24000L;

        if (this.lastProductionDay < 0) {
            this.lastProductionDay = currentDay;
            setChanged();
            return;
        }

        if (currentDay <= this.lastProductionDay) {
            return;
        }

        long daysPassed = currentDay - this.lastProductionDay;

        int ironProduction = getDailyIronProduction();
        int scrapProduction = getDailyScrapProduction();
        int coalProduction = getDailyCoalProduction();

        if (level instanceof ServerLevel serverLevel) {
            ironProduction = getEffectiveDailyIronProduction(serverLevel);
            scrapProduction = getEffectiveDailyScrapProduction(serverLevel);
            coalProduction = getEffectiveDailyCoalProduction(serverLevel);
        }

        double moraleMultiplier = ImperialCityMoraleManager.getProductionMultiplier(this.cityMorale);
        ironProduction = (int) Math.round(ironProduction * moraleMultiplier);
        scrapProduction = (int) Math.round(scrapProduction * moraleMultiplier);
        coalProduction = (int) Math.round(coalProduction * moraleMultiplier);

        // The city's type boosts its focus resource (e.g. Mining -> Iron, Forge -> Scrap).
        ImperialCityType type = getCityType();
        ironProduction = type.applyProductionFocus(ImperialResourceType.IRON, ironProduction);
        scrapProduction = type.applyProductionFocus(ImperialResourceType.SCRAP, scrapProduction);
        coalProduction = type.applyProductionFocus(ImperialResourceType.COAL, coalProduction);

        // Ork corruption within the city's territory chokes its industry.
        if (level instanceof ServerLevel serverLevel) {
            double corruptionMultiplier = OrkCorruptionManager.productionMultiplier(serverLevel, this.worldPosition, getTerritoryRadius());
            ironProduction = (int) Math.round(ironProduction * corruptionMultiplier);
            scrapProduction = (int) Math.round(scrapProduction * corruptionMultiplier);
            coalProduction = (int) Math.round(coalProduction * corruptionMultiplier);
        }

        addIron((int) daysPassed * ironProduction);
addScrapMetal((int) daysPassed * scrapProduction);
addCoal((int) daysPassed * coalProduction);
addEmperorGeneSeed((int) daysPassed * getDailyEmperorGeneProduction());

        this.lastProductionDay = currentDay;
        setChanged();
    }

    // Passive Iron output shrinks as staffed Imperial Mines take over production.
    public int getEffectiveDailyIronProduction(ServerLevel serverLevel) {
        int capacity = getImperialMineCapacity();

        if (capacity <= 0) {
            return getDailyIronProduction();
        }

        int staffed = Math.min(capacity, ImperialWorkSiteManager.countStaffedImperialMines(serverLevel, this, 128));

        return scalePassiveProduction(getDailyIronProduction(), staffed, capacity);
    }

    // Passive Scrap output shrinks as staffed Scrap Yards take over production.
    public int getEffectiveDailyScrapProduction(ServerLevel serverLevel) {
        int capacity = getScrapYardCapacity();

        if (capacity <= 0) {
            return getDailyScrapProduction();
        }

        int staffed = Math.min(capacity, ImperialScrapYardManager.countStaffedScrapYards(serverLevel, this, 128));

        return scalePassiveProduction(getDailyScrapProduction(), staffed, capacity);
    }

    // Passive Coal output shrinks as staffed Promethium Refineries take over production.
    public int getEffectiveDailyCoalProduction(ServerLevel serverLevel) {
        int capacity = getPromethiumRefineryCapacity();

        if (capacity <= 0) {
            return getDailyCoalProduction();
        }

        int staffed = Math.min(capacity, ImperialPromethiumRefineryManager.countStaffedRefineries(serverLevel, this, 128));

        return scalePassiveProduction(getDailyCoalProduction(), staffed, capacity);
    }

    private int scalePassiveProduction(int base, int staffed, int capacity) {
        double selfSufficiency = (double) staffed / (double) capacity;
        double passiveFraction = 1.0D - selfSufficiency * (1.0D - PASSIVE_PRODUCTION_FLOOR);

        return (int) Math.round(base * passiveFraction);
    }

    public void depositIron(Player player, ItemStack itemStack) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.not_owner"), true);
            return;
        }

        int acceptedAmount = addIron(itemStack.getCount());

        if (acceptedAmount <= 0) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.full", Component.translatable("gui.firstcrusade.res.iron")), true);
            return;
        }

        itemStack.shrink(acceptedAmount);
        setChanged();

        player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.done", acceptedAmount, Component.translatable("gui.firstcrusade.res.iron")), false);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.city", Component.translatable("gui.firstcrusade.res.iron"), this.resources.getIron(), getStorageCapacity()), false);
    }

    public void depositCoal(Player player, ItemStack itemStack) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.not_owner"), true);
            return;
        }

        int acceptedAmount = addCoal(itemStack.getCount());

        if (acceptedAmount <= 0) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.full", Component.translatable("gui.firstcrusade.res.coal")), true);
            return;
        }

        itemStack.shrink(acceptedAmount);
        setChanged();

        player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.done", acceptedAmount, Component.translatable("gui.firstcrusade.res.coal")), false);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.city", Component.translatable("gui.firstcrusade.res.coal"), this.resources.getCoal(), getStorageCapacity()), false);
    }

    public void depositScrapMetal(Player player, ItemStack itemStack) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.not_owner"), true);
            return;
        }

        int acceptedAmount = addScrapMetal(itemStack.getCount());

        if (acceptedAmount <= 0) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.full", Component.translatable("gui.firstcrusade.res.scrap")), true);
            return;
        }

        itemStack.shrink(acceptedAmount);
        setChanged();

        player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.done", acceptedAmount, Component.translatable("gui.firstcrusade.res.scrap")), false);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.city", Component.translatable("gui.firstcrusade.res.scrap"), this.resources.getScrapMetal(), getStorageCapacity()), false);
    }
    
    // Deposits whole compressed blocks (worth 9 units each) that fit in the remaining capacity.
    private int depositCompressed(ItemStack stack, int freeSpace, java.util.function.IntUnaryOperator deposit) {
        int blocksThatFit = Math.min(stack.getCount(), freeSpace / 9);

        if (blocksThatFit <= 0) {
            return 0;
        }

        int accepted = deposit.applyAsInt(blocksThatFit * 9);
        stack.shrink(blocksThatFit);
        return accepted;
    }

    public void depositAllResources(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.not_owner"), true);
        return;
    }

    int totalIron = 0;
    int totalCoal = 0;
    int totalScrap = 0;
    int totalGold = 0;
    int totalEmerald = 0;
    int totalCrusadium = 0;
    boolean foundResource = false;

    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        ItemStack stack = player.getInventory().getItem(slot);

        if (stack.isEmpty()) {
            continue;
        }

        // Compressed blocks count as 9 units each, so players can deposit in bulk much faster.
        if (stack.is(Items.IRON_BLOCK)) {
            foundResource = true;
            totalIron += depositCompressed(stack, getStorageCapacity() - getIron(), this::addIron);
            continue;
        }

        if (stack.is(Items.COAL_BLOCK)) {
            foundResource = true;
            totalCoal += depositCompressed(stack, getStorageCapacity() - getCoal(), this::addCoal);
            continue;
        }

        if (stack.is(Items.GOLD_BLOCK)) {
            foundResource = true;
            totalGold += depositCompressed(stack, getStorageCapacity() - getGold(), this::addGold);
            continue;
        }

        if (stack.is(Items.EMERALD_BLOCK)) {
            foundResource = true;
            totalEmerald += depositCompressed(stack, getStorageCapacity() - getEmerald(), this::addEmerald);
            continue;
        }

        if (stack.is(Items.IRON_INGOT)) {
            foundResource = true;
            int accepted = addIron(stack.getCount());

            if (accepted > 0) {
                stack.shrink(accepted);
                totalIron += accepted;
            }

            continue;
        }

        if (stack.is(Items.COAL)) {
            foundResource = true;
            int accepted = addCoal(stack.getCount());

            if (accepted > 0) {
                stack.shrink(accepted);
                totalCoal += accepted;
            }

            continue;
        }

        if (stack.is(FCRegistry.SCRAP_METAL.get())) {
            foundResource = true;
            int accepted = addScrapMetal(stack.getCount());

            if (accepted > 0) {
                stack.shrink(accepted);
                totalScrap += accepted;
            }

            continue;
        }

        if (stack.is(Items.GOLD_INGOT)) {
            foundResource = true;
            int accepted = addGold(stack.getCount());

            if (accepted > 0) {
                stack.shrink(accepted);
                totalGold += accepted;
            }

            continue;
        }

        if (stack.is(Items.EMERALD)) {
            foundResource = true;
            int accepted = addEmerald(stack.getCount());

            if (accepted > 0) {
                stack.shrink(accepted);
                totalEmerald += accepted;
            }

            continue;
        }

        if (stack.is(FCRegistry.CRUSADIUM_INGOT.get())) {
            foundResource = true;
            int accepted = addCrusadium(stack.getCount());

            if (accepted > 0) {
                stack.shrink(accepted);
                totalCrusadium += accepted;
            }
        }
    }

    if (totalIron <= 0 && totalCoal <= 0 && totalScrap <= 0
            && totalGold <= 0 && totalEmerald <= 0 && totalCrusadium <= 0) {
        if (foundResource) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.all_full"), true);
        } else {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.all_none"), true);
        }

        return;
    }

    player.getInventory().setChanged();
    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.all_done",
            totalIron, totalCoal, totalScrap, totalGold, totalEmerald, totalCrusadium), false);

    player.displayClientMessage(Component.translatable("msg.firstcrusade.deposit.all_storage",
            this.resources.getIron(), this.resources.getCoal(), this.resources.getScrapMetal(),
            this.resources.getGold(), this.resources.getEmerald(), this.resources.getCrusadium()), false);
}

    // Withdraws stored resources back into the owner's inventory as items (for crafting, etc.).
    public void withdrawResource(Player player, ImperialResourceType type, int requested) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.withdraw.not_owner"), true);
            return;
        }

        int available = this.resources.get(type);

        int amount = Math.min(available, requested);

        if (amount <= 0) {
            player.displayClientMessage(Component.translatable("gui.firstcrusade.reason.withdraw_empty", type.getDisplayName()), true);
            return;
        }

        Item item = switch (type) {
            case IRON -> Items.IRON_INGOT;
            case COAL -> Items.COAL;
            case SCRAP -> FCRegistry.SCRAP_METAL.get();
            case GOLD -> Items.GOLD_INGOT;
            case EMERALD -> Items.EMERALD;
            case CRUSADIUM -> FCRegistry.CRUSADIUM_INGOT.get();
        };

        this.resources.remove(type, amount);

        ItemStack stack = new ItemStack(item, amount);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        player.getInventory().setChanged();
        setChanged();

        player.displayClientMessage(Component.translatable("msg.firstcrusade.withdraw.done", amount, type.getDisplayName()), false);
    }

    private int addIron(int amount) {
        return this.resources.addIron(amount);
    }

    private int addCoal(int amount) {
        return this.resources.addCoal(amount);
    }

    private int addScrapMetal(int amount) {
        return this.resources.addScrapMetal(amount);
    }

    private int addGold(int amount) {
        return this.resources.addGold(amount);
    }

    private int addEmerald(int amount) {
        return this.resources.addEmerald(amount);
    }

    private int addCrusadium(int amount) {
        return this.resources.addCrusadium(amount);
    }

private int addEmperorGeneSeed(int amount) {
    if (amount <= 0) {
        return 0;
    }

    int freeSpace = getEmperorGeneSeedCapacity() - this.emperorGeneSeed;

    if (freeSpace <= 0) {
        return 0;
    }

    int acceptedAmount = Math.min(amount, freeSpace);
    this.emperorGeneSeed += acceptedAmount;

    return acceptedAmount;
}

public int getEmperorGeneSeed() {
    return this.emperorGeneSeed;
}

public boolean consumeEmperorGeneSeed(int amount) {
    if (amount <= 0 || this.emperorGeneSeed < amount) {
        return false;
    }

    this.emperorGeneSeed -= amount;
    setChanged();
    return true;
}

public int getFood() {
    return this.food;
}

public int getFoodCapacity() {
    return getStorageCapacity();
}

// Food produced per Farmer work cycle, scaling with city level.
public int getFarmFoodYield() {
    return ImperialCityLevelStats.farmFoodYield(this.cityLevel);
}

// Adds food up to the storage capacity; returns the amount accepted.
public int addFood(int amount) {
    if (amount <= 0) {
        return 0;
    }

    int freeSpace = Math.max(0, getFoodCapacity() - this.food);
    int accepted = Math.min(amount, freeSpace);

    if (accepted > 0) {
        this.food += accepted;
        setChanged();
    }

    return accepted;
}

// Receives food produced by a staffed Farm.
public int receiveProducedFood(int amount) {
    return addFood(amount);
}

// Soft-consumes food (e.g. when a citizen is born). Never blocks if food runs out.
public void consumeFood(int amount) {
    if (amount <= 0 || this.food <= 0) {
        return;
    }

    this.food = Math.max(0, this.food - amount);
    setChanged();
}

// Withdraws stored Food into the owner's inventory as Wheat.
public void withdrawFood(Player player, int requested) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.withdraw.not_owner"), true);
        return;
    }

    int amount = Math.min(this.food, requested);

    if (amount <= 0) {
        player.displayClientMessage(Component.translatable("gui.firstcrusade.reason.withdraw_empty", Component.translatable("gui.firstcrusade.res.food")), true);
        return;
    }

    this.food -= amount;

    ItemStack stack = new ItemStack(Items.WHEAT, amount);

    if (!player.getInventory().add(stack)) {
        player.drop(stack, false);
    }

    player.getInventory().setChanged();
    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.withdraw.done", amount, Component.translatable("gui.firstcrusade.res.food")), false);
}

public boolean consumeCrusadium(int amount) {
    boolean consumed = this.resources.consumeCrusadium(amount);

    if (consumed) {
        setChanged();
    }

    return consumed;
}

public int getPrimarchMourningCooldownTicks() {
    return this.primarchMourningCooldownTicks;
}

public int getPrimarchMourningCooldownSeconds() {
    return this.primarchMourningCooldownTicks / 20;
}

// Called when the city's Primarch dies: the settlement mourns and morale collapses.
public void onPrimarchDeath() {
    this.primarchMourningCooldownTicks = 24000; // ~20 minutes before another may rise
    setCityMorale(this.cityMorale - 25);
    setChanged();
}

private void reducePrimarchMourningCooldown() {
    if (this.primarchMourningCooldownTicks <= 0) {
        return;
    }

    this.primarchMourningCooldownTicks -= 200;

    if (this.primarchMourningCooldownTicks < 0) {
        this.primarchMourningCooldownTicks = 0;
    }

    setChanged();
}

public BlockPos getOrkCampPos() {
    return this.orkCampPos;
}

// Seeds one Ork Camp once the settlement is large enough to attract a warband.
private void trySeedOrkCamp(ServerLevel serverLevel) {
    // In the fixed test world the Ork cities are placed by the seeder, not spawned per-city.
    if (ExampleMod.TEST_FIXED_WORLD) {
        return;
    }

    if (this.orkCampSeeded || this.cityLevel < 2) {
        return;
    }

    BlockPos campPos = OrkCampManager.seedCamp(serverLevel, this);

    this.orkCampSeeded = true;
    this.orkCampPos = campPos;
    setChanged();
}

public int getDailyEmperorGeneProduction() {
    return ImperialCityLevelStats.dailyEmperorGeneProduction(this.cityLevel);
}

public int getEmperorGeneSeedCapacity() {
    return ImperialCityLevelStats.emperorGeneSeedCapacity(this.cityLevel);
}



// Spends the city type's Iron recruit cost; returns false (without spending) if the stockpile
// is short. Cheap for Hive levies, expensive for Fortress shock troops.
public boolean tryPayRecruitCost() {
    int cost = getCityType().getRecruitIronCost();

    if (cost <= 0) {
        return true;
    }

    if (this.resources.get(ImperialResourceType.IRON) < cost) {
        return false;
    }

    this.resources.remove(ImperialResourceType.IRON, cost);
    setChanged();
    return true;
}

public void tryRecruitGuardsman(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int recruitsInTraining = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.RECRUIT);

    if (this.recruitedGuardsmen + recruitsInTraining >= getMilitaryCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.military_cap"), true);
        return;
    }

    BlockPos barracksPos = ImperialBarracksManager.findAvailableBarracks(serverLevel, this, 128);

    if (barracksPos == null) {
        int barracksCount = ImperialBarracksManager.countBarracks(serverLevel, this, 128);

        if (barracksCount <= 0) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.need_barracks"), true);
        } else {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.barracks_busy"), true);
        }

        return;
    }

    ImperialCitizenEntity recruit = ImperialPopulationManager.findNearestTrainableCitizen(serverLevel, this, player);

    if (recruit == null) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.no_citizen"), true);
        return;
    }

    if (!tryPayRecruitCost()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.need_iron",
                getCityType().getTroopName(), getCityType().getRecruitIronCost()), true);
        return;
    }

    recruit.assignToCommandCore(this.worldPosition);
    recruit.assignJob(ImperialCitizenJob.RECRUIT, barracksPos);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.assigned"), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.recruit.in_training",
            recruitsInTraining + 1, this.recruitedGuardsmen, getMilitaryCapacity()), false);
}

public boolean completeRecruitTraining(ServerLevel serverLevel, ImperialCitizenEntity recruit) {
    if (this.recruitedGuardsmen >= getMilitaryCapacity()) {
        return false;
    }

    // City types with a dedicated themed troop field it instead of a Guardsman (see
    // getThemedTroopType); the rest field a Guardsman with the city's rank/chapter/regiment.
    EntityType<? extends AbstractImperialTroopEntity> themedType = getThemedTroopType(getCityType());

    boolean spawned = themedType != null
            ? spawnThemedTroop(serverLevel, recruit, themedType)
            : spawnTrainedGuardsman(serverLevel, recruit);

    if (!spawned) {
        return false;
    }

    recruit.discard();

    this.recruitedGuardsmen++;
    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.recruit_joined", getFieldedUnitName(), this.recruitedGuardsmen, getMilitaryCapacity())
    );

    return true;
}

// Display name of the basic troop this city actually fields (themed entity or Guardsman regiment).
private String getFieldedUnitName() {
    return switch (getCityType()) {
        case FORGE -> "Skitarii Ranger";
        case FORTRESS -> "Kasrkin";
        case HIVE -> "Enforcer";
        case SHRINE -> "Sister of Battle";
        default -> getCityType().getTroopName();
    };
}

private boolean spawnTrainedGuardsman(ServerLevel serverLevel, ImperialCitizenEntity recruit) {
    GuardsmanEntity guardsman = FCRegistry.GUARDSMAN.get().create(serverLevel);

    if (guardsman == null) {
        return false;
    }

    guardsman.moveTo(
            recruit.getX(),
            recruit.getY(),
            recruit.getZ(),
            recruit.getYRot(),
            recruit.getXRot()
    );

    guardsman.assignToCommandCore(this.worldPosition);
    guardsman.assignRandomChapter();
    guardsman.initializeFromCity(getStartingGuardsmanRank(), getCityType());

    serverLevel.addFreshEntity(guardsman);

    return true;
}

// The standalone themed troop a city type fields, or null if it fields the baseline Guardsman.
@Nullable
private static EntityType<? extends AbstractImperialTroopEntity> getThemedTroopType(ImperialCityType cityType) {
    return switch (cityType) {
        case FORGE -> FCRegistry.SKITARII_RANGER.get();     // Adeptus Mechanicus
        case FORTRESS -> FCRegistry.KASRKIN.get();          // Militarum Tempestus
        case HIVE -> FCRegistry.ENFORCER.get();             // Adeptus Arbites (melee)
        case MINING -> FCRegistry.MINE_GUARD.get();         // Industrial Enforcers (melee bruiser)
        case AGRI -> FCRegistry.AGRI_MILITIA.get();         // rural PDF (light skirmisher)
        case SHRINE -> FCRegistry.SISTER_OF_BATTLE.get();   // Adepta Sororitas (ranged zealot)
        case PENAL -> FCRegistry.PENAL_LEGIONNAIRE.get();   // Penal Legion (fast fragile melee swarm)
        case DEATH_WORLD -> FCRegistry.JUNGLE_FIGHTER.get(); // Catachan-style veteran skirmisher
        case FEUDAL -> FCRegistry.FEUDAL_KNIGHT.get();      // armoured melee shield wall
        default -> null;                                    // CIVILISED: baseline Guardsman
    };
}

private boolean spawnThemedTroop(ServerLevel serverLevel, ImperialCitizenEntity recruit,
                                 EntityType<? extends AbstractImperialTroopEntity> type) {
    return spawnThemedTroopAt(serverLevel, type,
            recruit.getX(), recruit.getY(), recruit.getZ(), recruit.getYRot(), recruit.getXRot());
}

// Spawns a themed troop bound to this Core at the given position. Themed troops free-roam (no fixed
// guard post), so unlike a Guardsman they need no guard-post/chapter setup. Used by recruitment and
// by reinforcements.
private boolean spawnThemedTroopAt(ServerLevel serverLevel, EntityType<? extends AbstractImperialTroopEntity> type,
                                   double x, double y, double z, float yRot, float xRot) {
    AbstractImperialTroopEntity troop = type.create(serverLevel);

    if (troop == null) {
        return false;
    }

    troop.moveTo(x, y, z, yRot, xRot);
    troop.assignToCommandCore(this.worldPosition);

    serverLevel.addFreshEntity(troop);

    return true;
}

public GuardsmanSpecialization getSelectedSpecialization() {
    GuardsmanSpecialization spec = GuardsmanSpecialization.fromOrdinal(this.selectedSpecialistOrdinal);

    if (!spec.isSpecialist()) {
        spec = GuardsmanSpecialization.SNIPER;
        this.selectedSpecialistOrdinal = spec.ordinal();
    }

    return spec;
}

public int getSelectedSpecialistOrdinal() {
    return this.selectedSpecialistOrdinal;
}

public void cycleSelectedSpecialist(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.cycle_not_owner"), true);
        return;
    }

    GuardsmanSpecialization next = getSelectedSpecialization().nextSelectable();
    this.selectedSpecialistOrdinal = next.ordinal();
    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.selected", next.getDisplayName()), true);
}

public void promoteSpecialist(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.promote_not_owner"), true);
        return;
    }

    if (this.cityLevel < 2) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.req_level_2"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int ironCost = getSpecialistIronCost();
    int scrapCost = getSpecialistScrapCost();
    int warSupportCost = getSpecialistWarSupportCost();

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_2_is", ironCost, scrapCost), true);
        return;
    }

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.need_warsupport", warSupportCost), true);
        return;
    }

    GuardsmanEntity target = findNearestSpecializableGuardsman(serverLevel, player);

    if (target == null) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.no_guardsman"), true);
        return;
    }

    GuardsmanSpecialization spec = getSelectedSpecialization();

    target.setSpecialization(spec, true);

    this.resources.spend(ironCost, scrapCost, 0);
    this.imperialWarSupport -= warSupportCost;

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.promoted", spec.getDisplayName()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.spec.cost", ironCost, scrapCost, warSupportCost), false);
}

private int getSpecialistIronCost() {
    return switch (this.cityLevel) {
        case 1 -> 40;
        case 2 -> 60;
        case 3 -> 100;
        case 4 -> 180;
        case 5 -> 320;
        default -> 60;
    };
}

private int getSpecialistScrapCost() {
    return switch (this.cityLevel) {
        case 1 -> 25;
        case 2 -> 40;
        case 3 -> 70;
        case 4 -> 120;
        case 5 -> 220;
        default -> 40;
    };
}

private int getSpecialistWarSupportCost() {
    return ImperialCityLevelStats.specialistWarSupportCost(this.cityLevel);
}

private GuardsmanEntity findNearestSpecializableGuardsman(ServerLevel serverLevel, Player player) {
    AABB searchBox = new AABB(
            this.worldPosition.getX() - 96,
            this.worldPosition.getY() - 32,
            this.worldPosition.getZ() - 96,
            this.worldPosition.getX() + 96,
            this.worldPosition.getY() + 64,
            this.worldPosition.getZ() + 96
    );

    List<GuardsmanEntity> guardsmen = serverLevel.getEntitiesOfClass(
            GuardsmanEntity.class,
            searchBox,
            guardsman -> guardsman.isAlive()
                    && guardsman.isAssignedToCommandCore(this.worldPosition)
                    && !guardsman.hasSpecialization()
    );

    GuardsmanEntity nearest = null;
    double nearestDistance = Double.MAX_VALUE;

    for (GuardsmanEntity guardsman : guardsmen) {
        double distance = guardsman.distanceToSqr(player);

        if (distance < nearestDistance) {
            nearestDistance = distance;
            nearest = guardsman;
        }
    }

    return nearest;
}

public boolean engineerRepair(int amount) {
    if (amount <= 0 || this.cityIntegrity >= 100) {
        return false;
    }

    this.cityIntegrity = Math.min(100, this.cityIntegrity + amount);
    setChanged();
    return true;
}

    private BlockPos findSpawnPosition(ServerLevel serverLevel) {
        BlockPos basePos = this.worldPosition.above();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos candidate = basePos.offset(x, 0, z);
                BlockPos aboveCandidate = candidate.above();

                if (serverLevel.getBlockState(candidate).isAir() && serverLevel.getBlockState(aboveCandidate).isAir()) {
                    return candidate;
                }
            }
        }

        return basePos;
    }

    private BlockPos findGuardPostPosition(int soldierIndex) {
        if (this.cityLevel <= 1) {
            int radius = 4;
            int positionIndex = soldierIndex % 8;

            return switch (positionIndex) {
                case 0 -> this.worldPosition.offset(0, 0, radius);
                case 1 -> this.worldPosition.offset(radius, 0, 0);
                case 2 -> this.worldPosition.offset(0, 0, -radius);
                case 3 -> this.worldPosition.offset(-radius, 0, 0);
                case 4 -> this.worldPosition.offset(radius, 0, radius);
                case 5 -> this.worldPosition.offset(-radius, 0, radius);
                case 6 -> this.worldPosition.offset(radius, 0, -radius);
                case 7 -> this.worldPosition.offset(-radius, 0, -radius);
                default -> this.worldPosition.offset(0, 0, radius);
            };
        }

        int radius = Math.max(8, getCityStructureRadius());
        int wallYOffset = getCityWallHeight();
        int towerYOffset = getCityWallHeight() + 4;
        int positionIndex = soldierIndex % 16;

        return switch (positionIndex) {
            case 0 -> this.worldPosition.offset(radius, towerYOffset, radius);
            case 1 -> this.worldPosition.offset(-radius, towerYOffset, radius);
            case 2 -> this.worldPosition.offset(radius, towerYOffset, -radius);
            case 3 -> this.worldPosition.offset(-radius, towerYOffset, -radius);
            case 4 -> this.worldPosition.offset(5, wallYOffset, radius);
            case 5 -> this.worldPosition.offset(-5, wallYOffset, radius);
            case 6 -> this.worldPosition.offset(radius, wallYOffset, 5);
            case 7 -> this.worldPosition.offset(radius, wallYOffset, -5);
            case 8 -> this.worldPosition.offset(5, wallYOffset, -radius);
            case 9 -> this.worldPosition.offset(-5, wallYOffset, -radius);
            case 10 -> this.worldPosition.offset(-radius, wallYOffset, 5);
            case 11 -> this.worldPosition.offset(-radius, wallYOffset, -5);
            case 12 -> this.worldPosition.offset(radius - 2, wallYOffset, radius - 2);
            case 13 -> this.worldPosition.offset(-radius + 2, wallYOffset, radius - 2);
            case 14 -> this.worldPosition.offset(radius - 2, wallYOffset, -radius + 2);
            case 15 -> this.worldPosition.offset(-radius + 2, wallYOffset, -radius + 2);
            default -> this.worldPosition.offset(5, wallYOffset, radius);
        };
    }

    private void prepareGuardPost(ServerLevel serverLevel, BlockPos guardPostPos) {
        if (guardPostPos == null) {
            return;
        }

        if (guardPostPos.equals(this.worldPosition)) {
            return;
        }

        BlockPos floorPos = guardPostPos.below();

        if (!floorPos.equals(this.worldPosition) && serverLevel.getBlockState(floorPos).isAir()) {
            serverLevel.setBlock(floorPos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }

        clearBlockForGuard(serverLevel, guardPostPos);
        clearBlockForGuard(serverLevel, guardPostPos.above());
    }

    private void clearBlockForGuard(ServerLevel serverLevel, BlockPos pos) {
        if (pos.equals(this.worldPosition)) {
            return;
        }

        if (!serverLevel.getBlockState(pos).isAir()) {
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void reorganizeExistingGuardsmen(ServerLevel serverLevel) {
        int searchRadius = Math.max(96, getCityStructureRadius() + 32);

        AABB searchBox = new AABB(
                this.worldPosition.getX() - searchRadius,
                this.worldPosition.getY() - 64,
                this.worldPosition.getZ() - searchRadius,
                this.worldPosition.getX() + searchRadius,
                this.worldPosition.getY() + 96,
                this.worldPosition.getZ() + searchRadius
        );

        List<GuardsmanEntity> guardsmen = serverLevel.getEntitiesOfClass(
                GuardsmanEntity.class,
                searchBox,
                guardsman -> guardsman.isAssignedToCommandCore(this.worldPosition)
        );

        int index = 0;

        for (GuardsmanEntity guardsman : guardsmen) {
            BlockPos guardPostPos = findGuardPostPosition(index);

            prepareGuardPost(serverLevel, guardPostPos);
            guardsman.assignGuardPost(guardPostPos);

            if (guardsman.getTarget() == null) {
                guardsman.teleportTo(
                        guardPostPos.getX() + 0.5D,
                        guardPostPos.getY(),
                        guardPostPos.getZ() + 0.5D
                );
            }

            index++;
        }

        // Themed troops (Skitarii, Kasrkin, Enforcer, Mine Guard, Agri Militia, ...) share a base
        // class and count toward the military tally too, but they hold no fixed guard post — they
        // patrol freely — so we only recount them here, in a single sweep.
        List<AbstractImperialTroopEntity> themedTroops = serverLevel.getEntitiesOfClass(
                AbstractImperialTroopEntity.class,
                searchBox,
                troop -> troop.isAssignedToCommandCore(this.worldPosition)
        );

        this.recruitedGuardsmen = guardsmen.size() + themedTroops.size();
        setChanged();
    }

    public void onAssignedGuardsmanDeath() {
        if (this.recruitedGuardsmen > 0) {
            this.recruitedGuardsmen--;
            setChanged();
        }
    }

    // A new Neophyte (from the aspirant pipeline) joins the garrison tally so its later death balances
    // the count (Space Marines decrement via onAssignedGuardsmanDeath like any assigned trooper).
    public void registerAscendedMarine() {
        this.recruitedGuardsmen++;
        setChanged();
    }

    private void reduceReinforcementCooldown() {
    if (this.reinforcementCooldownTicks <= 0) {
        return;
    }

    this.reinforcementCooldownTicks -= 200;

    if (this.reinforcementCooldownTicks < 0) {
        this.reinforcementCooldownTicks = 0;
    }

    setChanged();
}

public void callImperialReinforcements(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.reinf.not_owner"), true);
        return;
    }

    if (!this.activeOrkRaid) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.reinf.only_raid"), true);
        return;
    }

    if (this.reinforcementCooldownTicks > 0) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.reinf.cooldown", this.reinforcementCooldownTicks / 20), true);
        return;
    }

    if (this.recruitedGuardsmen >= getMilitaryCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.reinf.military_cap"), true);
        return;
    }

    int warSupportCost = getReinforcementWarSupportCost();

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.reinf.no_warsupport"), true);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.warsupport.required", warSupportCost), false);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.warsupport.current", this.imperialWarSupport), false);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int requestedReinforcements = getReinforcementCount();
    int availableSlots = getMilitaryCapacity() - this.recruitedGuardsmen;
    int actualReinforcements = Math.min(requestedReinforcements, availableSlots);

    if (actualReinforcements <= 0) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.reinf.no_capacity"), true);
        return;
    }

    int spawned = 0;

    // Reinforcements field the city's themed troop when it has one (Forge→Skitarii, etc.), matching
    // the recruitment path; only baseline Guardsman cities get the full guard-post/chapter setup.
    EntityType<? extends AbstractImperialTroopEntity> themedType = getThemedTroopType(getCityType());

    for (int i = 0; i < actualReinforcements; i++) {
        BlockPos spawnPos = findSpawnPosition(serverLevel);
        boolean deployed;

        if (themedType != null) {
            deployed = spawnThemedTroopAt(serverLevel, themedType,
                    spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot(), 0.0F);
        } else {
            GuardsmanEntity guardsman = FCRegistry.GUARDSMAN.get().create(serverLevel);

            if (guardsman == null) {
                continue;
            }

            BlockPos guardPostPos = findGuardPostPosition(this.recruitedGuardsmen);

            prepareGuardPost(serverLevel, guardPostPos);

            guardsman.moveTo(
                    spawnPos.getX() + 0.5D,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5D,
                    player.getYRot(),
                    0.0F
            );

            guardsman.assignToCommandCore(this.worldPosition);
            guardsman.assignGuardPost(guardPostPos);
            guardsman.assignRandomChapter();
            guardsman.initializeFromCity(getReinforcementRank(), getCityType());

            serverLevel.addFreshEntity(guardsman);

            deployed = true;
        }

        if (deployed) {
            this.recruitedGuardsmen++;
            spawned++;
        }
    }

    if (spawned <= 0) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.reinf.failed"), true);
        return;
    }

    this.imperialWarSupport -= warSupportCost;
    this.reinforcementCooldownTicks = getReinforcementCooldownTicks();

    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.reinf_deployed", spawned)
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.warsupport_spent", warSupportCost, this.imperialWarSupport)
    );
}

private int getReinforcementWarSupportCost() {
    return ImperialCityLevelStats.reinforcementWarSupportCost(this.cityLevel);
}

private int getReinforcementCount() {
    int base = switch (this.cityLevel) {
        case 1 -> 1;
        case 2 -> 2;
        case 3 -> 3;
        case 4 -> 4;
        case 5 -> 6;
        default -> 1;
    };

    // The global Imperial Crusade dispatches heavier reinforcements as it grows (Fase D overlord).
    if (this.level instanceof ServerLevel serverLevel) {
        base += ImperiumOverlordManager.getTier(serverLevel);
    }

    return base;
}

private int getReinforcementCooldownTicks() {
    return ImperialCityLevelStats.reinforcementCooldownTicks(this.cityLevel);
}

private GuardsmanRank getReinforcementRank() {
    return switch (this.cityLevel) {
        case 1 -> GuardsmanRank.GUARDSMAN;
        case 2 -> GuardsmanRank.VETERAN;
        case 3 -> GuardsmanRank.SERGEANT;
        case 4 -> GuardsmanRank.LIEUTENANT;
        case 5 -> GuardsmanRank.CAPTAIN;
        default -> GuardsmanRank.GUARDSMAN;
    };
}
public void rallyDefenders(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.rally.not_owner"), true);
        return;
    }

    if (!this.activeOrkRaid) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.rally.only_raid"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int affected = ImperialDefenseManager.rallyDefenders(serverLevel, this);

    if (affected <= 0) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.defense.no_guardsmen"), true);
        return;
    }

    setChanged();

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.rally_issued")
    );

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.rally_count", affected)
    );
}

public void fortifyDefenders(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.fortify.not_owner"), true);
        return;
    }

    if (!this.activeOrkRaid) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.fortify.only_raid"), true);
        return;
    }

    int warSupportCost = getFortifyDefendersWarSupportCost();

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.fortify.no_warsupport"), true);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.warsupport.required", warSupportCost), false);
        player.displayClientMessage(Component.translatable("msg.firstcrusade.warsupport.current", this.imperialWarSupport), false);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int affected = ImperialDefenseManager.fortifyDefenders(serverLevel, this);

    if (affected <= 0) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.defense.no_guardsmen"), true);
        return;
    }

    this.imperialWarSupport -= warSupportCost;

    setChanged();

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.fortify_issued")
    );

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.fortify_count", affected, warSupportCost)
    );
}

private int getFortifyDefendersWarSupportCost() {
    return ImperialCityLevelStats.fortifyWarSupportCost(this.cityLevel);
}

public void upgradeNearestGuardsmanToSpaceMarine(Player player, ItemStack catalystStack) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.not_owner"), true);
        return;
    }

    if (this.cityLevel < 3) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.req_level_3"), true);
        return;
    }

    if (catalystStack.isEmpty()) {
        return;
    }

    int ironCost = getSpaceMarineUpgradeIronCost();
    int scrapCost = getSpaceMarineUpgradeScrapCost();
    int coalCost = getSpaceMarineUpgradeCoalCost();
    int warSupportCost = getSpaceMarineUpgradeWarSupportCost();

    if (this.resources.getIron() < ironCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.missing_iron", ironCost - this.resources.getIron()), true);
        return;
    }

    if (this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.missing_scrap", scrapCost - this.resources.getScrapMetal()), true);
        return;
    }

    if (this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.missing_coal", coalCost - this.resources.getCoal()), true);
        return;
    }

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.missing_warsupport", warSupportCost - this.imperialWarSupport), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    GuardsmanEntity targetGuardsman = SpaceMarineUpgradeManager.findNearestUpgradeableGuardsman(
            serverLevel,
            this,
            player.blockPosition()
    );

    if (targetGuardsman == null) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.no_guardsman"), true);
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);
    this.imperialWarSupport -= warSupportCost;

    catalystStack.shrink(1);

    SpaceMarineUpgradeManager.upgradeToSpaceMarine(serverLevel, this, targetGuardsman);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.done"), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.sm.cost", ironCost, scrapCost, coalCost, warSupportCost), false);
}

private int getSpaceMarineUpgradeIronCost() {
    return ImperialCityLevelStats.spaceMarineUpgradeIronCost(this.cityLevel);
}

private int getSpaceMarineUpgradeScrapCost() {
    return ImperialCityLevelStats.spaceMarineUpgradeScrapCost(this.cityLevel);
}

private int getSpaceMarineUpgradeCoalCost() {
    return ImperialCityLevelStats.spaceMarineUpgradeCoalCost(this.cityLevel);
}

private int getSpaceMarineUpgradeWarSupportCost() {
    return ImperialCityLevelStats.spaceMarineUpgradeWarSupportCost(this.cityLevel);
}

    private void reduceSpaceMarinePromotionCooldown() {
    if (this.spaceMarinePromotionCooldownTicks <= 0) {
        return;
    }

    this.spaceMarinePromotionCooldownTicks -= 200;

    if (this.spaceMarinePromotionCooldownTicks < 0) {
        this.spaceMarinePromotionCooldownTicks = 0;
    }

    setChanged();
}

private void processAutomaticSpaceMarinePromotion(ServerLevel serverLevel) {
    if (this.cityLevel < 3) {
        this.pendingSpaceMarineCandidateUUID = null;
        return;
    }

    if (this.emperorGeneSeed <= 0) {
        return;
    }

    if (this.spaceMarinePromotionCooldownTicks > 0) {
        return;
    }

    if (this.pendingSpaceMarineCandidateUUID != null) {
        continuePendingSpaceMarinePromotion(serverLevel);
        return;
    }

    GuardsmanEntity candidate = SpaceMarineUpgradeManager.findBestAutomaticCandidate(serverLevel, this);

    if (candidate == null) {
        return;
    }

    this.pendingSpaceMarineCandidateUUID = candidate.getUUID();

    SpaceMarineUpgradeManager.commandCandidateToCore(serverLevel, this, candidate);

    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.sm_candidate", candidate.getGuardsmanRank().getDisplayName())
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.sm_candidate_moving")
    );
}

private void continuePendingSpaceMarinePromotion(ServerLevel serverLevel) {
    GuardsmanEntity candidate = SpaceMarineUpgradeManager.findGuardsmanByUUID(
            serverLevel,
            this,
            this.pendingSpaceMarineCandidateUUID
    );

    if (candidate == null || !candidate.isAlive()) {
        this.pendingSpaceMarineCandidateUUID = null;
        setChanged();
        return;
    }

    if (!SpaceMarineUpgradeManager.isEligibleForSpaceMarineUpgrade(candidate)) {
        this.pendingSpaceMarineCandidateUUID = null;
        setChanged();
        return;
    }

    SpaceMarineUpgradeManager.commandCandidateToCore(serverLevel, this, candidate);

    if (!SpaceMarineUpgradeManager.isNearPromotionPosition(this, candidate)) {
        return;
    }

    if (this.emperorGeneSeed <= 0) {
        return;
    }

    this.emperorGeneSeed--;

    SpaceMarineUpgradeManager.upgradeToSpaceMarine(serverLevel, this, candidate);

    this.pendingSpaceMarineCandidateUUID = null;
    this.spaceMarinePromotionCooldownTicks = getSpaceMarinePromotionCooldownTicks();

    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.gene_consumed", this.emperorGeneSeed)
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.sm_cooldown", this.spaceMarinePromotionCooldownTicks / 20)
    );
}

private int getSpaceMarinePromotionCooldownTicks() {
    return 24000;
}

    public void tryUpgradeCity(Player player, ItemStack plateStack) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.not_owner"), true);
        return;
    }

    if (!canUpgradeMore()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.max"), true);
        return;
    }

    int ironCost = getUpgradeIronCost();
    int scrapCost = getUpgradeScrapCost();
    int coalCost = getUpgradeCoalCost();
    int plateCost = getUpgradePlateCost();

    if (this.resources.getIron() < ironCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.missing_iron", ironCost - this.resources.getIron()), true);
        return;
    }

    if (this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.missing_scrap", scrapCost - this.resources.getScrapMetal()), true);
        return;
    }

    if (this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.missing_coal", coalCost - this.resources.getCoal()), true);
        return;
    }

    if (plateStack.getCount() < plateCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.missing_plate", plateCost - plateStack.getCount()), true);
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);
    plateStack.shrink(plateCost);

    this.cityLevel++;

    if (this.level instanceof ServerLevel serverLevel) {
        buildCityStructure(serverLevel);
        reorganizeExistingGuardsmen(serverLevel);
    }

    setChanged();

    if (this.level != null) {
        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
    }

    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.done", this.cityLevel), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.expanded"), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.reassigned"), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_storage", getStorageCapacity()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_production", getDailyIronProduction(), getDailyScrapProduction(), getDailyCoalProduction()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_military", getMilitaryCapacity()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_rank", getStartingGuardsmanRank().getDisplayName()), false);
}

// World-generated cities are established settlements, not fresh outposts: they start as a proper
// walled village (Core as the central keep, curtain wall around the whole town, towers and houses
// with beds). Called once by WorldSettlementSeeder right after the Core is placed.
private static final int AUTONOMOUS_VILLAGE_LEVEL = 4;
private static final int AUTONOMOUS_START_POPULATION = 18;
private static final int AUTONOMOUS_GARRISON = 8;

public void buildAutonomousVillage(ServerLevel serverLevel) {
    if (this.cityType == null) {
        assignCityType(serverLevel);
    }

    // The settlement's scale (Village/Town/City) drives its founding level, which the physical
    // builder maps to a wall radius. HIVE_CAPITAL never reaches here (its own path builds it).
    this.cityLevel = Math.max(this.cityLevel, this.settlementScale.getStartLevel());

    // Planned W40k settlement (plaza + avenues + curtain wall + zoned buildings), raised through
    // the footprint/anti-collision/entity-safety pipeline. The old loose ring of wooden houses
    // (buildSimpleSettlement) is kept but no longer called.
    // Register on the war map FIRST: the strategic sync prunes any settlement record whose city
    // isn't on the map, which would silently drop the layout plan built below.
    WorldWarMapData.get(serverLevel).recordCity(this.worldPosition);
    StrategicWarAIData warData = StrategicWarAIData.get(serverLevel);
    StrategicSettlementRecord record = warData.getOrCreateImperial(serverLevel, this.worldPosition);
    CityArchitect.buildFoundingSettlement(serverLevel, this, record);
    warData.setDirty();

    spawnStartingPopulation(serverLevel, AUTONOMOUS_START_POPULATION);
    spawnInitialGarrison(serverLevel, AUTONOMOUS_GARRISON);
    setChanged();
}

// Settles a starting population inside a freshly generated town so it reads as inhabited from the
// first moment (instead of slowly growing from empty). Citizens are assigned to this Core and find
// work / patrol / grow on their own from there.
private void spawnStartingPopulation(ServerLevel serverLevel, int count) {
    int radius = getCityStructureRadius();
    int spawned = 0;
    int attempts = 0;
    int maxAttempts = count * 10;

    while (spawned < count && attempts < maxAttempts) {
        attempts++;

        int dx = serverLevel.random.nextInt(radius * 2 - 4) - (radius - 2);
        int dz = serverLevel.random.nextInt(radius * 2 - 4) - (radius - 2);
        BlockPos pos = this.worldPosition.offset(dx, 0, dz);

        // Stand on solid ground with two blocks of clearance, never inside a wall or the Core.
        if (!serverLevel.isEmptyBlock(pos) || !serverLevel.isEmptyBlock(pos.above())) {
            continue;
        }
        if (serverLevel.isEmptyBlock(pos.below())) {
            continue;
        }

        ImperialCitizenEntity citizen = FCRegistry.IMPERIAL_CITIZEN.get().create(serverLevel);
        if (citizen == null) {
            break;
        }

        citizen.assignToCommandCore(this.worldPosition);
        citizen.moveTo(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );
        citizen.setCustomName(Component.literal("Imperial Citizen"));
        serverLevel.addFreshEntity(citizen);
        spawned++;
    }
}

private void buildCityStructure(ServerLevel serverLevel) {
    int radius = getCityStructureRadius();
    int wallHeight = getCityWallHeight();

    // Clear the whole footprint (plus a perimeter margin) of trees, leaves, plants and snow first,
    // so ambient terrain never pokes through or blocks the town.
    WorldGenPlacement.clearVegetation(serverLevel, this.worldPosition, radius + 4, wallHeight + 16);

    // Curtain wall around the whole town, corner towers, and a paved cross of main streets.
    buildFoundation(serverLevel, radius);
    buildOuterWall(serverLevel, radius, wallHeight);
    buildCornerTowers(serverLevel, radius, wallHeight);
    buildCentralRoad(serverLevel, radius);

    // The Core is the central keep: a small castle in the middle of the town.
    buildCentralKeep(serverLevel, CENTRAL_KEEP_HALF, wallHeight + 3);

    // Fill the rest of the interior with a grid of houses (each with beds), like a real village.
    buildHousingDistrict(serverLevel, radius);

    // The towering central hive spire rises behind the keep — the city's crowning landmark, climbing
    // far above the walls like the spire of an Imperial hive.
    buildCentralSpire(serverLevel, this.worldPosition.offset(-1, 0, -(radius / 2)), wallHeight + 26 + this.cityLevel * 4);
}

private static final int CENTRAL_KEEP_HALF = 6;

// The town's heart: a square gothic keep walled around the Core, with a southern gate, taller
// corner pillars and battlements. The Core itself sits in the courtyard at the centre.
private void buildCentralKeep(ServerLevel serverLevel, int keepHalf, int height) {
    BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    BlockState pillar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    BlockState crenel = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();

    for (int x = -keepHalf; x <= keepHalf; x++) {
        for (int z = -keepHalf; z <= keepHalf; z++) {
            boolean perimeter = Math.abs(x) == keepHalf || Math.abs(z) == keepHalf;
            if (!perimeter) {
                continue;
            }

            boolean gate = z == keepHalf && x >= -1 && x <= 1;
            boolean isCorner = Math.abs(x) == keepHalf && Math.abs(z) == keepHalf;
            int top = isCorner ? height + 2 : height;

            for (int y = 0; y < top; y++) {
                if (gate && y < 4) {
                    continue;
                }
                safePlace(serverLevel, this.worldPosition.offset(x, y, z), isCorner ? pillar : wall);
            }

            if (isCorner) {
                safePlace(serverLevel, this.worldPosition.offset(x, top, z), Blocks.LANTERN.defaultBlockState());
            } else if (!gate && ((x + z) & 1) == 0) {
                safePlace(serverLevel, this.worldPosition.offset(x, height, z), crenel);
            }
        }
    }
}

// Lays out the residential blocks of the town: gabled houses (each with beds) on a grid with wide
// streets, leaving an open plaza around the central keep and the main cross-roads (incl. the south
// gate) clear. Every house gets a street lamp at its corner so the city lights up at night. The
// number of houses scales with the wall radius, so bigger cities are bigger towns.
private void buildHousingDistrict(ServerLevel serverLevel, int radius) {
    int cell = 9;                                // grid cell pitch (house up to 7 + street)
    int inner = radius - 3;                      // keep houses off the curtain wall
    int plazaHalf = CENTRAL_KEEP_HALF + 4;       // open plaza ringing the keep
    int roadHalf = 2;                            // central cross-road corridor kept clear

    for (int sx = -inner; sx + 7 <= inner; sx += cell) {
        for (int sz = -inner; sz + 7 <= inner; sz += cell) {
            // Vary each house's footprint so the district isn't a wall of identical boxes.
            int w = 5 + serverLevel.random.nextInt(3);   // 5..7
            int d = 5 + serverLevel.random.nextInt(3);   // 5..7
            int x1 = sx + w - 1;
            int z1 = sz + d - 1;

            // Reserve the central plaza (the keep plus breathing room around it).
            if (x1 >= -plazaHalf && sx <= plazaHalf && z1 >= -plazaHalf && sz <= plazaHalf) {
                continue;
            }

            // Keep the main cross-roads (and the south gate) open.
            if ((x1 >= -roadHalf && sx <= roadHalf) || (z1 >= -roadHalf && sz <= roadHalf)) {
                continue;
            }

            // Leave the odd block as an open lamp-lit courtyard so it doesn't read as a solid grid.
            if (serverLevel.random.nextInt(6) == 0) {
                placeLampPost(serverLevel, this.worldPosition.offset(sx + w / 2, 0, sz + d / 2), 4);
                continue;
            }

            // Now and then a tall subsidiary hive spire rises among the houses, for vertical scale.
            if (serverLevel.random.nextInt(9) == 0) {
                buildTower(serverLevel, this.worldPosition.offset(sx + 1, 0, sz + 1), 12 + serverLevel.random.nextInt(12));
                continue;
            }

            int wallHeight = 4 + serverLevel.random.nextInt(2);   // 4..5
            buildSimpleHouse(serverLevel, this.worldPosition.offset(sx, 0, sz), w, d, wallHeight);

            // Street lamp on the corner facing the avenue.
            placeLampPost(serverLevel, this.worldPosition.offset(sx - 1, 0, sz - 1), 3);

            // Many hab-blocks vent a smoking manufactorum chimney — the industrial breath of a hive.
            if (serverLevel.random.nextInt(3) == 0) {
                placeSmokestack(serverLevel, this.worldPosition.offset(sx + 1, wallHeight, sz + 1), 3 + serverLevel.random.nextInt(3));
            }
        }
    }
}

// An industrial chimney venting smoke (a lit campfire), poking up through a hab-block roof.
private void placeSmokestack(ServerLevel serverLevel, BlockPos base, int height) {
    for (int y = 0; y < height; y++) {
        safePlace(serverLevel, base.offset(0, y, 0), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
    }
    safePlace(serverLevel, base.offset(0, height, 0), Blocks.CAMPFIRE.defaultBlockState());
}

// A gothic street lamp: a slim dark post crowned with a lantern, lighting the town after dark.
private void placeLampPost(ServerLevel serverLevel, BlockPos base, int height) {
    for (int y = 0; y < height; y++) {
        safePlace(serverLevel, base.offset(0, y, 0), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
    }
    safePlace(serverLevel, base.offset(0, height, 0), Blocks.LANTERN.defaultBlockState());
}

// A tall gothic spire: a 2x2 dark-stone shaft with banded buttresses, an overhanging
// battlement, a tapering steeple and a gilded dome topped with a glowing finial.
private void buildCentralSpire(ServerLevel serverLevel, BlockPos base, int height) {
    buildTower(serverLevel, base, height);

    // Extra arched windows up the shaft to read as a cathedral tower.
    for (int y = 4; y < height - 2; y += 4) {
        safePlace(serverLevel, base.offset(0, y, -1), Blocks.IRON_BARS.defaultBlockState());
        safePlace(serverLevel, base.offset(1, y, -1), Blocks.IRON_BARS.defaultBlockState());
    }
}

private void buildFoundation(ServerLevel serverLevel, int radius) {
    BlockState lightTile = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    BlockState darkTile = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

    for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
            // Pave the whole interior floor (replacing grass/dirt), so the city has a proper stone
            // plaza instead of patches of bare ground. A thin sub-floor keeps it solid over dips.
            BlockPos floor = this.worldPosition.offset(x, -1, z);
            BlockState tile = ((x + z) & 1) == 0 ? lightTile : darkTile;
            serverLevel.setBlock(floor, tile, 3);

            BlockPos below = floor.below();
            if (serverLevel.getBlockState(below).isAir()
                    || !serverLevel.getBlockState(below).isCollisionShapeFullBlock(serverLevel, below)) {
                serverLevel.setBlock(below, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 3);
            }
        }
    }
}

private void buildOuterWall(ServerLevel serverLevel, int radius, int wallHeight) {
    BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    BlockState pillar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    BlockState crenel = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
    BlockState window = Blocks.IRON_BARS.defaultBlockState();

    for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
            boolean isWall = Math.abs(x) == radius || Math.abs(z) == radius;

            if (!isWall) {
                continue;
            }

            // Southern gate: an arched opening (clear below, wall arches over the top).
            boolean isGate = z == radius && x >= -2 && x <= 2;

            // Buttress pillars every few blocks rise above the curtain wall.
            boolean isPillar = (Math.abs(z) == radius && Math.floorMod(x, 5) == 0)
                    || (Math.abs(x) == radius && Math.floorMod(z, 5) == 0);

            int columnTop = isPillar ? wallHeight + 2 : wallHeight;

            for (int y = 0; y < columnTop; y++) {
                if (isGate && y < 5) {
                    continue;
                }

                BlockState mat = isPillar ? pillar : wall;

                // Tall narrow gothic windows between the buttresses.
                if (!isPillar && !isGate && (y == wallHeight / 2 || y == wallHeight / 2 + 1)
                        && (Math.floorMod(x, 5) == 2 || Math.floorMod(z, 5) == 2)) {
                    mat = window;
                }

                safePlace(serverLevel, this.worldPosition.offset(x, y, z), mat);
            }

            if (isPillar) {
                safePlace(serverLevel, this.worldPosition.offset(x, columnTop, z), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                safePlace(serverLevel, this.worldPosition.offset(x, columnTop + 1, z), Blocks.LANTERN.defaultBlockState());
            } else if (!isGate && ((x + z) & 1) == 0) {
                safePlace(serverLevel, this.worldPosition.offset(x, wallHeight, z), crenel);
            }
        }
    }
}

private void buildCornerTowers(ServerLevel serverLevel, int radius, int wallHeight) {
    int height = wallHeight + 8;
    buildTower(serverLevel, this.worldPosition.offset(radius, 0, radius), height);
    buildTower(serverLevel, this.worldPosition.offset(-radius, 0, radius), height);
    buildTower(serverLevel, this.worldPosition.offset(radius, 0, -radius), height);
    buildTower(serverLevel, this.worldPosition.offset(-radius, 0, -radius), height);
}

// A gothic Imperial spire: dark 2x2 shaft with banded buttresses and lit windows, an
// overhanging battlement, a tapering steeple, and a gilded dome with a glowing finial.
private void buildTower(ServerLevel serverLevel, BlockPos corner, int height) {
    BlockState shaft = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    BlockState band = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

    for (int y = 0; y < height; y++) {
        BlockState mat = (y % 5 == 4) ? band : shaft;
        safePlace(serverLevel, corner.offset(0, y, 0), mat);
        safePlace(serverLevel, corner.offset(1, y, 0), mat);
        safePlace(serverLevel, corner.offset(0, y, 1), mat);
        safePlace(serverLevel, corner.offset(1, y, 1), mat);
    }

    int topY = height;

    // Overhanging battlement platform (4x4).
    for (int x = -1; x <= 2; x++) {
        for (int z = -1; z <= 2; z++) {
            safePlace(serverLevel, corner.offset(x, topY, z), band);
        }
    }

    // Crenellations around the platform edge.
    for (int t = -1; t <= 2; t++) {
        if ((t & 1) == 0) {
            safePlace(serverLevel, corner.offset(t, topY + 1, -1), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
            safePlace(serverLevel, corner.offset(t, topY + 1, 2), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
            safePlace(serverLevel, corner.offset(-1, topY + 1, t), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
            safePlace(serverLevel, corner.offset(2, topY + 1, t), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
        }
    }

    // Tapering steeple above the platform.
    int spireY = topY + 1;
    for (int i = 0; i < 4; i++) {
        BlockState m = (i % 2 == 0) ? band : shaft;
        safePlace(serverLevel, corner.offset(0, spireY + i, 0), m);
        safePlace(serverLevel, corner.offset(1, spireY + i, 0), m);
        safePlace(serverLevel, corner.offset(0, spireY + i, 1), m);
        safePlace(serverLevel, corner.offset(1, spireY + i, 1), m);
    }

    // Gilded dome and glowing finial.
    int domeY = spireY + 4;
    safePlace(serverLevel, corner.offset(0, domeY, 0), Blocks.GOLD_BLOCK.defaultBlockState());
    safePlace(serverLevel, corner.offset(1, domeY, 0), Blocks.GOLD_BLOCK.defaultBlockState());
    safePlace(serverLevel, corner.offset(0, domeY, 1), Blocks.GOLD_BLOCK.defaultBlockState());
    safePlace(serverLevel, corner.offset(1, domeY, 1), Blocks.GOLD_BLOCK.defaultBlockState());
    safePlace(serverLevel, corner.offset(0, domeY + 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());
    safePlace(serverLevel, corner.offset(0, domeY + 2, 0), Blocks.END_ROD.defaultBlockState());

    // A lantern hung at the base of the steeple.
    safePlace(serverLevel, corner.offset(0, spireY, -1), Blocks.LANTERN.defaultBlockState());
}

// A gothic Imperial hab-block: dark brick walls with tall narrow lancet windows, a gabled tiled
// roof, a gilded finial, and beds inside. Footprint and roof axis vary per house so the district
// reads as a real town of distinct buildings rather than a grid of identical boxes.
private void buildSimpleHouse(ServerLevel serverLevel, BlockPos start, int width, int depth, int height) {
    BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    BlockState corner = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    BlockState window = Blocks.IRON_BARS.defaultBlockState();

    for (int x = 0; x < width; x++) {
        for (int z = 0; z < depth; z++) {
            safePlace(serverLevel, start.offset(x, -1, z), Blocks.COBBLED_DEEPSLATE.defaultBlockState());

            boolean border = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
            boolean isCorner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);

            if (border) {
                for (int y = 0; y < height; y++) {
                    boolean doorway = z == 0 && x == width / 2 && y <= 1;
                    // Tall, narrow lancet windows centred on each wall (gothic 40k look).
                    boolean windowSlot = !isCorner && (x == width / 2 || z == depth / 2)
                            && y >= 1 && y <= height - 2;

                    if (doorway) {
                        continue;
                    }

                    BlockState mat = isCorner ? corner : (windowSlot ? window : wall);
                    safePlace(serverLevel, start.offset(x, y, z), mat);
                }
            }
        }
    }

    // A pitched, tiled gable roof along the longer axis (varies the silhouette house to house).
    boolean ridgeAlongX = width >= depth;
    buildGableRoof(serverLevel, start, width, depth, height, ridgeAlongX);

    // Gilded finial crowning the ridge.
    int peak = (ridgeAlongX ? depth : width) / 2;
    safePlace(serverLevel, start.offset(width / 2, height + peak + 1, depth / 2), Blocks.GOLD_BLOCK.defaultBlockState());
    safePlace(serverLevel, start.offset(width / 2, height + peak + 2, depth / 2), Blocks.END_ROD.defaultBlockState());

    // A lantern hung from the ridge lights the home at night.
    safePlace(serverLevel, start.offset(width / 2, height - 1, depth / 2), Blocks.LANTERN.defaultBlockState());

    // Beds (homes are where the population sleeps and grows; beds also register as home POIs).
    placeBed(serverLevel, start.offset(1, 0, 1), Direction.SOUTH);
    if (width >= 6 && depth >= 5) {
        placeBed(serverLevel, start.offset(width - 2, 0, 1), Direction.SOUTH);
    }
}

// Builds a tiled gable roof over a [0..width-1] x [0..depth-1] house whose walls top out at y=height,
// with a one-block eave overhang and the gable-end walls filled in. The ridge runs along the longer
// axis. Roof stairs face outward (away from the ridge); flip the facings here if it ever looks
// inverted in game.
private void buildGableRoof(ServerLevel serverLevel, BlockPos start, int width, int depth, int height, boolean ridgeAlongX) {
    BlockState ridge = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    BlockState gableWall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();

    if (ridgeAlongX) {
        int peak = depth / 2;
        for (int i = 0; i <= peak; i++) {
            int y = height + i;
            int zf = i;
            int zb = depth - 1 - i;

            for (int x = -1; x <= width; x++) {
                if (zf < zb) {
                    placeRoofStair(serverLevel, start.offset(x, y, zf), Direction.SOUTH);
                    placeRoofStair(serverLevel, start.offset(x, y, zb), Direction.NORTH);
                } else {
                    safePlace(serverLevel, start.offset(x, y, zf), ridge);
                }
            }

            // Fill the triangular gable ends so the attic isn't open.
            for (int z = zf + 1; z < zb; z++) {
                safePlace(serverLevel, start.offset(0, y, z), gableWall);
                safePlace(serverLevel, start.offset(width - 1, y, z), gableWall);
            }
        }
    } else {
        int peak = width / 2;
        for (int i = 0; i <= peak; i++) {
            int y = height + i;
            int xf = i;
            int xb = width - 1 - i;

            for (int z = -1; z <= depth; z++) {
                if (xf < xb) {
                    placeRoofStair(serverLevel, start.offset(xf, y, z), Direction.WEST);
                    placeRoofStair(serverLevel, start.offset(xb, y, z), Direction.EAST);
                } else {
                    safePlace(serverLevel, start.offset(xf, y, z), ridge);
                }
            }

            for (int x = xf + 1; x < xb; x++) {
                safePlace(serverLevel, start.offset(x, y, 0), gableWall);
                safePlace(serverLevel, start.offset(x, y, depth - 1), gableWall);
            }
        }
    }
}

// Places a roof stair (into empty space only) facing the given direction.
private void placeRoofStair(ServerLevel serverLevel, BlockPos pos, Direction facing) {
    if (pos.equals(this.worldPosition) || !serverLevel.getBlockState(pos).isAir()) {
        return;
    }

    serverLevel.setBlock(pos, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 3);
}

// Places a two-block bed (foot + head) facing the given direction, only into empty space.
private void placeBed(ServerLevel serverLevel, BlockPos footPos, Direction facing) {
    BlockPos headPos = footPos.relative(facing);

    if (footPos.equals(this.worldPosition) || headPos.equals(this.worldPosition)) {
        return;
    }

    if (!serverLevel.getBlockState(footPos).isAir() || !serverLevel.getBlockState(headPos).isAir()) {
        return;
    }

    serverLevel.setBlock(footPos, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, facing)
            .setValue(BedBlock.PART, BedPart.FOOT), 3);
    serverLevel.setBlock(headPos, Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING, facing)
            .setValue(BedBlock.PART, BedPart.HEAD), 3);
}

private void buildCentralRoad(ServerLevel serverLevel, int radius) {
    BlockState road = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    BlockState inlay = Blocks.GILDED_BLACKSTONE.defaultBlockState();

    for (int z = -radius + 1; z <= radius - 1; z++) {
        forcePlaceFloor(serverLevel, this.worldPosition.offset(0, -1, z), inlay);
        forcePlaceFloor(serverLevel, this.worldPosition.offset(1, -1, z), road);
        forcePlaceFloor(serverLevel, this.worldPosition.offset(-1, -1, z), road);
    }

    for (int x = -radius + 1; x <= radius - 1; x++) {
        forcePlaceFloor(serverLevel, this.worldPosition.offset(x, -1, 0), inlay);
        forcePlaceFloor(serverLevel, this.worldPosition.offset(x, -1, 1), road);
        forcePlaceFloor(serverLevel, this.worldPosition.offset(x, -1, -1), road);
    }

    // Lamp posts line the grand avenues so the city glows after dark (skipping the keep around the
    // centre so they don't punch through its wall).
    for (int z = -radius + 4; z <= radius - 4; z += 6) {
        if (Math.abs(z) <= CENTRAL_KEEP_HALF + 1) {
            continue;
        }
        placeLampPost(serverLevel, this.worldPosition.offset(2, 0, z), 3);
        placeLampPost(serverLevel, this.worldPosition.offset(-2, 0, z), 3);
    }
    for (int x = -radius + 4; x <= radius - 4; x += 6) {
        if (Math.abs(x) <= CENTRAL_KEEP_HALF + 1) {
            continue;
        }
        placeLampPost(serverLevel, this.worldPosition.offset(x, 0, 2), 3);
        placeLampPost(serverLevel, this.worldPosition.offset(x, 0, -2), 3);
    }
}

// Places a floor block, overwriting the plain foundation tiles (but never the Core itself).
private void forcePlaceFloor(ServerLevel serverLevel, BlockPos pos, BlockState state) {
    if (!pos.equals(this.worldPosition)) {
        serverLevel.setBlock(pos, state, 3);
    }
}

private void safePlace(ServerLevel serverLevel, BlockPos pos, BlockState state) {
    if (pos.equals(this.worldPosition)) {
        return;
    }

    if (serverLevel.getBlockState(pos).isAir()) {
        serverLevel.setBlock(pos, state, 3);
    }
}

private void trySpawnOrkRaid(ServerLevel serverLevel) {
    // The fixed test world relies on the seeded Ork cities' war parties, not artificial raids.
    if (ExampleMod.TEST_FIXED_WORLD) {
        return;
    }

    long currentDay = serverLevel.getDayTime() / 24000L;

    if (currentDay < 1) {
        return;
    }

    if (this.recruitedGuardsmen <= 0 && this.cityLevel <= 1) {
        return;
    }

    if (this.lastOrkRaidDay < 0) {
        this.lastOrkRaidDay = currentDay;
        setChanged();
        return;
    }

    long daysSinceLastRaid = currentDay - this.lastOrkRaidDay;

    if (daysSinceLastRaid < getRaidCooldownDays()) {
        return;
    }

    if (serverLevel.random.nextFloat() > getRaidChance()) {
        return;
    }

    spawnOrkRaid(serverLevel, currentDay, false);
}

public void forceOrkRaid(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.raid.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    long currentDay = serverLevel.getDayTime() / 24000L;
    spawnOrkRaid(serverLevel, currentDay, true);
}
private void checkActiveOrkRaid(ServerLevel serverLevel) {
    if (!this.activeOrkRaid) {
        return;
    }

    this.activeOrkRaidTicks += 200;

    OrkRaidManager.updateRaiders(serverLevel, this);

    int remainingRaiders = OrkRaidManager.countRaidersInsideRadius(serverLevel, this, 160);

    if (remainingRaiders <= 0 && this.activeOrkRaidTicks >= 400) {
        finishOrkRaidVictory(serverLevel);
        return;
    }

    int closeRaiders = OrkRaidManager.countRaidersInsideRadius(serverLevel, this, 12);

    if (closeRaiders > 0) {
        this.raidPressureTicks += 200;

        if (this.raidPressureTicks >= 600) {
            int damage = closeRaiders * getRaidCoreDamagePerWave();

            this.cityIntegrity = Math.max(0, this.cityIntegrity - damage);
            this.raidPressureTicks = 0;

            setChanged();

            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    this.worldPosition,
                    Component.translatable("msg.firstcrusade.bcast.raid_damaging", this.cityIntegrity)
            );

            if (this.cityIntegrity <= 0) {
                finishOrkRaidDefeat(serverLevel);
                return;
            }
        }
    } else {
        this.raidPressureTicks = 0;
    }

    if (this.activeOrkRaidTicks % 1200 == 0) {
        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                this.worldPosition,
                Component.translatable("msg.firstcrusade.bcast.raid_active", remainingRaiders, this.cityIntegrity)
        );
    }

    if (this.activeOrkRaidTicks >= 12000) {
        this.activeOrkRaid = false;
        this.activeOrkRaidTicks = 0;
        this.raidPressureTicks = 0;

        setChanged();

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                this.worldPosition,
                Component.translatable("msg.firstcrusade.bcast.raid_scattered")
        );
    }
}

private void finishOrkRaidVictory(ServerLevel serverLevel) {
    this.activeOrkRaid = false;
    this.activeOrkRaidTicks = 0;
    this.raidPressureTicks = 0;

    this.orkRaidVictories++;

    int ironReward = getRaidVictoryIronReward();
    int scrapReward = getRaidVictoryScrapReward();
    int coalReward = getRaidVictoryCoalReward();
    int warSupportReward = getRaidVictoryWarSupportReward();
    int integrityRepairReward = getRaidVictoryIntegrityRepairReward();

    addIron(ironReward);
    addScrapMetal(scrapReward);
    addCoal(coalReward);

    this.imperialWarSupport = Math.min(999999, this.imperialWarSupport + warSupportReward);
    this.cityIntegrity = Math.min(100, this.cityIntegrity + integrityRepairReward);

    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.raid_victory")
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.victory_reward", ironReward, scrapReward, coalReward, warSupportReward)
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.victory_repair", integrityRepairReward)
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.victory_total", this.orkRaidVictories)
    );
}

private int getRaidVictoryIronReward() {
    return switch (this.cityLevel) {
        case 1 -> 40;
        case 2 -> 100;
        case 3 -> 250;
        case 4 -> 600;
        case 5 -> 1500;
        default -> 40;
    };
}

private void finishOrkRaidDefeat(ServerLevel serverLevel) {
    this.activeOrkRaid = false;
    this.activeOrkRaidTicks = 0;
    this.raidPressureTicks = 0;

    int lostIron = this.resources.getIron() / 2;
    int lostScrap = this.resources.getScrapMetal() / 2;
    int lostCoal = this.resources.getCoal() / 2;

    this.resources.remove(ImperialResourceType.IRON, lostIron);
    this.resources.remove(ImperialResourceType.SCRAP, lostScrap);
    this.resources.remove(ImperialResourceType.COAL, lostCoal);

    this.imperialWarSupport = Math.max(0, this.imperialWarSupport - getRaidDefeatWarSupportPenalty());
    this.cityIntegrity = 25;

    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.defeat_overrun")
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.defeat_lost", lostIron, lostScrap, lostCoal)
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            Component.translatable("msg.firstcrusade.bcast.defeat_restored")
    );
}

public void repairCity(Player player, ItemStack plateStack) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.repair.not_owner"), true);
        return;
    }

    if (this.cityIntegrity >= 100) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.repair.full"), true);
        return;
    }

    if (plateStack.isEmpty()) {
        return;
    }

    int repairAmount = getManualRepairAmount();

    plateStack.shrink(1);
    this.cityIntegrity = Math.min(100, this.cityIntegrity + repairAmount);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.repair.done"), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.repair.integrity", this.cityIntegrity), false);
}

private int getManualRepairAmount() {
    return switch (this.cityLevel) {
        case 1 -> 20;
        case 2 -> 18;
        case 3 -> 15;
        case 4 -> 12;
        case 5 -> 10;
        default -> 15;
    };
}

private int getRaidCoreDamagePerWave() {
    return switch (this.cityLevel) {
        case 1 -> 4;
        case 2 -> 5;
        case 3 -> 6;
        case 4 -> 7;
        case 5 -> 8;
        default -> 4;
    };
}

private int getRaidVictoryIntegrityRepairReward() {
    return switch (this.cityLevel) {
        case 1 -> 10;
        case 2 -> 12;
        case 3 -> 15;
        case 4 -> 18;
        case 5 -> 25;
        default -> 10;
    };
}

private int getRaidDefeatWarSupportPenalty() {
    return switch (this.cityLevel) {
        case 1 -> 5;
        case 2 -> 10;
        case 3 -> 20;
        case 4 -> 40;
        case 5 -> 80;
        default -> 5;
    };
}

private int getRaidVictoryWarSupportReward() {
    return switch (this.cityLevel) {
        case 1 -> 5;
        case 2 -> 12;
        case 3 -> 25;
        case 4 -> 55;
        case 5 -> 120;
        default -> 5;
    };
}

private String getThreatLevelName() {
    int threatScore = getThreatScore();

    if (threatScore <= 3) {
        return "Low";
    }

    if (threatScore <= 7) {
        return "Rising";
    }

    if (threatScore <= 12) {
        return "Dangerous";
    }

    if (threatScore <= 18) {
        return "Critical";
    }

    return "WAAAGH!";
}

private String getThreatLevelDescription() {
    String threatName = getThreatLevelName();

    return switch (threatName) {
        case "Low" -> "Minor Ork activity detected.";
        case "Rising" -> "Ork scouts are watching this settlement.";
        case "Dangerous" -> "Ork warbands are gathering nearby.";
        case "Critical" -> "Major Ork assault risk is high.";
        case "WAAAGH!" -> "The settlement has become a major Ork target.";
        default -> "Unknown threat level.";
    };
}

private int getThreatScore() {
    int score = 0;

    score += this.cityLevel * 2;
    score += Math.min(this.orkRaidCount, 10);

    if (this.activeOrkRaid) {
        score += 5;
    }

    score -= Math.min(this.orkRaidVictories, 8);

    if (score < 0) {
        score = 0;
    }

    return score;
}

private int getRaidVictoryScrapReward() {
    return switch (this.cityLevel) {
        case 1 -> 25;
        case 2 -> 70;
        case 3 -> 180;
        case 4 -> 420;
        case 5 -> 1000;
        default -> 25;
    };
}

private int getRaidVictoryCoalReward() {
    return switch (this.cityLevel) {
        case 1 -> 15;
        case 2 -> 40;
        case 3 -> 100;
        case 4 -> 240;
        case 5 -> 600;
        default -> 15;
    };
}

private void spawnOrkRaid(ServerLevel serverLevel, long currentDay, boolean forced) {
    if (this.activeOrkRaid) {
        if (forced) {
            OrkRaidManager.notifyNearbyPlayers(
                    serverLevel,
                    this.worldPosition,
                    Component.translatable("msg.firstcrusade.bcast.raid_already")
            );
        }

        return;
    }

    OrkRaidManager.spawnRaid(serverLevel, this, forced, this.orkRaidCount);

    this.lastOrkRaidDay = currentDay;
    this.orkRaidCount++;

    this.activeOrkRaid = true;
    this.activeOrkRaidTicks = 0;

    setChanged();
}

private int getRaidCooldownDays() {
    return switch (this.cityLevel) {
        case 1 -> 6;
        case 2 -> 5;
        case 3 -> 4;
        case 4 -> 3;
        case 5 -> 3;
        default -> 6;
    };
}

private float getRaidChance() {
    return switch (this.cityLevel) {
        case 1 -> 0.12F;
        case 2 -> 0.18F;
        case 3 -> 0.24F;
        case 4 -> 0.32F;
        case 5 -> 0.40F;
        default -> 0.12F;
    };
}

private int getCityStructureRadius() {
    return switch (this.cityLevel) {
        case 1 -> 8;
        case 2 -> 15;
        case 3 -> 22;
        case 4 -> 30;
        case 5 -> 40;
        default -> 8;
    };
}

private int getCityWallHeight() {
    return switch (this.cityLevel) {
        case 1 -> 1;
        case 2 -> 3;
        case 3 -> 5;
        case 4 -> 7;
        case 5 -> 9;
        default -> 1;
    };
}

public GuardsmanRank getStartingGuardsmanRank() {
    GuardsmanRank baseRank = switch (this.cityLevel) {
        case 1 -> GuardsmanRank.RECRUIT;
        case 2 -> GuardsmanRank.GUARDSMAN;
        case 3 -> GuardsmanRank.VETERAN;
        case 4 -> GuardsmanRank.SERGEANT;
        case 5 -> GuardsmanRank.LIEUTENANT;
        default -> GuardsmanRank.RECRUIT;
    };

    // City type shapes its soldiery: Fortress trains harder, Hive levies en masse.
    return baseRank.advance(getCityType().getRecruitRankBonus());
}

public void setOwner(Player player) {
    this.ownerUUID = player.getUUID();
    this.ownerName = player.getName().getString();
    setChanged();
}

public boolean hasOwner() {
    return this.ownerUUID != null;
}

public boolean isOwner(Player player) {
    return this.ownerUUID != null && this.ownerUUID.equals(player.getUUID());
}

public boolean canUpgradeMore() {
    return this.cityLevel < MAX_CITY_LEVEL;
}

public void showSettlementStatus(Player player) {
    ImperialMilitaryReportManager.showReport(player, this);
}

public void openCommandInterface(Player player) {
    if (!(player instanceof ServerPlayer serverPlayer)) {
        return;
    }

    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.interface.not_owner"), true);
        return;
    }

    NetworkHooks.openScreen(
            serverPlayer,
            new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("block.firstcrusade.imperial_command_core");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                    return new ImperialCommandCoreMenu(containerId, playerInventory, ImperialCommandCoreBlockEntity.this);
                }
            },
            this.worldPosition
    );
}

public String getBaseName() {
    return this.baseName;
}

public ImperialCityType getCityType() {
    return this.cityType == null ? ImperialCityType.CIVILISED : this.cityType;
}

// Size axis of this settlement (separate from the theme). Set at founding by the seeder.
public SettlementScale getSettlementScale() {
    return this.settlementScale;
}

public void setSettlementScale(SettlementScale scale) {
    this.settlementScale = scale == null ? SettlementScale.TOWN : scale;
    setChanged();
}

public int getCityTypeOrdinal() {
    return getCityType().ordinal();
}

// Global Imperial Crusade tier (0-4) — the strategic overlord state shared by every city (Fase D).
public int getCrusadeTier() {
    if (this.level instanceof ServerLevel serverLevel) {
        return ImperiumOverlordManager.getTier(serverLevel);
    }

    return 0;
}

// The radius (in blocks) the city claims and defends as its own territory; it widens as the city
// grows (Fase D). Used as the reach of the Primarch's defence and shown in the GUI.
public int getTerritoryRadius() {
    return 64 + Math.max(1, this.cityLevel) * 16;
}

// ----- Governor persona & build border -----

// Picks this city's Governor once, biased by the city type. Called the first server tick a city
// has no Governor yet (covers both freshly placed and older saved cities).
private void ensureGovernor(ServerLevel serverLevel) {
    this.governorPersonality = ImperialGovernorPersonality.pickForCityType(getCityType(), serverLevel.random);

    if (this.governorNameId < 0) {
        this.governorNameId = ImperialGovernorManager.randomNameId(serverLevel.random);
    }

    setChanged();
}

public ImperialGovernorPersonality getGovernorPersonality() {
    return this.governorPersonality == null ? ImperialGovernorPersonality.DEFAULT : this.governorPersonality;
}

public int getGovernorPersonalityOrdinal() {
    return getGovernorPersonality().ordinal();
}

public int getGovernorNameId() {
    return Math.max(0, this.governorNameId);
}

public boolean isGovernanceDelegated() {
    return this.governanceDelegated;
}

// The Governor runs the city when it is unclaimed, or when its player owner has delegated to it.
public boolean isGoverned() {
    return !hasOwner() || this.governanceDelegated;
}

// GUI code: 0 = unclaimed (Governor rules), 1 = owned & run by the player, 2 = owned & delegated.
public int getGovernanceState() {
    if (!hasOwner()) {
        return 0;
    }

    return this.governanceDelegated ? 2 : 1;
}

// Owner toggles whether the Governor runs the city in their absence (appoint / recall).
public void toggleGovernanceDelegation(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.interface.not_owner"), true);
        return;
    }

    this.governanceDelegated = !this.governanceDelegated;
    setChanged();

    String governorName = ImperialGovernorManager.nameForId(getGovernorNameId());
    Component key = this.governanceDelegated
            ? Component.translatable("msg.firstcrusade.governor.appointed", governorName, this.baseName)
            : Component.translatable("msg.firstcrusade.governor.recalled", governorName);
    player.displayClientMessage(key, true);
}

// The radius (blocks) the city may build its work-sites within. It is a hard, level-scaled space
// limit that grows as the city levels up (the MineColonies-style expanding plot); an Architect
// Governor claims a little extra room.
public int getBuildBorderRadius() {
    int base = switch (this.cityLevel) {
        case 1 -> 16;
        case 2 -> 24;
        case 3 -> 34;
        case 4 -> 46;
        case 5 -> 60;
        default -> 16;
    };

    return base + getGovernorPersonality().getBorderBonus();
}

// Shows the owner the current build border as a ring of particles.
public void surveyBuildBorder(Player player) {
    if (!(player instanceof ServerPlayer serverPlayer) || !(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.interface.not_owner"), true);
        return;
    }

    ImperialGovernorManager.surveyBuildBorder(serverLevel, serverPlayer, this.worldPosition, getBuildBorderRadius());
    player.displayClientMessage(
            Component.translatable("msg.firstcrusade.governor.surveyed", getBuildBorderRadius()), true);
}

// =========================
// City Builder Tool
// =========================

// Hands the owner the Ferramenta de Construção, bound to this Core (see CityBuilderToolItem).
public void giveBuilderTool(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.not_owner"), true);
        return;
    }

    ItemStack tool = new ItemStack(FCRegistry.CITY_BUILDER_TOOL.get());
    CityBuilderToolItem.bindToCore(tool, this.worldPosition);

    if (!player.getInventory().add(tool)) {
        player.drop(tool, false);
    }

    player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.given"), true);
}

// Places a structure the owner picked with the Builder Tool at a chosen position: validates
// ownership, city level, the build border, a clear+solid footprint and the resource cost, then
// builds/binds/staffs it through the same manager path the Core's auto-build uses.
public void placeStructureWithTool(Player player, CityStructureType type, BlockPos pos) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    if (this.cityLevel < type.getRequiredLevel()) {
        player.displayClientMessage(
                Component.translatable("msg.firstcrusade.builder.need_level", type.getRequiredLevel()), true);
        return;
    }

    int border = getBuildBorderRadius();
    double dx = (pos.getX() + 0.5D) - (this.worldPosition.getX() + 0.5D);
    double dz = (pos.getZ() + 0.5D) - (this.worldPosition.getZ() + 0.5D);

    if (dx * dx + dz * dz > (double) border * border) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.out_of_border", border), true);
        return;
    }

    if (!CityBuilderPlacement.isAreaBuildable(serverLevel, pos, type.getFootprintRadius(), type.getFootprintHeight())) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.blocked"), true);
        return;
    }

    if (!this.resources.has(type.getIronCost(), type.getScrapCost(), type.getCoalCost())) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.need_res",
                type.getIronCost(), type.getScrapCost(), type.getCoalCost()), true);
        return;
    }

    type.place(serverLevel, this, player, pos);
    this.resources.spend(type.getIronCost(), type.getScrapCost(), type.getCoalCost());
    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.builder.built",
            Component.translatable(type.getNameKey())), true);
}

// Live threat from actual nearby enemies (quantity x quality), not raid history.
public int getLiveThreatScore() {
    if (this.level instanceof ServerLevel serverLevel) {
        return ThreatAssessmentManager.assessThreat(
                serverLevel,
                this.worldPosition,
                ThreatAssessmentManager.DEFAULT_RADIUS
        );
    }

    return 0;
}

public int getLiveThreatLevel() {
    return ThreatAssessmentManager.threatLevel(getLiveThreatScore());
}

private void assignCityType(ServerLevel serverLevel) {
    this.cityType = pickCityTypeForBiome(serverLevel, this.worldPosition, serverLevel.random);

    // Only rename a still-default outpost so player-claimed names are kept.
    if (this.baseName == null || this.baseName.isEmpty() || this.baseName.equals("Imperial Outpost")) {
        this.baseName = this.cityType.getDisplayName();
    }

    setChanged();
}

// The settlement's type is biased by the biome it is founded in, so placement feels intentional
// (a desert breeds a Mining city, a jungle a Death World, plains an Agri city, ...). Biomes with no
// clear theme fall back to a random type, which keeps Hive/Forge/Shrine/Civilised in rotation.
private static ImperialCityType pickCityTypeForBiome(ServerLevel serverLevel, BlockPos pos, net.minecraft.util.RandomSource random) {
    String biome = serverLevel.getBiome(pos)
            .unwrapKey()
            .map(key -> key.location().getPath())
            .orElse("");

    if (contains(biome, "desert", "badlands", "mesa", "mountain", "peak", "slope", "hill", "stony", "windswept")) {
        return ImperialCityType.MINING;
    }

    if (contains(biome, "jungle", "bamboo")) {
        return ImperialCityType.DEATH_WORLD;
    }

    if (contains(biome, "swamp", "mangrove")) {
        return ImperialCityType.PENAL;
    }

    if (contains(biome, "snowy", "frozen", "ice", "taiga", "grove")) {
        return ImperialCityType.FORTRESS;
    }

    if (contains(biome, "plains", "meadow", "savanna", "sunflower")) {
        return ImperialCityType.AGRI;
    }

    // Forest/ocean/other: no strong theme -> keep variety with a random type.
    return ImperialCityType.random(random);
}

private static boolean contains(String value, String... needles) {
    for (String needle : needles) {
        if (value.contains(needle)) {
            return true;
        }
    }

    return false;
}

public String getOwnerName() {
    return this.ownerName;
}

public int getCityLevel() {
    return this.cityLevel;
}

public int getIron() {
    return this.resources.getIron();
}

public int getCoal() {
    return this.resources.getCoal();
}

public int getScrapMetal() {
    return this.resources.getScrapMetal();
}

public int getGold() {
    return this.resources.getGold();
}

public int getEmerald() {
    return this.resources.getEmerald();
}

public int getCrusadium() {
    return this.resources.getCrusadium();
}

public int getCityMorale() {
    return this.cityMorale;
}

public void setCityMorale(int morale) {
    int clamped = ImperialCityMoraleManager.clamp(morale);

    if (clamped != this.cityMorale) {
        this.cityMorale = clamped;
        setChanged();
    }
}

public int getCityIntegrityValue() {
    return this.cityIntegrity;
}

public int getImperialWarSupportValue() {
    return this.imperialWarSupport;
}

public boolean hasActiveOrkRaid() {
    return this.activeOrkRaid;
}

public int getActiveOrkRaidSeconds() {
    return this.activeOrkRaidTicks / 20;
}

public int getOrkRaidCountValue() {
    return this.orkRaidCount;
}

public int getOrkRaidVictoriesValue() {
    return this.orkRaidVictories;
}

public int getReinforcementCooldownSeconds() {
    return this.reinforcementCooldownTicks / 20;
}

public int getSpaceMarinePromotionCooldownSeconds() {
    return this.spaceMarinePromotionCooldownTicks / 20;
}

public boolean hasPendingSpaceMarineCandidate() {
    return this.pendingSpaceMarineCandidateUUID != null;
}

public String getThreatLevelNameForReport() {
    return getThreatLevelName();
}

public String getThreatLevelDescriptionForReport() {
    return getThreatLevelDescription();
}

public int getRecruitedGuardsmen() {
    return this.recruitedGuardsmen;
}

public int getStorageCapacity() {
    return ImperialCityLevelStats.storageCapacity(this.cityLevel);
}

public int getMilitaryCapacity() {
    int cap = ImperialCityLevelStats.militaryCapacity(this.cityLevel);
    return ExampleMod.TEST_FIXED_WORLD ? Math.min(ExampleMod.TEST_WARRIOR_CAP, cap) : cap;
}

// Test/seeder hook: point this city at an existing Ork city so it marches on it, without seeding
// its own extra camp.
public void setKnownOrkCamp(BlockPos campPos) {
    this.orkCampPos = campPos;
    this.orkCampSeeded = true;
    setChanged();
}

public int getDailyIronProduction() {
    return ImperialCityLevelStats.dailyIronProduction(this.cityLevel);
}

public int getDailyScrapProduction() {
    return ImperialCityLevelStats.dailyScrapProduction(this.cityLevel);
}

public int getDailyCoalProduction() {
    return ImperialCityLevelStats.dailyCoalProduction(this.cityLevel);
}

public int getGuardsmanRecruitIronCost() {
    return switch (this.cityLevel) {
        case 1 -> 12;
        case 2 -> 25;
        case 3 -> 60;
        case 4 -> 120;
        case 5 -> 250;
        default -> 12;
    };
}

public int getGuardsmanRecruitScrapCost() {
    return switch (this.cityLevel) {
        case 1 -> 8;
        case 2 -> 16;
        case 3 -> 40;
        case 4 -> 80;
        case 5 -> 160;
        default -> 8;
    };
}

public int getGuardsmanRecruitCoalCost() {
    return switch (this.cityLevel) {
        case 1 -> 4;
        case 2 -> 8;
        case 3 -> 20;
        case 4 -> 40;
        case 5 -> 80;
        default -> 4;
    };
}

public int getUpgradeIronCost() {
    return ImperialCityLevelStats.upgradeIronCost(this.cityLevel);
}

public int getUpgradeScrapCost() {
    return ImperialCityLevelStats.upgradeScrapCost(this.cityLevel);
}

public int getUpgradeCoalCost() {
    return ImperialCityLevelStats.upgradeCoalCost(this.cityLevel);
}

public int getUpgradePlateCost() {
    return ImperialCityLevelStats.upgradePlateCost(this.cityLevel);
}

@Override
protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);

    tag.putString("BaseName", this.baseName);
    tag.putString("OwnerName", this.ownerName);

    if (this.cityType != null) {
        tag.putString("CityType", this.cityType.name());
    }

    tag.putString("SettlementScale", this.settlementScale.name());

    if (this.ownerUUID != null) {
        tag.putUUID("OwnerUUID", this.ownerUUID);
    }

    tag.putInt("CityLevel", this.cityLevel);

    if (this.governorPersonality != null) {
        tag.putString("GovernorPersonality", this.governorPersonality.name());
    }
    tag.putInt("GovernorNameId", this.governorNameId);
    tag.putBoolean("GovernanceDelegated", this.governanceDelegated);

    this.resources.save(tag);

    tag.putInt("CityMorale", this.cityMorale);
    tag.putInt("PrimarchMourningCooldownTicks", this.primarchMourningCooldownTicks);

    tag.putBoolean("OrkCampSeeded", this.orkCampSeeded);

    if (this.orkCampPos != null) {
        tag.putLong("OrkCampPos", this.orkCampPos.asLong());
    }

    tag.putInt("RecruitedGuardsmen", this.recruitedGuardsmen);

    tag.putLong("LastProductionDay", this.lastProductionDay);

    tag.putLong("LastOrkRaidDay", this.lastOrkRaidDay);
    tag.putInt("OrkRaidCount", this.orkRaidCount);

tag.putBoolean("ActiveOrkRaid", this.activeOrkRaid);
tag.putInt("ActiveOrkRaidTicks", this.activeOrkRaidTicks);

tag.putInt("OrkRaidVictories", this.orkRaidVictories);
tag.putInt("ImperialWarSupport", this.imperialWarSupport);

tag.putInt("CityIntegrity", this.cityIntegrity);
tag.putInt("RaidPressureTicks", this.raidPressureTicks);

tag.putInt("ReinforcementCooldownTicks", this.reinforcementCooldownTicks);
tag.putInt("EmperorGeneSeed", this.emperorGeneSeed);
tag.putInt("Food", this.food);
tag.putInt("SpaceMarinePromotionCooldownTicks", this.spaceMarinePromotionCooldownTicks);
tag.putInt("SelectedSpecialistOrdinal", this.selectedSpecialistOrdinal);

if (this.pendingSpaceMarineCandidateUUID != null) {
    tag.putUUID("PendingSpaceMarineCandidateUUID", this.pendingSpaceMarineCandidateUUID);
}
}

@Override
public void load(CompoundTag tag) {
    super.load(tag);

    this.baseName = tag.getString("BaseName");
    this.ownerName = tag.getString("OwnerName");

    this.cityType = tag.contains("CityType") ? ImperialCityType.fromName(tag.getString("CityType")) : null;
    this.settlementScale = SettlementScale.fromName(tag.getString("SettlementScale"));

    if (this.baseName == null || this.baseName.isEmpty()) {
        this.baseName = "Imperial Outpost";
    }

    if (this.ownerName == null || this.ownerName.isEmpty()) {
        this.ownerName = "Unclaimed";
    }

    if (tag.hasUUID("OwnerUUID")) {
        this.ownerUUID = tag.getUUID("OwnerUUID");
    }

    this.cityLevel = tag.getInt("CityLevel");

    if (this.cityLevel <= 0) {
        this.cityLevel = 1;
    }

    if (this.cityLevel > MAX_CITY_LEVEL) {
        this.cityLevel = MAX_CITY_LEVEL;
    }

    this.governorPersonality = tag.contains("GovernorPersonality")
            ? ImperialGovernorPersonality.fromName(tag.getString("GovernorPersonality"))
            : null;
    this.governorNameId = tag.contains("GovernorNameId") ? tag.getInt("GovernorNameId") : -1;
    this.governanceDelegated = tag.getBoolean("GovernanceDelegated");

    this.resources.load(tag);
    this.food = Math.min(tag.getInt("Food"), getFoodCapacity());

    this.cityMorale = tag.contains("CityMorale")
            ? ImperialCityMoraleManager.clamp(tag.getInt("CityMorale"))
            : ImperialCityMoraleManager.DEFAULT_MORALE;

    this.primarchMourningCooldownTicks = Math.max(0, tag.getInt("PrimarchMourningCooldownTicks"));

    this.orkCampSeeded = tag.getBoolean("OrkCampSeeded");
    this.orkCampPos = tag.contains("OrkCampPos") ? BlockPos.of(tag.getLong("OrkCampPos")) : null;

    this.recruitedGuardsmen = tag.getInt("RecruitedGuardsmen");

    if (this.recruitedGuardsmen < 0) {
        this.recruitedGuardsmen = 0;
    }

    this.lastProductionDay = tag.getLong("LastProductionDay");

    this.lastOrkRaidDay = tag.getLong("LastOrkRaidDay");
    this.orkRaidCount = tag.getInt("OrkRaidCount");

    this.activeOrkRaid = tag.getBoolean("ActiveOrkRaid");
this.activeOrkRaidTicks = tag.getInt("ActiveOrkRaidTicks");

this.orkRaidVictories = tag.getInt("OrkRaidVictories");
this.imperialWarSupport = tag.getInt("ImperialWarSupport");

if (this.orkRaidVictories < 0) {
    this.orkRaidVictories = 0;
}

if (this.imperialWarSupport < 0) {
    this.imperialWarSupport = 0;
}
this.cityIntegrity = tag.getInt("CityIntegrity");

if (this.cityIntegrity <= 0) {
    this.cityIntegrity = 100;
}

if (this.cityIntegrity > 100) {
    this.cityIntegrity = 100;
}

this.raidPressureTicks = tag.getInt("RaidPressureTicks");

if (this.raidPressureTicks < 0) {
    this.raidPressureTicks = 0;
}

this.reinforcementCooldownTicks = tag.getInt("ReinforcementCooldownTicks");
this.emperorGeneSeed = tag.getInt("EmperorGeneSeed");

if (this.emperorGeneSeed < 0) {
    this.emperorGeneSeed = 0;
}

if (this.emperorGeneSeed > getEmperorGeneSeedCapacity()) {
    this.emperorGeneSeed = getEmperorGeneSeedCapacity();
}
this.spaceMarinePromotionCooldownTicks = tag.getInt("SpaceMarinePromotionCooldownTicks");

if (this.spaceMarinePromotionCooldownTicks < 0) {
    this.spaceMarinePromotionCooldownTicks = 0;
}

this.selectedSpecialistOrdinal = tag.getInt("SelectedSpecialistOrdinal");

if (!GuardsmanSpecialization.fromOrdinal(this.selectedSpecialistOrdinal).isSpecialist()) {
    this.selectedSpecialistOrdinal = GuardsmanSpecialization.SNIPER.ordinal();
}

if (tag.hasUUID("PendingSpaceMarineCandidateUUID")) {
    this.pendingSpaceMarineCandidateUUID = tag.getUUID("PendingSpaceMarineCandidateUUID");
} else {
    this.pendingSpaceMarineCandidateUUID = null;
}

if (this.reinforcementCooldownTicks < 0) {
    this.reinforcementCooldownTicks = 0;
}

}
public void tryBuildScrapYard(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentScrapYards = ImperialScrapYardManager.countScrapYards(serverLevel, this, 128);

    if (currentScrapYards >= getScrapYardCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.scrap_yard_cap"), true);
        return;
    }

    int ironCost = 15;
    int coalCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_2_ic", ironCost, coalCost), true);
        return;
    }

    boolean built = ImperialScrapYardManager.buildScrapYard(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, 0, coalCost);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_3", this.resources.getIron(), this.resources.getScrapMetal(), this.resources.getCoal()), false);
}

public int getScrapYardCapacity() {
    return Math.max(1, this.cityLevel) + specialtyBonus(ImperialCityType.HIVE);
}

public void tryBuildPromethiumRefinery(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentRefineries = ImperialPromethiumRefineryManager.countRefineries(serverLevel, this, 128);

    if (currentRefineries >= getPromethiumRefineryCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.refinery_cap"), true);
        return;
    }

    int ironCost = 18;
    int scrapCost = 8;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_2_is", ironCost, scrapCost), true);
        return;
    }

    boolean built = ImperialPromethiumRefineryManager.buildRefinery(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, 0);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_3", this.resources.getIron(), this.resources.getScrapMetal(), this.resources.getCoal()), false);
}

public int getBarracksCapacity() {
    return Math.max(1, this.cityLevel) + specialtyBonus(ImperialCityType.FORTRESS);
}

public void tryBuildBarracks(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentBarracks = ImperialBarracksManager.countBarracks(serverLevel, this, 128);

    if (currentBarracks >= getBarracksCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.barracks_cap"), true);
        return;
    }

    int ironCost = 25;
    int scrapCost = 15;
    int coalCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_3", ironCost, scrapCost, coalCost), true);
        return;
    }

    boolean built = ImperialBarracksManager.buildBarracks(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_3", this.resources.getIron(), this.resources.getScrapMetal(), this.resources.getCoal()), false);
}

public void tryBuildImperialForge(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.not_owner"), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentForges = ImperialForgeManager.countForges(serverLevel, this, 128);

    if (currentForges >= getImperialForgeCapacity()) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.forge_cap"), true);
        return;
    }

    int ironCost = 30;
    int scrapCost = 20;
    int coalCost = 10;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.translatable("msg.firstcrusade.build.need_3", ironCost, scrapCost, coalCost), true);
        return;
    }

    boolean built = ImperialForgeManager.buildForge(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.translatable("msg.firstcrusade.build.res_3", this.resources.getIron(), this.resources.getScrapMetal(), this.resources.getCoal()), false);
}

public int getImperialForgeCapacity() {
    return Math.max(1, (this.cityLevel + 1) / 2) + specialtyBonus(ImperialCityType.FORGE);
}

public boolean consumeResourcesForCrusadiumPlateProduction(int ironCost, int scrapCost, int coalCost) {
    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        return false;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();
    return true;
}
}