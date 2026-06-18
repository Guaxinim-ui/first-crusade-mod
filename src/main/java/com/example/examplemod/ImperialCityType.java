package com.example.examplemod;

import net.minecraft.util.RandomSource;

/**
 * Flavour of an Imperial settlement. Each Command Core is born as one of these types, which
 * gives the city a name, a production focus (one resource it produces 50% more of), and a
 * population factor. This is the data-driven seed for the full city-type roster in
 * {@code docs/DESIGN_WORLD_CITIES_FACTIONS.md}; more types and deeper effects come later.
 */
public enum ImperialCityType {
    //        name                    focus                       pop    rank  troopName         hp    armor  dmg   lasgun speed   iron
    CIVILISED("Civilised World City", null, 1.0D, 0, "Guardsman", 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 4),
    HIVE("Hive City", ImperialResourceType.SCRAP, 2.0D, -1, "Hive Levy", -2.0D, -1.0D, 0.0D, -0.5D, 0.0D, 2),
    FORGE("Forge City", ImperialResourceType.SCRAP, 1.2D, 1, "Forge Guard", 1.0D, 3.0D, 0.0D, 0.75D, -0.005D, 6),
    FORTRESS("Fortress City", ImperialResourceType.IRON, 0.8D, 2, "Shock Trooper", 4.0D, 3.0D, 1.0D, 0.0D, -0.01D, 8),
    AGRI("Agri City", ImperialResourceType.COAL, 1.4D, 0, "Agri Militia", 1.0D, 0.0D, 0.0D, 0.0D, 0.015D, 3),
    MINING("Mining City", ImperialResourceType.IRON, 1.1D, 0, "Mine Guard", 3.0D, 1.0D, 1.0D, 0.0D, 0.0D, 4),
    SHRINE("Shrine City", null, 1.3D, 1, "Battle Sister", 2.0D, 2.0D, 1.0D, 0.0D, 0.0D, 6),
    PENAL("Penal Colony", null, 1.5D, -1, "Penal Legionnaire", -2.0D, -2.0D, 1.0D, -0.5D, 0.01D, 2),
    DEATH_WORLD("Death World Settlement", null, 0.9D, 1, "Jungle Fighter", 2.0D, 1.0D, 2.0D, 0.5D, 0.02D, 5);

    private static final double FOCUS_BONUS = 1.5D;

    private final String displayName;
    private final ImperialResourceType productionFocus;
    private final double populationFactor;
    private final int recruitRankBonus;
    private final String troopName;

    // Combat identity of this city's regiment, layered on top of rank/chapter (see GuardsmanEntity).
    private final double maxHealthBonus;
    private final double armorBonus;
    private final double attackDamageBonus;
    private final double lasgunDamageBonus;
    private final double movementSpeedBonus;

    // Iron paid to train one soldier: cheap levies (Hive) vs expensive elites (Fortress).
    private final int recruitIronCost;

    ImperialCityType(String displayName, ImperialResourceType productionFocus, double populationFactor,
                     int recruitRankBonus, String troopName, double maxHealthBonus, double armorBonus,
                     double attackDamageBonus, double lasgunDamageBonus, double movementSpeedBonus,
                     int recruitIronCost) {
        this.displayName = displayName;
        this.productionFocus = productionFocus;
        this.populationFactor = populationFactor;
        this.recruitRankBonus = recruitRankBonus;
        this.troopName = troopName;
        this.maxHealthBonus = maxHealthBonus;
        this.armorBonus = armorBonus;
        this.attackDamageBonus = attackDamageBonus;
        this.lasgunDamageBonus = lasgunDamageBonus;
        this.movementSpeedBonus = movementSpeedBonus;
        this.recruitIronCost = recruitIronCost;
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

    public double getMaxHealthBonus() {
        return this.maxHealthBonus;
    }

    public double getArmorBonus() {
        return this.armorBonus;
    }

    public double getAttackDamageBonus() {
        return this.attackDamageBonus;
    }

    public double getLasgunDamageBonus() {
        return this.lasgunDamageBonus;
    }

    public double getMovementSpeedBonus() {
        return this.movementSpeedBonus;
    }

    // Iron cost to train one of this city's soldiers.
    public int getRecruitIronCost() {
        return this.recruitIronCost;
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
