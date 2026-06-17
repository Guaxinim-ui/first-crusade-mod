package com.example.examplemod;

/**
 * Pure per-city-level lookup tables for the settlement's economy. These are functions of the
 * city level only (no state), so they live here instead of bloating ImperialCommandCoreBlockEntity.
 * The Core delegates to these; behaviour is unchanged. Add new level-scaled values here as the
 * economy grows, keeping the Core focused on state and orchestration.
 */
public final class ImperialCityLevelStats {
    private ImperialCityLevelStats() {
    }

    // Total resource storage capacity of the city.
    public static int storageCapacity(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 1500;
            case 3 -> 5000;
            case 4 -> 15000;
            case 5 -> 50000;
            default -> 500;
        };
    }

    // Maximum number of Guardsmen the city can field.
    public static int militaryCapacity(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 12;
            case 3 -> 25;
            case 4 -> 50;
            case 5 -> 100;
            default -> 5;
        };
    }

    // Passive daily production (phased out as staffed work sites take over).
    public static int dailyIronProduction(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 25;
            case 3 -> 100;
            case 4 -> 400;
            case 5 -> 1500;
            default -> 5;
        };
    }

    public static int dailyScrapProduction(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 15;
            case 3 -> 60;
            case 4 -> 240;
            case 5 -> 900;
            default -> 3;
        };
    }

    public static int dailyCoalProduction(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 10;
            case 3 -> 40;
            case 4 -> 160;
            case 5 -> 600;
            default -> 2;
        };
    }

    public static int dailyEmperorGeneProduction(int cityLevel) {
        return switch (cityLevel) {
            case 3 -> 1;
            case 4 -> 2;
            case 5 -> 4;
            default -> 0;
        };
    }

    public static int emperorGeneSeedCapacity(int cityLevel) {
        return switch (cityLevel) {
            case 3 -> 5;
            case 4 -> 12;
            case 5 -> 30;
            default -> 0;
        };
    }

    // Resource produced per work cycle by a staffed work site.
    public static int mineIronYield(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            case 5 -> 8;
            default -> 1;
        };
    }

    public static int scrapYardScrapYield(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 6;
            default -> 1;
        };
    }

    public static int refineryCoalYield(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            case 5 -> 4;
            default -> 1;
        };
    }

    public static int goldMineYield(int cityLevel) {
        return switch (cityLevel) {
            case 4 -> 2;
            case 5 -> 3;
            default -> 1;
        };
    }

    public static int tradeDepotEmeraldYield(int cityLevel) {
        return switch (cityLevel) {
            case 4 -> 2;
            case 5 -> 3;
            default -> 1;
        };
    }

    // War Support cost to call reinforcements during a raid (also shown in the Core screen).
    public static int reinforcementWarSupportCost(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 10;
            case 3 -> 18;
            case 4 -> 30;
            case 5 -> 50;
            default -> 5;
        };
    }

    // War Support cost to fortify defenders during a raid (also shown in the Core screen).
    public static int fortifyWarSupportCost(int cityLevel) {
        return switch (cityLevel) {
            case 2 -> 10;
            case 3 -> 18;
            case 4 -> 30;
            case 5 -> 45;
            default -> 5;
        };
    }
}
