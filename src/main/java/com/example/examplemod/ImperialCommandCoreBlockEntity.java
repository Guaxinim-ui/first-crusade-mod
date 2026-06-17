package com.example.examplemod;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

    private int cityLevel = 1;

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
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentMines = ImperialWorkSiteManager.countImperialMines(serverLevel, this, 128);

    if (currentMines >= getImperialMineCapacity()) {
        player.displayClientMessage(Component.literal(
                "Imperial Mine capacity reached. Upgrade the city to build more mines."
        ), true);
        return;
    }

    int ironCost = 20;
    int scrapCost = 10;
    int coalCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + scrapCost + " Scrap, "
                        + coalCost + " Coal."
        ), true);
        return;
    }

    boolean built = ImperialWorkSiteManager.buildImperialMine(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getCoal() + " Coal."
    ), false);
}

public int getImperialMineCapacity() {
    return Math.max(1, this.cityLevel);
}

// Gold mining unlocks at city level 2 and stays scarce: capacity grows slowly with the city.
public void tryBuildImperialGoldMine(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    if (this.cityLevel < 2) {
        player.displayClientMessage(Component.literal(
                "Gold Mines require an Imperial settlement of Level 2 or higher."
        ), true);
        return;
    }

    int currentGoldMines = ImperialGoldMineManager.countGoldMines(serverLevel, this, 128);

    if (currentGoldMines >= getGoldMineCapacity()) {
        player.displayClientMessage(Component.literal(
                "Gold Mine capacity reached. Upgrade the city to build more gold mines."
        ), true);
        return;
    }

    int ironCost = 40;
    int scrapCost = 25;
    int coalCost = 15;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + scrapCost + " Scrap, "
                        + coalCost + " Coal."
        ), true);
        return;
    }

    boolean built = ImperialGoldMineManager.buildImperialGoldMine(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getCoal() + " Coal."
    ), false);
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
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentFarms = ImperialFarmManager.countFarms(serverLevel, this, 128);

    if (currentFarms >= getFarmCapacity()) {
        player.displayClientMessage(Component.literal(
                "Farm capacity reached. Upgrade the city to build more farms."
        ), true);
        return;
    }

    int ironCost = 15;
    int scrapCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + scrapCost + " Scrap."
        ), true);
        return;
    }

    boolean built = ImperialFarmManager.buildImperialFarm(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, 0);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap."
    ), false);
}

public int getFarmCapacity() {
    return Math.max(1, this.cityLevel);
}

// Trade Depots unlock at city level 3 and trade Gold for Emerald with the capital.
public void tryBuildEmeraldTradeDepot(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    if (this.cityLevel < 3) {
        player.displayClientMessage(Component.literal(
                "Emerald Trade Depots require an Imperial settlement of Level 3 or higher."
        ), true);
        return;
    }

    int currentDepots = ImperialEmeraldTradeDepotManager.countTradeDepots(serverLevel, this, 128);

    if (currentDepots >= getTradeDepotCapacity()) {
        player.displayClientMessage(Component.literal(
                "Emerald Trade Depot capacity reached. Upgrade the city to build more."
        ), true);
        return;
    }

    int ironCost = 30;
    int scrapCost = 15;
    int coalCost = 10;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + scrapCost + " Scrap, "
                        + coalCost + " Coal."
        ), true);
        return;
    }

    boolean built = ImperialEmeraldTradeDepotManager.buildTradeDepot(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getCoal() + " Coal."
    ), false);
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
        super(ExampleMod.IMPERIAL_COMMAND_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImperialCommandCoreBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (blockEntity.cityType == null) {
            blockEntity.assignCityType(serverLevel.random);
        }

        ImperialPopulationManager.tickCitizenGrowth(serverLevel, blockEntity);

        // Refresh the (expensive) structure/citizen/threat counts only a few times per second
        // instead of every tick. The Core GUI reads these cached values, so opening it no longer
        // forces a full block/entity scan on every server tick (which caused multi-second freezes).
        if (serverLevel.getGameTime() % 40L == 0L) {
            blockEntity.recomputeMenuStats(serverLevel);
        }

        blockEntity.tickCounter++;

        if (blockEntity.tickCounter < 200) {
            return;
        }

       blockEntity.tickCounter = 0;
ImperialCityMoraleManager.tickMorale(serverLevel, blockEntity);
ImperialPatrolManager.tickPatrols(serverLevel, blockEntity);
ImperialWorkforceManager.autoManageWorkforce(serverLevel, blockEntity);
blockEntity.produceResourcesIfNewDay(level);
blockEntity.reduceReinforcementCooldown();
blockEntity.reduceSpaceMarinePromotionCooldown();
blockEntity.processAutomaticSpaceMarinePromotion(serverLevel);
ImperialCustodesManager.tickCustodes(serverLevel, blockEntity);
blockEntity.reducePrimarchMourningCooldown();
ImperialPrimarchManager.tickPrimarch(serverLevel, blockEntity);
WaaaghOverlordManager.contributeFromCity(serverLevel, blockEntity);
blockEntity.trySeedOrkCamp(serverLevel);
blockEntity.trySpawnOrkRaid(serverLevel);
blockEntity.checkActiveOrkRaid(serverLevel);
    }

    // Recomputes the cached counts read by the Core GUI. Called a few times per second from
    // serverTick, so the menu never triggers these block/entity scans on its own polling.
    private void recomputeMenuStats(ServerLevel serverLevel) {
        this.statMineCount = ImperialWorkSiteManager.countImperialMines(serverLevel, this, STATS_SCAN_RADIUS);
        this.statGoldMineCount = ImperialGoldMineManager.countGoldMines(serverLevel, this, STATS_SCAN_RADIUS);
        this.statScrapYardCount = ImperialScrapYardManager.countScrapYards(serverLevel, this, STATS_SCAN_RADIUS);
        this.statForgeCount = ImperialForgeManager.countForges(serverLevel, this, STATS_SCAN_RADIUS);
        this.statRefineryCount = ImperialPromethiumRefineryManager.countRefineries(serverLevel, this, STATS_SCAN_RADIUS);
        this.statFarmCount = ImperialFarmManager.countFarms(serverLevel, this, STATS_SCAN_RADIUS);
        this.statTradeDepotCount = ImperialEmeraldTradeDepotManager.countTradeDepots(serverLevel, this, STATS_SCAN_RADIUS);
        this.statBarracksCount = ImperialBarracksManager.countBarracks(serverLevel, this, STATS_SCAN_RADIUS);

        this.statCitizenCount = ImperialPopulationManager.countAssignedCitizens(serverLevel, this);
        this.statUnemployedCount = ImperialPopulationManager.countUnemployedCitizens(serverLevel, this);
        this.statMinerCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.MINER);
        this.statGoldMinerCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.GOLD_MINER);
        this.statScrapperCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.SCRAPPER);
        this.statSmithCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.SMITH);
        this.statStokerCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.STOKER);
        this.statFarmerCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.FARMER);
        this.statTraderCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.TRADER);
        this.statRecruitCount = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.RECRUIT);

        this.statThreatScore = getLiveThreatScore();
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
            player.displayClientMessage(Component.literal("Only the owner can deposit resources into this city."), true);
            return;
        }

        int acceptedAmount = addIron(itemStack.getCount());

        if (acceptedAmount <= 0) {
            player.displayClientMessage(Component.literal("Iron warehouse is full."), true);
            return;
        }

        itemStack.shrink(acceptedAmount);
        setChanged();

        player.displayClientMessage(Component.literal("Deposited " + acceptedAmount + " Iron."), false);
        player.displayClientMessage(Component.literal("City Iron: " + this.resources.getIron() + "/" + getStorageCapacity()), false);
    }

    public void depositCoal(Player player, ItemStack itemStack) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.literal("Only the owner can deposit resources into this city."), true);
            return;
        }

        int acceptedAmount = addCoal(itemStack.getCount());

        if (acceptedAmount <= 0) {
            player.displayClientMessage(Component.literal("Coal warehouse is full."), true);
            return;
        }

        itemStack.shrink(acceptedAmount);
        setChanged();

        player.displayClientMessage(Component.literal("Deposited " + acceptedAmount + " Coal."), false);
        player.displayClientMessage(Component.literal("City Coal: " + this.resources.getCoal() + "/" + getStorageCapacity()), false);
    }

    public void depositScrapMetal(Player player, ItemStack itemStack) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.literal("Only the owner can deposit resources into this city."), true);
            return;
        }

        int acceptedAmount = addScrapMetal(itemStack.getCount());

        if (acceptedAmount <= 0) {
            player.displayClientMessage(Component.literal("Scrap Metal warehouse is full."), true);
            return;
        }

        itemStack.shrink(acceptedAmount);
        setChanged();

        player.displayClientMessage(Component.literal("Deposited " + acceptedAmount + " Scrap Metal."), false);
        player.displayClientMessage(Component.literal("City Scrap Metal: " + this.resources.getScrapMetal() + "/" + getStorageCapacity()), false);
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
        player.displayClientMessage(Component.literal("Only the owner can deposit resources into this city."), true);
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

        if (stack.is(ExampleMod.SCRAP_METAL.get())) {
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

        if (stack.is(ExampleMod.CRUSADIUM_INGOT.get())) {
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
            player.displayClientMessage(Component.literal("City storage is full. No resources were deposited."), true);
        } else {
            player.displayClientMessage(Component.literal("No depositable city resources found in your inventory."), true);
        }

        return;
    }

    player.getInventory().setChanged();
    setChanged();

    player.displayClientMessage(Component.literal(
            "Deposited: " + totalIron + " Iron, " + totalCoal + " Coal, " + totalScrap + " Scrap, "
                    + totalGold + " Gold, " + totalEmerald + " Emerald, " + totalCrusadium + " Crusadium."
    ), false);

    player.displayClientMessage(Component.literal(
            "City Storage: " + this.resources.getIron() + " Iron, " + this.resources.getCoal() + " Coal, " + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getGold() + " Gold, " + this.resources.getEmerald() + " Emerald, " + this.resources.getCrusadium() + " Crusadium."
    ), false);
}

    // Withdraws stored resources back into the owner's inventory as items (for crafting, etc.).
    public void withdrawResource(Player player, ImperialResourceType type, int requested) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.literal("Only the owner can withdraw resources from this city."), true);
            return;
        }

        int available = this.resources.get(type);

        int amount = Math.min(available, requested);

        if (amount <= 0) {
            player.displayClientMessage(Component.literal("No " + type.getDisplayName() + " stored to withdraw."), true);
            return;
        }

        Item item = switch (type) {
            case IRON -> Items.IRON_INGOT;
            case COAL -> Items.COAL;
            case SCRAP -> ExampleMod.SCRAP_METAL.get();
            case GOLD -> Items.GOLD_INGOT;
            case EMERALD -> Items.EMERALD;
            case CRUSADIUM -> ExampleMod.CRUSADIUM_INGOT.get();
        };

        this.resources.remove(type, amount);

        ItemStack stack = new ItemStack(item, amount);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        player.getInventory().setChanged();
        setChanged();

        player.displayClientMessage(Component.literal(
                "Withdrew " + amount + " " + type.getDisplayName() + " from the city."
        ), false);
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
        player.displayClientMessage(Component.literal("Only the owner can withdraw resources from this city."), true);
        return;
    }

    int amount = Math.min(this.food, requested);

    if (amount <= 0) {
        player.displayClientMessage(Component.literal("No Food stored to withdraw."), true);
        return;
    }

    this.food -= amount;

    ItemStack stack = new ItemStack(Items.WHEAT, amount);

    if (!player.getInventory().add(stack)) {
        player.drop(stack, false);
    }

    player.getInventory().setChanged();
    setChanged();

    player.displayClientMessage(Component.literal(
            "Withdrew " + amount + " Food (Wheat) from the city."
    ), false);
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



public void tryRecruitGuardsman(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can train soldiers in this city."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int recruitsInTraining = ImperialPopulationManager.countCitizensWithJob(serverLevel, this, ImperialCitizenJob.RECRUIT);

    if (this.recruitedGuardsmen + recruitsInTraining >= getMilitaryCapacity()) {
        player.displayClientMessage(Component.literal("Military capacity reached. Upgrade the city to train more soldiers."), true);
        return;
    }

    BlockPos barracksPos = ImperialBarracksManager.findAvailableBarracks(serverLevel, this, 128);

    if (barracksPos == null) {
        int barracksCount = ImperialBarracksManager.countBarracks(serverLevel, this, 128);

        if (barracksCount <= 0) {
            player.displayClientMessage(Component.literal("You need an Imperial Barracks to train soldiers."), true);
        } else {
            player.displayClientMessage(Component.literal("All Barracks are currently training recruits."), true);
        }

        return;
    }

    ImperialCitizenEntity recruit = ImperialPopulationManager.findNearestTrainableCitizen(serverLevel, this, player);

    if (recruit == null) {
        player.displayClientMessage(Component.literal("No trainable Imperial Citizen found near the Command Core."), true);
        return;
    }

    recruit.assignToCommandCore(this.worldPosition);
    recruit.assignJob(ImperialCitizenJob.RECRUIT, barracksPos);

    setChanged();

    player.displayClientMessage(Component.literal("Imperial Citizen assigned to training as Recruit."), false);
    player.displayClientMessage(Component.literal(
            "Recruits in training: " + (recruitsInTraining + 1) + ". Soldiers: " + this.recruitedGuardsmen + "/" + getMilitaryCapacity()
    ), false);
}

public boolean completeRecruitTraining(ServerLevel serverLevel, ImperialCitizenEntity recruit) {
    if (this.recruitedGuardsmen >= getMilitaryCapacity()) {
        return false;
    }

    GuardsmanEntity guardsman = ExampleMod.GUARDSMAN.get().create(serverLevel);

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
    guardsman.initializeFromCity(getStartingGuardsmanRank());

    recruit.discard();
    serverLevel.addFreshEntity(guardsman);

    this.recruitedGuardsmen++;
    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "A Recruit completed training and joined the Guardsmen. Soldiers: " + this.recruitedGuardsmen + "/" + getMilitaryCapacity()
    );

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
        player.displayClientMessage(Component.literal("Only the owner can change the specialist selection."), true);
        return;
    }

    GuardsmanSpecialization next = getSelectedSpecialization().nextSelectable();
    this.selectedSpecialistOrdinal = next.ordinal();
    setChanged();

    player.displayClientMessage(Component.literal("Selected specialist: " + next.getDisplayName() + "."), true);
}

public void promoteSpecialist(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can promote specialists."), true);
        return;
    }

    if (this.cityLevel < 2) {
        player.displayClientMessage(Component.literal("Specialist promotions require an Imperial settlement of Level 2 or higher."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int ironCost = getSpecialistIronCost();
    int scrapCost = getSpecialistScrapCost();
    int warSupportCost = getSpecialistWarSupportCost();

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.literal("Not enough city resources. Need: " + ironCost + " Iron, " + scrapCost + " Scrap."), true);
        return;
    }

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.literal("Not enough Imperial War Support. Need: " + warSupportCost + "."), true);
        return;
    }

    GuardsmanEntity target = findNearestSpecializableGuardsman(serverLevel, player);

    if (target == null) {
        player.displayClientMessage(Component.literal("No non-specialist Guardsman found near the Command Core."), true);
        return;
    }

    GuardsmanSpecialization spec = getSelectedSpecialization();

    target.setSpecialization(spec, true);

    this.resources.spend(ironCost, scrapCost, 0);
    this.imperialWarSupport -= warSupportCost;

    setChanged();

    player.displayClientMessage(Component.literal("Guardsman promoted to " + spec.getDisplayName() + "."), false);
    player.displayClientMessage(Component.literal(
            "Cost: " + ironCost + " Iron, " + scrapCost + " Scrap, " + warSupportCost + " War Support."
    ), false);
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

        this.recruitedGuardsmen = guardsmen.size();
        setChanged();
    }

    public void onAssignedGuardsmanDeath() {
        if (this.recruitedGuardsmen > 0) {
            this.recruitedGuardsmen--;
            setChanged();
        }
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
        player.displayClientMessage(Component.literal("Only the owner can call Imperial reinforcements."), true);
        return;
    }

    if (!this.activeOrkRaid) {
        player.displayClientMessage(Component.literal("Imperial reinforcements can only be called during an active Ork raid."), true);
        return;
    }

    if (this.reinforcementCooldownTicks > 0) {
        player.displayClientMessage(Component.literal("Reinforcements are still on cooldown: " + (this.reinforcementCooldownTicks / 20) + " seconds."), true);
        return;
    }

    if (this.recruitedGuardsmen >= getMilitaryCapacity()) {
        player.displayClientMessage(Component.literal("Military capacity reached. Cannot call more reinforcements."), true);
        return;
    }

    int warSupportCost = getReinforcementWarSupportCost();

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.literal("Not enough Imperial War Support to call reinforcements."), true);
        player.displayClientMessage(Component.literal("Required: " + warSupportCost + " War Support."), false);
        player.displayClientMessage(Component.literal("Current: " + this.imperialWarSupport + " War Support."), false);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int requestedReinforcements = getReinforcementCount();
    int availableSlots = getMilitaryCapacity() - this.recruitedGuardsmen;
    int actualReinforcements = Math.min(requestedReinforcements, availableSlots);

    if (actualReinforcements <= 0) {
        player.displayClientMessage(Component.literal("No military capacity available for reinforcements."), true);
        return;
    }

    int spawned = 0;

    for (int i = 0; i < actualReinforcements; i++) {
        GuardsmanEntity guardsman = ExampleMod.GUARDSMAN.get().create(serverLevel);

        if (guardsman == null) {
            continue;
        }

        BlockPos spawnPos = findSpawnPosition(serverLevel);
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
        guardsman.initializeFromCity(getReinforcementRank());

        serverLevel.addFreshEntity(guardsman);

        this.recruitedGuardsmen++;
        spawned++;
    }

    if (spawned <= 0) {
        player.displayClientMessage(Component.literal("Failed to deploy Imperial reinforcements."), true);
        return;
    }

    this.imperialWarSupport -= warSupportCost;
    this.reinforcementCooldownTicks = getReinforcementCooldownTicks();

    setChanged();

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Imperial reinforcements deployed: " + spawned + " Guardsmen."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "War Support spent: " + warSupportCost + ". Remaining: " + this.imperialWarSupport + "."
    );
}

private int getReinforcementWarSupportCost() {
    return ImperialCityLevelStats.reinforcementWarSupportCost(this.cityLevel);
}

private int getReinforcementCount() {
    return switch (this.cityLevel) {
        case 1 -> 1;
        case 2 -> 2;
        case 3 -> 3;
        case 4 -> 4;
        case 5 -> 6;
        default -> 1;
    };
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
        player.displayClientMessage(Component.literal("Only the owner can rally city defenders."), true);
        return;
    }

    if (!this.activeOrkRaid) {
        player.displayClientMessage(Component.literal("Defender rally can only be used during an active Ork raid."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int affected = ImperialDefenseManager.rallyDefenders(serverLevel, this);

    if (affected <= 0) {
        player.displayClientMessage(Component.literal("No assigned Guardsmen found near this Core."), true);
        return;
    }

    setChanged();

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            "Imperial order issued: defenders rally to the Core!"
    );

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            "Defenders repositioned: " + affected
    );
}

public void fortifyDefenders(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can fortify city defenders."), true);
        return;
    }

    if (!this.activeOrkRaid) {
        player.displayClientMessage(Component.literal("Defender fortification can only be used during an active Ork raid."), true);
        return;
    }

    int warSupportCost = getFortifyDefendersWarSupportCost();

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.literal("Not enough Imperial War Support to fortify defenders."), true);
        player.displayClientMessage(Component.literal("Required: " + warSupportCost + " War Support."), false);
        player.displayClientMessage(Component.literal("Current: " + this.imperialWarSupport + " War Support."), false);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int affected = ImperialDefenseManager.fortifyDefenders(serverLevel, this);

    if (affected <= 0) {
        player.displayClientMessage(Component.literal("No assigned Guardsmen found near this Core."), true);
        return;
    }

    this.imperialWarSupport -= warSupportCost;

    setChanged();

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            "Imperial order issued: defenders fortified!"
    );

    ImperialDefenseManager.notifyDefenseCommand(
            serverLevel,
            this.worldPosition,
            "Fortified Guardsmen: " + affected + ". War Support spent: " + warSupportCost + "."
    );
}

private int getFortifyDefendersWarSupportCost() {
    return ImperialCityLevelStats.fortifyWarSupportCost(this.cityLevel);
}

public void upgradeNearestGuardsmanToSpaceMarine(Player player, ItemStack catalystStack) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can upgrade Guardsmen into Space Marines."), true);
        return;
    }

    if (this.cityLevel < 3) {
        player.displayClientMessage(Component.literal("Space Marine upgrades require an Imperial settlement of Level 3 or higher."), true);
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
        player.displayClientMessage(Component.literal("Missing Iron for Space Marine upgrade: " + (ironCost - this.resources.getIron())), true);
        return;
    }

    if (this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.literal("Missing Scrap Metal for Space Marine upgrade: " + (scrapCost - this.resources.getScrapMetal())), true);
        return;
    }

    if (this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal("Missing Coal for Space Marine upgrade: " + (coalCost - this.resources.getCoal())), true);
        return;
    }

    if (this.imperialWarSupport < warSupportCost) {
        player.displayClientMessage(Component.literal("Missing Imperial War Support for Space Marine upgrade: " + (warSupportCost - this.imperialWarSupport)), true);
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
        player.displayClientMessage(Component.literal("No upgradeable Guardsman found near this Core."), true);
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);
    this.imperialWarSupport -= warSupportCost;

    catalystStack.shrink(1);

    SpaceMarineUpgradeManager.upgradeToSpaceMarine(serverLevel, this, targetGuardsman);

    setChanged();

    player.displayClientMessage(Component.literal("Space Marine upgrade completed."), false);
    player.displayClientMessage(Component.literal("Cost: " + ironCost + " Iron, " + scrapCost + " Scrap, " + coalCost + " Coal, " + warSupportCost + " War Support, 1 Netherite Ingot."), false);
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
            "Space Marine candidate selected: " + candidate.getGuardsmanRank().getDisplayName() + "."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Candidate is moving to the Imperial Command Core for ascension."
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
            "Gene of the Emperor consumed: 1. Remaining: " + this.emperorGeneSeed + "."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Next Space Marine ascension cooldown: " + (this.spaceMarinePromotionCooldownTicks / 20) + " seconds."
    );
}

private int getSpaceMarinePromotionCooldownTicks() {
    return 24000;
}

    public void tryUpgradeCity(Player player, ItemStack plateStack) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can upgrade this city."), true);
        return;
    }

    if (!canUpgradeMore()) {
        player.displayClientMessage(Component.literal("This city has reached the current max level."), true);
        return;
    }

    int ironCost = getUpgradeIronCost();
    int scrapCost = getUpgradeScrapCost();
    int coalCost = getUpgradeCoalCost();
    int plateCost = getUpgradePlateCost();

    if (this.resources.getIron() < ironCost) {
        player.displayClientMessage(Component.literal("Missing Iron: " + (ironCost - this.resources.getIron())), true);
        return;
    }

    if (this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.literal("Missing Scrap Metal: " + (scrapCost - this.resources.getScrapMetal())), true);
        return;
    }

    if (this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal("Missing Coal: " + (coalCost - this.resources.getCoal())), true);
        return;
    }

    if (plateStack.getCount() < plateCost) {
        player.displayClientMessage(Component.literal("Missing Crusadium Plate: " + (plateCost - plateStack.getCount())), true);
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

    player.displayClientMessage(Component.literal("City upgraded to Level " + this.cityLevel + "."), false);
    player.displayClientMessage(Component.literal("City structure expanded."), false);
    player.displayClientMessage(Component.literal("Guardsmen reassigned to defensive posts."), false);
    player.displayClientMessage(Component.literal("New Storage Capacity: " + getStorageCapacity()), false);
    player.displayClientMessage(Component.literal("New Daily Production: +" + getDailyIronProduction() + " Iron, +" + getDailyScrapProduction() + " Scrap, +" + getDailyCoalProduction() + " Coal"), false);
    player.displayClientMessage(Component.literal("New Military Capacity: " + getMilitaryCapacity()), false);
    player.displayClientMessage(Component.literal("New Recruit Rank: " + getStartingGuardsmanRank().getDisplayName()), false);
}

private void buildCityStructure(ServerLevel serverLevel) {
    int radius = getCityStructureRadius();
    int wallHeight = getCityWallHeight();

    buildFoundation(serverLevel, radius);
    buildOuterWall(serverLevel, radius, wallHeight);
    buildCornerTowers(serverLevel, radius, wallHeight);

    if (this.cityLevel >= 3) {
        buildSimpleHouse(serverLevel, this.worldPosition.offset(4, 0, 4), 5, 5, 3);
        buildSimpleHouse(serverLevel, this.worldPosition.offset(-8, 0, 4), 5, 5, 3);
    }

    if (this.cityLevel >= 4) {
        buildSimpleHouse(serverLevel, this.worldPosition.offset(4, 0, -8), 6, 5, 4);
        buildSimpleHouse(serverLevel, this.worldPosition.offset(-10, 0, -8), 6, 5, 4);
    }

    if (this.cityLevel >= 5) {
        buildCentralRoad(serverLevel, radius);
        buildSimpleHouse(serverLevel, this.worldPosition.offset(10, 0, 10), 7, 6, 4);
        buildSimpleHouse(serverLevel, this.worldPosition.offset(-16, 0, 10), 7, 6, 4);
        buildSimpleHouse(serverLevel, this.worldPosition.offset(10, 0, -14), 7, 6, 4);
        buildSimpleHouse(serverLevel, this.worldPosition.offset(-16, 0, -14), 7, 6, 4);
        // A grand gothic cathedral spire rises behind the Core as the city's crowning landmark.
        buildCentralSpire(serverLevel, this.worldPosition.offset(-1, 0, -(radius / 2)), wallHeight + 14);
    }
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
            BlockPos pos = this.worldPosition.offset(x, -1, z);

            if (serverLevel.getBlockState(pos).isAir() || !serverLevel.getBlockState(pos).isCollisionShapeFullBlock(serverLevel, pos)) {
                serverLevel.setBlock(pos, ((x + z) & 1) == 0 ? lightTile : darkTile, 3);
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
                    boolean windowSlot = !isCorner && (y == 1 || y == 2)
                            && (x == width / 2 || z == depth / 2);

                    if (doorway) {
                        continue;
                    }

                    BlockState mat = isCorner ? corner : (windowSlot ? window : wall);
                    safePlace(serverLevel, start.offset(x, y, z), mat);
                }
            }
        }
    }

    // Dark pitched-look roof rim + a gilded finial at the apex.
    for (int x = -1; x <= width; x++) {
        for (int z = -1; z <= depth; z++) {
            safePlace(serverLevel, start.offset(x, height, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        }
    }

    safePlace(serverLevel, start.offset(width / 2, height + 1, depth / 2), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
    safePlace(serverLevel, start.offset(width / 2, height + 2, depth / 2), Blocks.GOLD_BLOCK.defaultBlockState());
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
        player.displayClientMessage(Component.literal("Only the owner can force an Ork raid test."), true);
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
                    "Orks are damaging the Imperial Command Core! Integrity: " + this.cityIntegrity + "/100"
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
                "Ork raid still active. Remaining enemies: " + remainingRaiders + ". Core Integrity: " + this.cityIntegrity + "/100"
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
                "The Ork raid has scattered. Some enemies may still be nearby."
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
            "Ork raid defeated! The Imperial settlement stands victorious."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Victory reward: +" + ironReward + " Iron, +" + scrapReward + " Scrap, +" + coalReward + " Coal, +" + warSupportReward + " War Support."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Core repaired by victory momentum: +" + integrityRepairReward + " Integrity."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Total Ork Raid Victories: " + this.orkRaidVictories
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
            "The Imperial Command Core was overrun! The settlement has suffered heavy damage."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Lost resources: -" + lostIron + " Iron, -" + lostScrap + " Scrap, -" + lostCoal + " Coal."
    );

    OrkRaidManager.notifyNearbyPlayers(
            serverLevel,
            this.worldPosition,
            "Core Integrity restored to emergency level: 25/100."
    );
}

public void repairCity(Player player, ItemStack plateStack) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can repair this city."), true);
        return;
    }

    if (this.cityIntegrity >= 100) {
        player.displayClientMessage(Component.literal("The Imperial Command Core is already fully repaired."), true);
        return;
    }

    if (plateStack.isEmpty()) {
        return;
    }

    int repairAmount = getManualRepairAmount();

    plateStack.shrink(1);
    this.cityIntegrity = Math.min(100, this.cityIntegrity + repairAmount);

    setChanged();

    player.displayClientMessage(Component.literal("Used 1 Crusadium Plate to repair the Core."), false);
    player.displayClientMessage(Component.literal("Core Integrity: " + this.cityIntegrity + "/100"), false);
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
                    "There is already an active Ork raid. Defeat the current enemies first."
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
        case 1 -> 4;
        case 2 -> 3;
        case 3 -> 3;
        case 4 -> 2;
        case 5 -> 2;
        default -> 4;
    };
}

private float getRaidChance() {
    return switch (this.cityLevel) {
        case 1 -> 0.20F;
        case 2 -> 0.28F;
        case 3 -> 0.36F;
        case 4 -> 0.45F;
        case 5 -> 0.55F;
        default -> 0.20F;
    };
}

private int getCityStructureRadius() {
    return switch (this.cityLevel) {
        case 1 -> 4;
        case 2 -> 8;
        case 3 -> 12;
        case 4 -> 18;
        case 5 -> 26;
        default -> 4;
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
        player.displayClientMessage(Component.literal("Only the owner can access the Imperial Command Core interface."), true);
        return;
    }

    NetworkHooks.openScreen(
            serverPlayer,
            new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("Imperial Command Core");
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

public int getCityTypeOrdinal() {
    return getCityType().ordinal();
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

private void assignCityType(net.minecraft.util.RandomSource random) {
    this.cityType = ImperialCityType.random(random);

    // Only rename a still-default outpost so player-claimed names are kept.
    if (this.baseName == null || this.baseName.isEmpty() || this.baseName.equals("Imperial Outpost")) {
        this.baseName = this.cityType.getDisplayName();
    }

    setChanged();
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
    return ImperialCityLevelStats.militaryCapacity(this.cityLevel);
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

    if (this.ownerUUID != null) {
        tag.putUUID("OwnerUUID", this.ownerUUID);
    }

    tag.putInt("CityLevel", this.cityLevel);

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
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentScrapYards = ImperialScrapYardManager.countScrapYards(serverLevel, this, 128);

    if (currentScrapYards >= getScrapYardCapacity()) {
        player.displayClientMessage(Component.literal(
                "Scrap Yard capacity reached. Upgrade the city to build more Scrap Yards."
        ), true);
        return;
    }

    int ironCost = 15;
    int coalCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + coalCost + " Coal."
        ), true);
        return;
    }

    boolean built = ImperialScrapYardManager.buildScrapYard(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, 0, coalCost);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getCoal() + " Coal."
    ), false);
}

public int getScrapYardCapacity() {
    return Math.max(1, this.cityLevel);
}

public void tryBuildPromethiumRefinery(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentRefineries = ImperialPromethiumRefineryManager.countRefineries(serverLevel, this, 128);

    if (currentRefineries >= getPromethiumRefineryCapacity()) {
        player.displayClientMessage(Component.literal(
                "Promethium Refinery capacity reached. Upgrade the city to build more refineries."
        ), true);
        return;
    }

    int ironCost = 18;
    int scrapCost = 8;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + scrapCost + " Scrap."
        ), true);
        return;
    }

    boolean built = ImperialPromethiumRefineryManager.buildRefinery(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, 0);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getCoal() + " Coal."
    ), false);
}

public int getBarracksCapacity() {
    return Math.max(1, this.cityLevel);
}

public void tryBuildBarracks(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentBarracks = ImperialBarracksManager.countBarracks(serverLevel, this, 128);

    if (currentBarracks >= getBarracksCapacity()) {
        player.displayClientMessage(Component.literal(
                "Barracks capacity reached. Upgrade the city to build more barracks."
        ), true);
        return;
    }

    int ironCost = 25;
    int scrapCost = 15;
    int coalCost = 5;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + scrapCost + " Scrap, "
                        + coalCost + " Coal."
        ), true);
        return;
    }

    boolean built = ImperialBarracksManager.buildBarracks(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getCoal() + " Coal."
    ), false);
}

public void tryBuildImperialForge(Player player) {
    if (!isOwner(player)) {
        player.displayClientMessage(Component.literal("Only the owner can build city work sites."), true);
        return;
    }

    if (!(this.level instanceof ServerLevel serverLevel)) {
        return;
    }

    int currentForges = ImperialForgeManager.countForges(serverLevel, this, 128);

    if (currentForges >= getImperialForgeCapacity()) {
        player.displayClientMessage(Component.literal(
                "Imperial Forge capacity reached. Upgrade the city to build more forges."
        ), true);
        return;
    }

    int ironCost = 30;
    int scrapCost = 20;
    int coalCost = 10;

    if (this.resources.getIron() < ironCost || this.resources.getScrapMetal() < scrapCost || this.resources.getCoal() < coalCost) {
        player.displayClientMessage(Component.literal(
                "Not enough city resources. Need: "
                        + ironCost + " Iron, "
                        + scrapCost + " Scrap, "
                        + coalCost + " Coal."
        ), true);
        return;
    }

    boolean built = ImperialForgeManager.buildForge(serverLevel, this, player);

    if (!built) {
        return;
    }

    this.resources.spend(ironCost, scrapCost, coalCost);

    setChanged();

    player.displayClientMessage(Component.literal(
            "City Resources: "
                    + this.resources.getIron() + " Iron, "
                    + this.resources.getScrapMetal() + " Scrap, "
                    + this.resources.getCoal() + " Coal."
    ), false);
}

public int getImperialForgeCapacity() {
    return Math.max(1, (this.cityLevel + 1) / 2);
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