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

// Whether this Core has already been stood down from the old city builder. An existing save runs
// the migration once (cancel projects, clear guard posts, dismiss the workforce) and never again.
private boolean simplifiedBaseMigrated = false;

// When the base may next look at its garrison. A deadline in game time, not a countdown: an
// unloaded chunk cannot fall behind one, and a base at full strength costs one comparison a minute.
private long garrisonCheckReadyAt = 0L;

/** True once this Core has been converted from a city to a simplified base. */
public boolean isSimplifiedBaseMigrated() {
    return this.simplifiedBaseMigrated;
}

public void markSimplifiedBaseMigrated() {
    this.simplifiedBaseMigrated = true;
    setChanged();
}

public long getGarrisonCheckReadyAt() {
    return this.garrisonCheckReadyAt;
}

public void setGarrisonCheckReadyAt(long gameTime) {
    this.garrisonCheckReadyAt = gameTime;
    setChanged();
}

/**
 * The stock reply to any request the city builder used to serve.
 *
 * <p>Hiding the buttons is not enough: an old client, or a hand-written packet, can still ask. The
 * server refuses here, so the removed system cannot be reached from outside at all.
 */
private void refuseRemovedConstruction(Player player) {
    player.displayClientMessage(
            Component.translatable("msg.firstcrusade.build.system_removed"), true);
}

public void tryBuildImperialMine(Player player) {
    refuseRemovedConstruction(player);
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
    refuseRemovedConstruction(player);
}

public int getGoldMineCapacity() {
    if (this.cityLevel < 2) {
        return 0;
    }

    return Math.max(1, this.cityLevel / 2);
}

// Farms feed the city: a staffed Farm raises morale and sustains population growth.
public void tryBuildImperialFarm(Player player) {
    refuseRemovedConstruction(player);
}

public int getFarmCapacity() {
    return Math.max(1, this.cityLevel) + specialtyBonus(ImperialCityType.AGRI);
}

// Trade Depots unlock at city level 3 and trade Gold for Emerald with the capital.
public void tryBuildEmeraldTradeDepot(Player player) {
    refuseRemovedConstruction(player);
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

        // Citizen growth belongs to the old city builder: a simplified base has soldiers, not a
        // workforce, so nobody is born here any more (see SimpleImperialBaseManager).

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
WorldWarMapData warMap = WorldWarMapData.get(serverLevel);
warMap.recordCity(serverLevel, blockEntity.worldPosition);
// Publish the territorial attributes the flora decorator resolves palettes from. Writing the
// same values twice is free and does not mark the map dirty (see WorldWarMapData.recordCityInfo).
warMap.recordCityInfo(
        serverLevel,
        blockEntity.worldPosition,
        blockEntity.getCityType(),
        blockEntity.getSettlementScale(),
        blockEntity.cityLevel,
        blockEntity.getBuildBorderRadius());
// Morale easing, patrol rings and workforce management were the old city's three per-city scans.
// A simplified base has no citizens to be moody, no ring to march and no jobs to fill: the only
// recurring work left is keeping the small garrison alive, and that looks at itself once a minute.
SimpleImperialBaseManager.tickBase(serverLevel, blockEntity);
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
// Autonomous governance — the Governor recruiting, upgrading and launching offensives on its own —
// is gone with the city builder. A base does not decide anything; it holds its garrison and waits.
// The Imperium pushing corruption back is now the flora system's job: retaking ground marks the
// chunks and the decorator replaces Ork growth with the Imperial palette. Nothing to scrub here.
    }

    // Raises a single garrison soldier: the base's themed troop, or a Guardsman. No guard post is
    // handed out — soldiers are bound to the Core's home ring and left loose (SimpleImperialBaseManager).
    boolean spawnGarrisonTroop(ServerLevel serverLevel, GuardsmanRank rank) {
        BlockPos spawnPos = SimpleImperialBaseManager.findStandingSpot(serverLevel, this.worldPosition);

        // The regiment fills the slot, not the city type. A Catachan base is mostly Jungle Fighters
        // and a Forge auxilia is a line with a few Skitarii in it — rolled per soldier, so a garrison
        // is a mix rather than eleven copies of one unit. It never changes how many soldiers there
        // are: the caller already checked the cap before getting here.
        com.example.examplemod.crusade.ImperialRegimentType regiment =
                com.example.examplemod.crusade.ImperialCrusadeData.get(serverLevel)
                        .roster(this.worldPosition)
                        .regiment();

        EntityType<? extends AbstractImperialTroopEntity> themedType;

        if (regiment == com.example.examplemod.crusade.ImperialRegimentType.CRUSADE_GENERIC) {
            // The regiment with no character of its own is where the older city-type theme still
            // lives, so a Shrine or Feudal base founded before regiments keeps its own troops.
            themedType = getThemedTroopType(getCityType());
        } else {
            // A named regiment is authoritative: a null roll means a plain Guardsman, and letting
            // the city theme answer instead would quietly turn every Catachan roll into a Jungle
            // Fighter and erase the mix the roll exists to produce.
            themedType = regiment.rollTroop(serverLevel.getRandom());
        }

        if (themedType != null) {
            return spawnThemedTroopAt(serverLevel, themedType,
                    spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        }

        GuardsmanEntity guardsman = FCRegistry.GUARDSMAN.get().create(serverLevel);
        if (guardsman == null) {
            return false;
        }

        guardsman.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        guardsman.assignRandomChapter();
        guardsman.initializeFromCity(rank, getCityType());
        SimpleImperialBaseManager.bindToBase(guardsman, this.worldPosition);

        serverLevel.addFreshEntity(guardsman);
        return true;
    }

    /** One replacement soldier, at the base's current rank. Called by the once-a-minute check. */
    public boolean raiseGarrisonSoldier(ServerLevel serverLevel) {
        if (this.recruitedGuardsmen >= getMilitaryCapacity()) {
            return false;
        }

        if (!spawnGarrisonTroop(serverLevel, getReinforcementRank())) {
            return false;
        }

        this.recruitedGuardsmen++;
        setChanged();
        return true;
    }

    /**
     * Raises the tally to match a sweep that found more soldiers than the counter knew about.
     *
     * <h2>It only ever raises, and that is the whole point</h2>
     *
     * A sweep of the ring around the Core cannot see a soldier that is chasing an Ork over the hill
     * or away on a raid, so "the sweep found fewer than the counter" does not mean soldiers are
     * missing — it means some are out. Letting the sweep <i>lower</i> the counter is what made a
     * base recruit forever: every soldier that strayed past the scan radius looked like a casualty,
     * the tally dropped, a replacement was raised, and the garrison doubled. Deaths already
     * decrement the counter exactly once, in {@link #onAssignedGuardsmanDeath}, which is the only
     * place that knows a soldier is actually gone.
     */
    public void raiseGarrisonCountTo(int actual) {
        if (actual <= this.recruitedGuardsmen) {
            return;
        }

        this.recruitedGuardsmen = actual;
        setChanged();
    }

    /**
     * Sets the tally outright. Migration only.
     *
     * <p>The one moment an exact recount is honest is the conversion of an old city, before any
     * expedition exists to hide a soldier from the sweep — and it is needed, because a city's tally
     * counted citizens and recruits in training alongside actual soldiers.
     */
    public void setGarrisonCount(int actual) {
        this.recruitedGuardsmen = Math.max(0, actual);
        setChanged();
    }

    // Garrisons a freshly founded base so it can defend itself from the first night.
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
        farthest = Math.max(farthest, farthestSettlement(map.getCities(serverLevel)));
        farthest = Math.max(farthest, farthestSettlement(map.getCamps(serverLevel)));
        this.statMapRange = Math.min(WAR_MAP_MAX_RANGE, farthest);

        // Second pass: plot the blips (imperial cities, then ork camps).
        for (long packed : map.getCities(serverLevel)) {
            if (packed == this.worldPosition.asLong()) {
                continue; // this city is the centre marker, drawn separately
            }
            BlockPos pos = BlockPos.of(packed);
            addBlip(pos.getX() - this.worldPosition.getX(), pos.getZ() - this.worldPosition.getZ(), 1);
        }

        for (long packed : map.getCamps(serverLevel)) {
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
            double corruptionMultiplier = OrkSporeManager.productionMultiplier(serverLevel, this.worldPosition, getTerritoryRadius());
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

    guardsman.assignRandomChapter();
    guardsman.initializeFromCity(getStartingGuardsmanRank(), getCityType());
    SimpleImperialBaseManager.bindToBase(guardsman, this.worldPosition);

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
    SimpleImperialBaseManager.bindToBase(troop, this.worldPosition);

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

    // Guard posts are gone. A base's soldiers are bound to a home ring around the Core and left
    // loose; findGuardPostPosition/prepareGuardPost/reorganizeExistingGuardsmen wrote blocks on the
    // walls and reassigned every trooper on every level-up, and both were only ever there to serve
    // the city builder. Binding is now one call to SimpleImperialBaseManager.bindToBase.

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

            guardsman.moveTo(
                    spawnPos.getX() + 0.5D,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5D,
                    player.getYRot(),
                    0.0F
            );

            guardsman.assignRandomChapter();
            guardsman.initializeFromCity(getReinforcementRank(), getCityType());
            SimpleImperialBaseManager.bindToBase(guardsman, this.worldPosition);

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

    // An upgrade is now purely abstract: capacity, storage, production and the strategic Age move,
    // and not one block is placed. Nothing is built, no wall grows and no soldier is reassigned.
    if (this.level instanceof ServerLevel serverLevel) {
        applyStrategicAgeForLevel(serverLevel);
    }

    setChanged();

    if (this.level != null) {
        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
    }

    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.done", this.cityLevel), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.abstract"), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_era",
            SimpleImperialBaseBalance.ageForCoreLevel(this.cityLevel).getDisplayName()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_storage", getStorageCapacity()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_production", getDailyIronProduction(), getDailyScrapProduction(), getDailyCoalProduction()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_military", getMilitaryCapacity()), false);
    player.displayClientMessage(Component.translatable("msg.firstcrusade.upgrade.new_rank", getStartingGuardsmanRank().getDisplayName()), false);
}

/**
 * Founds a world-generated Imperial base: a Core, one small pad of ground, four soldiers.
 *
 * <p>This used to raise a whole walled town — curtain wall, corner towers, a housing district, a
 * central spire — and settle eighteen citizens and eight troopers inside it, and then keep growing
 * it forever. A base is now the Imperial mirror of an Ork camp, so the founding is a single call
 * that writes a 9x9 foundation and never places another block.
 *
 * <p>Called once by {@link WorldSettlementSeeder} right after the Core block appears.
 */
public void buildAutonomousVillage(ServerLevel serverLevel) {
    if (this.cityType == null) {
        assignCityType(serverLevel);
    }

    // The settlement scale still sets the base's abstract level (and so its garrison size), it just
    // no longer maps to a wall radius, because there is no wall.
    this.cityLevel = Math.max(this.cityLevel, this.settlementScale.getStartLevel());

    // Register on the war map FIRST: the strategic sync prunes any settlement record whose city
    // isn't on the map, which would drop the record the Age is written onto below.
    SimpleImperialBaseManager.foundBase(serverLevel, this);
    applyStrategicAgeForLevel(serverLevel);

    // A brand-new base always starts at four, whatever its level would allow it to hold.
    spawnInitialGarrison(serverLevel, SimpleImperialBaseBalance.FOUNDING_GARRISON);

    // Founding counts as the migration: nothing legacy exists here to stand down.
    this.simplifiedBaseMigrated = true;
    setChanged();
}

/**
 * Writes the strategic Age this Core's level stands for straight onto its settlement record.
 *
 * <p>The Age used to be bought by an AI running every 100 ticks off a bank of strategic resources
 * nobody could see. Making it a function of the Core level means upgrading the Core is the visible
 * act that opens the next tier of Astartes surgery — and it costs one map write, not a tick.
 */
private void applyStrategicAgeForLevel(ServerLevel serverLevel) {
    WorldWarMapData.get(serverLevel).recordCity(serverLevel, this.worldPosition);

    StrategicWarAIData warData = StrategicWarAIData.get(serverLevel);
    StrategicSettlementRecord record = warData.getOrCreateImperial(serverLevel, this.worldPosition);

    if (record.setAgeAtLeast(SimpleImperialBaseBalance.ageForCoreLevel(this.cityLevel))) {
        warData.setDirty();
    }
}

// The physical city builder is gone: buildCityStructure and its seventeen helpers (foundation,
// curtain wall, corner towers, central keep, housing district, roads, spire, beds, lamp posts)
// placed thousands of blocks every time a city levelled up. A base's whole physical presence is
// now the 9x9 pad written once at founding by SimpleImperialBaseManager, and nothing rebuilds it.

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

// getCityStructureRadius / getCityWallHeight went with the builder they served: there is no wall
// to be tall and no footprint to be wide. Territory reach is getTerritoryRadius; the garrison's
// home ring is SimpleImperialBaseBalance.HOME_RADIUS.

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
    refuseRemovedConstruction(player);
}

// Places a structure the owner picked with the Builder Tool at a chosen position: validates
// ownership, city level, the build border, a clear+solid footprint and the resource cost, then
// builds/binds/staffs it through the same manager path the Core's auto-build uses.
public void placeStructureWithTool(Player player, CityStructureType type, BlockPos pos) {
    refuseRemovedConstruction(player);
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

/**
 * Credits War Support earned outside this city's own raids — a completed campaign operation, most
 * of all.
 *
 * <p>War Support was previously written only from inside this class, by the raid-victory path, so
 * there was no way for the wider war to pay a city for anything. It uses the same ceiling that path
 * does, so a reward cannot overflow the counter however many operations are finished.
 *
 * @return how much was actually credited, which is less than asked for at the cap
 */
public int addWarSupport(int amount) {
    if (amount <= 0) {
        return 0;
    }

    int before = this.imperialWarSupport;
    this.imperialWarSupport = Math.min(999999, this.imperialWarSupport + amount);

    int credited = this.imperialWarSupport - before;

    if (credited > 0) {
        setChanged();
    }

    return credited;
}

/**
 * Spends War Support on something outside this city's own menu — a War Table order.
 *
 * <p>Checks and deducts in one call on purpose. The alternative, reading the value and subtracting
 * it separately, leaves a window in which the amount can be spent twice, and the whole point of the
 * War Table's order path is that the server decides the payment.
 *
 * @return true when the city could afford it and has been charged
 */
public boolean spendWarSupport(int amount) {
    if (amount <= 0) {
        return true;
    }

    if (this.imperialWarSupport < amount) {
        return false;
    }

    this.imperialWarSupport -= amount;
    setChanged();

    return true;
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

/**
 * How many soldiers this base may hold.
 *
 * <p>Four at level 1, twelve at level 5 — a garrison, not the hundred-strong army the old city
 * table allowed. The number lives in {@link SimpleImperialBaseBalance} because it is the only
 * military figure a simple base has.
 */
public int getMilitaryCapacity() {
    int cap = SimpleImperialBaseBalance.garrisonCapacity(this.cityLevel);
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

tag.putBoolean("SimplifiedBaseMigrated", this.simplifiedBaseMigrated);
tag.putLong("GarrisonCheckReadyAt", this.garrisonCheckReadyAt);

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

// Absent in every save written before the base was simplified — which is exactly the set of saves
// that still need the migration, so the default of false is the correct answer.
this.simplifiedBaseMigrated = tag.getBoolean("SimplifiedBaseMigrated");
this.garrisonCheckReadyAt = tag.getLong("GarrisonCheckReadyAt");

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
    refuseRemovedConstruction(player);
}

public int getScrapYardCapacity() {
    return Math.max(1, this.cityLevel) + specialtyBonus(ImperialCityType.HIVE);
}

public void tryBuildPromethiumRefinery(Player player) {
    refuseRemovedConstruction(player);
}

public int getBarracksCapacity() {
    return Math.max(1, this.cityLevel) + specialtyBonus(ImperialCityType.FORTRESS);
}

public void tryBuildBarracks(Player player) {
    refuseRemovedConstruction(player);
}

public void tryBuildImperialForge(Player player) {
    refuseRemovedConstruction(player);
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