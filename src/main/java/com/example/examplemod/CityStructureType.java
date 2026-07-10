package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The catalogue of city structures the {@link CityBuilderToolItem} can place. Each entry bundles
 * everything the tool and Core need for a structure: display name, required city level, resource
 * cost (iron/scrap/coal — the same currency the Core spends), footprint size for the ghost + the
 * area check, and the manager call that actually builds/binds/staffs it at a chosen position.
 *
 * The placer references the {@code buildAt} methods added to each work-site manager, so the tool
 * reuses the exact same structures, worker assignment and Core binding as the Core's auto-build.
 */
public enum CityStructureType {
    MINE("gui.firstcrusade.structure.mine", 1, 8, 0, 0, 2, 4,
            ImperialWorkSiteManager::buildImperialMineAt),
    GOLD_MINE("gui.firstcrusade.structure.gold_mine", 2, 12, 0, 0, 2, 4,
            ImperialGoldMineManager::buildGoldMineAt),
    SCRAP_YARD("gui.firstcrusade.structure.scrap_yard", 1, 15, 0, 5, 2, 4,
            ImperialScrapYardManager::buildScrapYardAt),
    FORGE("gui.firstcrusade.structure.forge", 1, 20, 8, 4, 2, 4,
            ImperialForgeManager::buildForgeAt),
    REFINERY("gui.firstcrusade.structure.refinery", 2, 18, 8, 0, 2, 4,
            ImperialPromethiumRefineryManager::buildRefineryAt),
    FARM("gui.firstcrusade.structure.farm", 1, 10, 0, 0, 2, 4,
            ImperialFarmManager::buildFarmAt),
    TRADE_DEPOT("gui.firstcrusade.structure.trade_depot", 3, 12, 4, 0, 2, 4,
            ImperialEmeraldTradeDepotManager::buildDepotAt),
    BARRACKS("gui.firstcrusade.structure.barracks", 1, 25, 15, 5, 3, 5,
            ImperialBarracksManager::buildBarracksAt);

    @FunctionalInterface
    public interface Placer {
        void place(ServerLevel level, ImperialCommandCoreBlockEntity core, Player player, BlockPos pos);
    }

    private final String nameKey;
    private final int requiredLevel;
    private final int ironCost;
    private final int scrapCost;
    private final int coalCost;
    private final int footprintRadius;
    private final int footprintHeight;
    private final Placer placer;

    CityStructureType(String nameKey, int requiredLevel, int ironCost, int scrapCost, int coalCost,
                      int footprintRadius, int footprintHeight, Placer placer) {
        this.nameKey = nameKey;
        this.requiredLevel = requiredLevel;
        this.ironCost = ironCost;
        this.scrapCost = scrapCost;
        this.coalCost = coalCost;
        this.footprintRadius = footprintRadius;
        this.footprintHeight = footprintHeight;
        this.placer = placer;
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public int getRequiredLevel() {
        return this.requiredLevel;
    }

    public int getIronCost() {
        return this.ironCost;
    }

    public int getScrapCost() {
        return this.scrapCost;
    }

    public int getCoalCost() {
        return this.coalCost;
    }

    public int getFootprintRadius() {
        return this.footprintRadius;
    }

    public int getFootprintHeight() {
        return this.footprintHeight;
    }

    public void place(ServerLevel level, ImperialCommandCoreBlockEntity core, Player player, BlockPos pos) {
        this.placer.place(level, core, player, pos);
    }

    public CityStructureType next() {
        CityStructureType[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static CityStructureType byIndex(int index) {
        CityStructureType[] values = values();
        int safe = ((index % values.length) + values.length) % values.length;
        return values[safe];
    }
}
