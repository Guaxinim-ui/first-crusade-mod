package com.example.examplemod;

import net.minecraft.util.RandomSource;

/**
 * Flavour of an Imperial settlement. Each Command Core is born as one of these types, which
 * gives the city a name, a production focus (one resource it produces 50% more of), and a
 * population factor. This is the data-driven seed for the full city-type roster in
 * {@code docs/DESIGN_WORLD_CITIES_FACTIONS.md}; more types and deeper effects come later.
 */
public enum ImperialCityType {
    //        name                    focus                       pop    rank  troopName
    CIVILISED("Civilised World City", null, 1.0D, 0, "Guardsman"),
    HIVE("Hive City", ImperialResourceType.SCRAP, 2.0D, -1, "Hive Levy"),
    FORGE("Forge City", ImperialResourceType.SCRAP, 1.2D, 1, "Forge Guard"),
    FORTRESS("Fortress City", ImperialResourceType.IRON, 0.8D, 2, "Shock Trooper"),
    AGRI("Agri City", ImperialResourceType.COAL, 1.4D, 0, "Agri Militia"),
    MINING("Mining City", ImperialResourceType.IRON, 1.1D, 0, "Mine Guard");

    private static final double FOCUS_BONUS = 1.5D;

    private final String displayName;
    private final ImperialResourceType productionFocus;
    private final double populationFactor;
    private final int recruitRankBonus;
    private final String troopName;

    ImperialCityType(String displayName, ImperialResourceType productionFocus, double populationFactor,
                     int recruitRankBonus, String troopName) {
        this.displayName = displayName;
        this.productionFocus = productionFocus;
        this.populationFactor = populationFactor;
        this.recruitRankBonus = recruitRankBonus;
        this.troopName = troopName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public double getPopulationFactor() {
        return this.populationFactor;
    }

    // How many ranks this city's recruits start above/below the level baseline.
    public int getRecruitRankBonus() {
        return this.recruitRankBonus;
    }

    // Thematic name for this city's basic soldiers (Hive Levy, Shock Trooper, ...).
    public String getTroopName() {
        return this.troopName;
    }

    // Multiplies a resource's daily production if it is this city's focus.
    public int applyProductionFocus(ImperialResourceType resourceType, int amount) {
        if (this.productionFocus == resourceType) {
            return (int) Math.round(amount * FOCUS_BONUS);
        }

        return amount;
    }

    public static ImperialCityType random(RandomSource random) {
        ImperialCityType[] values = values();
        return values[random.nextInt(values.length)];
    }

    public static ImperialCityType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return CIVILISED;
        }

        for (ImperialCityType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }

        return CIVILISED;
    }
}
