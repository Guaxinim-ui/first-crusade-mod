package com.example.examplemod;

import net.minecraft.util.RandomSource;

/**
 * The <b>size</b> axis of a settlement, deliberately kept separate from {@link ImperialCityType}
 * (which is the settlement's <i>theme</i>: Forge, Fortress, Hive World, …). A city has both: e.g.
 * a FORGE-theme TOWN, or a CIVILISED-theme CITY.
 *
 * <p>The scale drives how large the settlement is founded (via its starting city level, which the
 * physical builder already maps to a wall radius). Only scales flagged {@link #isSeededNormally()}
 * are ever produced by {@link WorldSettlementSeeder}.
 *
 * <p><b>HIVE_CAPITAL</b> is the exception: a colossal 768-block capital that is <i>rare</i> and must
 * NOT be raised by the ordinary village builder or the ordinary world seeder. Its dedicated blocks
 * and builder are a separate feature (see the roadmap). The normal seeder can never pick it
 * ({@code weight 0}, {@code seededNormally=false}), so it will only ever appear through its own,
 * purpose-built founding path.
 */
public enum SettlementScale {
    //            display          diameter startLevel weight seededNormally
    OUTPOST     ("Outpost",           16,      1,        2,     true),
    VILLAGE     ("Village",           44,      3,        5,     true),
    TOWN        ("Town",              60,      4,        6,     true),
    CITY        ("City",              80,      5,        3,     true),
    HIVE_CAPITAL("Hive Capital",     768,      5,        0,     false);

    private final String displayName;
    private final int footprintDiameter;
    private final int startLevel;
    private final int seedWeight;
    private final boolean seededNormally;

    SettlementScale(String displayName, int footprintDiameter, int startLevel, int seedWeight, boolean seededNormally) {
        this.displayName = displayName;
        this.footprintDiameter = footprintDiameter;
        this.startLevel = startLevel;
        this.seedWeight = seedWeight;
        this.seededNormally = seededNormally;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    // Full width of the settlement footprint in blocks (768 for a Hive Capital).
    public int getFootprintDiameter() {
        return this.footprintDiameter;
    }

    public int getFootprintRadius() {
        return this.footprintDiameter / 2;
    }

    // City level a settlement of this scale is founded at; the physical builder maps level -> radius.
    public int getStartLevel() {
        return this.startLevel;
    }

    // Whether the ordinary world seeder may produce this scale. Hive Capitals are excluded.
    public boolean isSeededNormally() {
        return this.seededNormally;
    }

    // Weighted random pick among the normally-seedable scales only (never HIVE_CAPITAL).
    public static SettlementScale randomSeedable(RandomSource random) {
        int total = 0;
        for (SettlementScale scale : values()) {
            if (scale.seededNormally) {
                total += scale.seedWeight;
            }
        }

        int roll = random.nextInt(Math.max(1, total));
        for (SettlementScale scale : values()) {
            if (!scale.seededNormally) {
                continue;
            }
            roll -= scale.seedWeight;
            if (roll < 0) {
                return scale;
            }
        }

        return TOWN;
    }

    public static SettlementScale fromName(String name) {
        if (name == null || name.isEmpty()) {
            return TOWN;
        }

        for (SettlementScale scale : values()) {
            if (scale.name().equals(name)) {
                return scale;
            }
        }

        return TOWN;
    }
}
