package com.example.examplemod;

import net.minecraft.util.RandomSource;

/**
 * Flavour of an Imperial settlement. Each Command Core is born as one of these types, which
 * gives the city a name, a production focus (one resource it produces 50% more of), and a
 * population factor. This is the data-driven seed for the full city-type roster in
 * {@code docs/DESIGN_WORLD_CITIES_FACTIONS.md}; more types and deeper effects come later.
 */
public enum ImperialCityType {
    CIVILISED("Civilised World City", null, 1.0D),
    HIVE("Hive City", ImperialResourceType.SCRAP, 2.0D),
    FORGE("Forge City", ImperialResourceType.SCRAP, 1.2D),
    FORTRESS("Fortress City", ImperialResourceType.IRON, 0.8D),
    AGRI("Agri City", ImperialResourceType.COAL, 1.4D),
    MINING("Mining City", ImperialResourceType.IRON, 1.1D);

    private static final double FOCUS_BONUS = 1.5D;

    private final String displayName;
    private final ImperialResourceType productionFocus;
    private final double populationFactor;

    ImperialCityType(String displayName, ImperialResourceType productionFocus, double populationFactor) {
        this.displayName = displayName;
        this.productionFocus = productionFocus;
        this.populationFactor = populationFactor;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public double getPopulationFactor() {
        return this.populationFactor;
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
