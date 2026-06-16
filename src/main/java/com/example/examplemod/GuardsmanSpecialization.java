package com.example.examplemod;

public enum GuardsmanSpecialization {
    NONE("None", "", 0.0D, 0.0D, 0.0D, 0.0D, 0.0D),
    SNIPER("Sniper", "Snp", 0.0D, -1.0D, 0.0D, 6.0D, 0.0D),
    HEAVY_GUNNER("Heavy Gunner", "Hvy", 15.0D, 2.0D, 3.0D, 3.0D, -0.05D),
    MELEE_TROOPER("Melee Trooper", "Mle", 8.0D, 4.0D, 4.0D, -2.0D, 0.03D),
    MEDIC("Medic", "Med", 4.0D, -1.0D, 0.0D, 0.0D, 0.0D),
    OFFICER("Officer", "Off", 10.0D, 2.0D, 2.0D, 1.0D, 0.0D),
    ENGINEER("Engineer", "Eng", 6.0D, 0.0D, 1.0D, 0.0D, 0.0D);

    private final String displayName;
    private final String shortTag;
    private final double maxHealthBonus;
    private final double attackDamageBonus;
    private final double armorBonus;
    private final double lasgunDamageBonus;
    private final double movementSpeedBonus;

    GuardsmanSpecialization(
            String displayName,
            String shortTag,
            double maxHealthBonus,
            double attackDamageBonus,
            double armorBonus,
            double lasgunDamageBonus,
            double movementSpeedBonus
    ) {
        this.displayName = displayName;
        this.shortTag = shortTag;
        this.maxHealthBonus = maxHealthBonus;
        this.attackDamageBonus = attackDamageBonus;
        this.armorBonus = armorBonus;
        this.lasgunDamageBonus = lasgunDamageBonus;
        this.movementSpeedBonus = movementSpeedBonus;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortTag() {
        return shortTag;
    }

    public double getMaxHealthBonus() {
        return maxHealthBonus;
    }

    public double getAttackDamageBonus() {
        return attackDamageBonus;
    }

    public double getArmorBonus() {
        return armorBonus;
    }

    public double getLasgunDamageBonus() {
        return lasgunDamageBonus;
    }

    public double getMovementSpeedBonus() {
        return movementSpeedBonus;
    }

    public boolean isSpecialist() {
        return this != NONE;
    }

    // Returns the next selectable specialist type, cycling through all real specialists (skips NONE).
    public GuardsmanSpecialization nextSelectable() {
        GuardsmanSpecialization[] values = values();
        int next = this.ordinal() + 1;

        if (next >= values.length) {
            next = 1;
        }

        return values[next];
    }

    public static GuardsmanSpecialization fromName(String name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }

        try {
            return GuardsmanSpecialization.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }

    public static GuardsmanSpecialization fromOrdinal(int ordinal) {
        GuardsmanSpecialization[] values = values();

        if (ordinal < 0 || ordinal >= values.length) {
            return NONE;
        }

        return values[ordinal];
    }
}
