package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;

import java.util.function.IntSupplier;

/**
 * Holds the city's six stockpiled resources and the capacity-aware logic for filling and
 * spending them. Extracted from {@link ImperialCommandCoreBlockEntity} to keep the Core focused
 * on orchestration.
 *
 * The shared storage capacity is supplied by the Core (it scales with city level), so this class
 * stays free of city-level knowledge. NBT keys are unchanged ("Iron", "Coal", "ScrapMetal",
 * "Gold", "Emerald", "Crusadium") so cities saved before this refactor load correctly.
 *
 * This class does NOT call {@code setChanged()} — callers in the Core do that after a mutation.
 */
public class ImperialResourceStorage {
    private final IntSupplier capacity;

    private int iron;
    private int coal;
    private int scrapMetal;
    private int gold;
    private int emerald;
    private int crusadium;

    public ImperialResourceStorage(IntSupplier capacity) {
        this.capacity = capacity;
    }

    public int getIron() {
        return this.iron;
    }

    public int getCoal() {
        return this.coal;
    }

    public int getScrapMetal() {
        return this.scrapMetal;
    }

    public int getGold() {
        return this.gold;
    }

    public int getEmerald() {
        return this.emerald;
    }

    public int getCrusadium() {
        return this.crusadium;
    }

    public int get(ImperialResourceType type) {
        return switch (type) {
            case IRON -> this.iron;
            case COAL -> this.coal;
            case SCRAP -> this.scrapMetal;
            case GOLD -> this.gold;
            case EMERALD -> this.emerald;
            case CRUSADIUM -> this.crusadium;
        };
    }

    // Adds up to the available free space; returns the amount actually accepted.
    public int add(ImperialResourceType type, int amount) {
        int accepted = acceptable(get(type), amount);

        if (accepted > 0) {
            setRaw(type, get(type) + accepted);
        }

        return accepted;
    }

    public int addIron(int amount) {
        return add(ImperialResourceType.IRON, amount);
    }

    public int addCoal(int amount) {
        return add(ImperialResourceType.COAL, amount);
    }

    public int addScrapMetal(int amount) {
        return add(ImperialResourceType.SCRAP, amount);
    }

    public int addGold(int amount) {
        return add(ImperialResourceType.GOLD, amount);
    }

    public int addEmerald(int amount) {
        return add(ImperialResourceType.EMERALD, amount);
    }

    public int addCrusadium(int amount) {
        return add(ImperialResourceType.CRUSADIUM, amount);
    }

    // True if the stockpile holds at least the given amounts of Iron, Scrap and Coal.
    public boolean has(int ironCost, int scrapCost, int coalCost) {
        return this.iron >= ironCost && this.scrapMetal >= scrapCost && this.coal >= coalCost;
    }

    // Spends exactly the given Iron/Scrap/Coal if all are available; returns false otherwise.
    public boolean spend(int ironCost, int scrapCost, int coalCost) {
        if (!has(ironCost, scrapCost, coalCost)) {
            return false;
        }

        this.iron -= ironCost;
        this.scrapMetal -= scrapCost;
        this.coal -= coalCost;
        return true;
    }

    // Removes up to `amount` of the resource (never below zero); returns the amount removed.
    public int remove(ImperialResourceType type, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int removed = Math.min(amount, get(type));
        setRaw(type, get(type) - removed);
        return removed;
    }

    public boolean consumeCrusadium(int amount) {
        if (amount <= 0 || this.crusadium < amount) {
            return false;
        }

        this.crusadium -= amount;
        return true;
    }

    // Trades stored Gold for Emerald at the given rate; returns the Emerald produced (0 if none).
    public int tradeGoldForEmerald(int emeraldYield, int goldPerEmerald) {
        int freeSpace = Math.max(0, this.capacity.getAsInt() - this.emerald);
        int produced = Math.min(emeraldYield, freeSpace);

        if (produced <= 0) {
            return 0;
        }

        int goldCost = produced * goldPerEmerald;

        if (this.gold < goldCost) {
            return 0;
        }

        this.gold -= goldCost;
        this.emerald += produced;
        return produced;
    }

    private int acceptable(int currentAmount, int requestedAmount) {
        if (requestedAmount <= 0) {
            return 0;
        }

        int freeSpace = this.capacity.getAsInt() - currentAmount;

        if (freeSpace <= 0) {
            return 0;
        }

        return Math.min(requestedAmount, freeSpace);
    }

    private void setRaw(ImperialResourceType type, int value) {
        switch (type) {
            case IRON -> this.iron = value;
            case COAL -> this.coal = value;
            case SCRAP -> this.scrapMetal = value;
            case GOLD -> this.gold = value;
            case EMERALD -> this.emerald = value;
            case CRUSADIUM -> this.crusadium = value;
        }
    }

    public void save(CompoundTag tag) {
        tag.putInt("Iron", this.iron);
        tag.putInt("Coal", this.coal);
        tag.putInt("ScrapMetal", this.scrapMetal);
        tag.putInt("Gold", this.gold);
        tag.putInt("Emerald", this.emerald);
        tag.putInt("Crusadium", this.crusadium);
    }

    // Loads the stockpile, clamping each value to the current storage capacity.
    public void load(CompoundTag tag) {
        int cap = this.capacity.getAsInt();

        this.iron = Math.min(tag.getInt("Iron"), cap);
        this.coal = Math.min(tag.getInt("Coal"), cap);
        this.scrapMetal = Math.min(tag.getInt("ScrapMetal"), cap);
        this.gold = Math.min(tag.getInt("Gold"), cap);
        this.emerald = Math.min(tag.getInt("Emerald"), cap);
        this.crusadium = Math.min(tag.getInt("Crusadium"), cap);
    }
}
